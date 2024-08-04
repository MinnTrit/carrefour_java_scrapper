package playwright;
import org.apache..Conf;
import org.apache..sql.*;
import static org.apache..sql.functions.*;
import org.apache..sql.catalyst.encoders.RowEncoder;
import org.apache..sql.types.*;
import org.apache..api.java.*;
import java.util.List;
import java.util.Map;
import java.io.Serializable;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.apache..api.java.function.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache..sql.expressions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.stream.Collectors;
import static org.apache..sql.functions.lit;
import java.sql.PreparedStatement;
import scala.Function1;
import scala.runtime.BoxedUnit;
import scala.collection.Iterator;


public class DataFrameProcessor implements Serializable {
	private transient String searchKeyword;
	private transient String jdbcConnection;
	private transient Properties connectionProperties;
	private transient Session Session;
	private transient JavaContext Context;
	private transient Connection connection;
	private transient PreparedStatement preparedStatement;
	private Dataset<Row> finalDf;
	public DataFrameProcessor(String searchKeyword) {
		this.searchKeyword = searchKeyword;
		System.out.println("Start initializing Java ");
		initialize();
	};
	
	private void initialize() {
		String jdbcConnection = "jdbc:mysql://localhost:3306/minhtriet";
		Properties connectionProperties = new Properties();
		connectionProperties.put("user", "");
		connectionProperties.put("password", "");
		connectionProperties.put("driver", "com.mysql.cj.jdbc.Driver");
		this.jdbcConnection = jdbcConnection;
		this.connectionProperties = connectionProperties;
		Conf conf = new Conf().setAppName("Processor").setMaster("local[*]")
				.set(".driver.memory", "4g")
				.set(".executor.cores", "3")
				.set(".executor.instances", "9")
				.set(".executor.memory", "16g");
		Session Session = Session.builder().config(conf).getOrCreate();
		JavaContext Context = new JavaContext(Session.Context());
		this.Context = Context;
		this.Session = Session;
	}
	
	public Dataset<Row> toDataframe(List<Map<String, String>> inputList) {
		System.out.println("Start processing the data");
		JavaRDD<Map<String, String>> stringRDD = Context.parallelize(inputList);
		
		Function<Map<String, String>, Row> toRow = new Function<>() {
			@Override
			public Row call(Map<String, String> inputMap) {
				//Getting the keyword
				String keyWord = inputMap.get("KeyWord");
				
				//Get the SKU ID
				String skuId = inputMap.get("SKU_ID");
				
				//Getting the ratings
				String ratingString = inputMap.get("Ratings");
				Integer ratings;
				if (ratingString != null) {
					ratings = Integer.valueOf(ratingString);
				}
				else {
					ratings = null;
				}
				String stars = inputMap.get("Stars");
				
				//Getting the price
				String price = inputMap.get("Price").replace(",", "");
				Pattern pattern = Pattern.compile("\\d");
				Matcher matcher = pattern.matcher(price);
				StringBuilder digitsOnly = new StringBuilder();
				while (matcher.find()) {
					digitsOnly.append(matcher.group());
				}
				String digitString = digitsOnly.toString();
				int priceInt = Integer.valueOf(digitString);
				
				//Getting the product
				String productName = inputMap.get("Product");
				
				//Getting the brand
				String brand = inputMap.get("Brand");
				
				//Getting the create time
				String create = inputMap.get("Create");
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
				LocalDateTime createLocal = LocalDateTime.parse(create, formatter);
				Timestamp sqlDate = Timestamp.valueOf(createLocal);
				
				//Getting the year_month
				int createMonth = createLocal.getMonth().getValue();
				int createYear = createLocal.getYear();
				String monthString = String.valueOf(createMonth);
				String yearString = String.valueOf(createYear);
				if (monthString.length() == 1) {
					monthString = "0" + monthString;
				}
				String yearMonth = yearString + "_" + monthString;
				
				//Getting the url and the img_url
				String url = inputMap.get("url");
				String imgUrl = inputMap.get("img_url");
				
				//Getting the base size
				String baseSize = inputMap.get("BaseSize");
				
				//Getting the source
				String source = inputMap.get("source");
				
				Row returnedRow = RowFactory.create(keyWord, skuId, brand,
						productName, priceInt, ratings, stars, yearMonth, sqlDate,
						url, imgUrl, baseSize, source);
				return returnedRow;
			}
			
		};
		
		JavaRDD<Row> rdd = stringRDD.map(toRow);
		StructField[] fields = new StructField[] {
			DataTypes.createStructField("KeyWord", DataTypes.StringType, false),
			DataTypes.createStructField("SKU_ID", DataTypes.StringType, false),
			DataTypes.createStructField("Brand", DataTypes.StringType, false),
			DataTypes.createStructField("Product", DataTypes.StringType, false),	
			DataTypes.createStructField("Price", DataTypes.IntegerType, false),
			DataTypes.createStructField("Ratings", DataTypes.IntegerType, true),
			DataTypes.createStructField("Stars", DataTypes.StringType, true),
			DataTypes.createStructField("YearMonth", DataTypes.StringType, false),
			DataTypes.createStructField("Created", DataTypes.TimestampType, false),
			DataTypes.createStructField("url", DataTypes.StringType, false),
			DataTypes.createStructField("img_url", DataTypes.StringType, false),
			DataTypes.createStructField("BaseSize", DataTypes.StringType, true),
			DataTypes.createStructField("source", DataTypes.StringType, false)
		};
		StructType schema = new StructType(fields);
		Dataset<Row> returnedDf = Session.createDataFrame(rdd, schema);
		this.finalDf = returnedDf;
		return returnedDf;
	}
	
	public Dataset<Row> toTransform() {
		WindowSpec firstRank = Window.partitionBy("Product", "Create")
									   .orderBy(finalDf.col("Price").asc());
		finalDf = finalDf.withColumn("Partition", functions.ntile(4).over(firstRank));
			
		//Getting the price range
		WindowSpec windowRange = Window.partitionBy("Partition");		
		finalDf = finalDf.withColumn("MinPrice", functions.min(finalDf.col("Price")).over(windowRange));
		finalDf = finalDf.withColumn("MaxPrice", functions.max(finalDf.col("Price")).over(windowRange));
		finalDf = finalDf.withColumn("PriceRange", functions.concat_ws(" - ", 
															finalDf.col("MinPrice"),
															finalDf.col("MaxPrice")));
		finalDf = finalDf.drop("MinPrice", "MaxPrice");
		
		//Getting the total products by range
		WindowSpec countWindow = Window.partitionBy("Partition");
		finalDf = finalDf.withColumn("TotalProductsInRange", functions.count(finalDf.col("Product"))
																	  .over(countWindow));
		//Getting the average price by range
		WindowSpec averageWindow = Window.partitionBy("PriceRange");
		finalDf = finalDf.withColumn("AveragePriceInRange", functions.avg(finalDf.col("Price")).over(averageWindow));
		return finalDf;
	};
	
	private Dataset<Row> readSkus() {
		Dataset<Row> skuDf = Session.read()
								.jdbc(jdbcConnection, "ecommerce_sku", connectionProperties);
		return skuDf;
	}
	
	private Dataset<Row> readRatings() {
		Dataset<Row> ratingDf = Session.read()
							.jdbc(jdbcConnection, "ecommerce_sku_ratings", connectionProperties);
		return ratingDf;
	};
	
	private Dataset<Row> readPrices() {
		Dataset<Row> priceDf = Session.read()
								.jdbc(jdbcConnection, "ecommerce_sku_prices", connectionProperties);
		return priceDf;
	};
	
	private Dataset<Row> readBrands() {
		Dataset<Row> brandsDf = Session.read()
				.jdbc(jdbcConnection, "ecommerce_brand", connectionProperties);
		return brandsDf;
	}
	
	private Map<String, Integer> getSkuMap() {
		Dataset<Row> skuDf = readSkus();
		List<Row> rowList = skuDf.select("SKU_ID", "id").collectAsList();
		Map<String, Integer> skuMap = rowList.stream().collect(
				Collectors.toMap(
				(Row row) -> row.getString(0),
				(Row row) -> row.getInt(1)
				));
		return skuMap;
	}
	
	private Map<String, Integer> getBrandMap() {
		Dataset<Row> brandsDf = readBrands();
		List<Row> rowList = brandsDf.select("id", "Brand").collectAsList();
		Map<String, Integer> brandMap = rowList.stream().collect(
				Collectors.toMap(
					(Row row) -> row.getString(1),
					(Row row) -> row.getInt(0)	
					));
		return brandMap;
	}
	
	private void saveBrands() {
		System.out.println("Start saving the brands to the database");
		Dataset<Row> brandDf = readBrands();
		List<Row> rowList = brandDf.select("Brand").collectAsList();
		List<String> brandList = rowList.stream()
				.map((Row row) -> row.getAs("Brand").toString())
				.collect(Collectors.toList());
		Dataset<Row> brandsDf;
		if (!brandList.isEmpty()) {
			System.out.println("The dataframe is not empty");
			brandsDf = finalDf.filter((Row row) -> !brandList.contains(row.getAs("Brand")));
			brandsDf = brandsDf.select("Brand").distinct();
		}
		else {
			brandsDf = finalDf.select("Brand");
			System.out.println("The dataframe is empty");
			WindowSpec numWindow = Window.partitionBy("Brand").orderBy(col("Brand"));
			brandsDf = brandsDf.withColumn("RowNum", functions.row_number().over(numWindow));
			brandsDf = brandsDf.filter(col("RowNum").equalTo(1)).drop("RowNum");
		}
		brandsDf.write().mode("append").jdbc(jdbcConnection,"ecommerce_brand", connectionProperties);
	}
	
	private void saveSkus() {
		System.out.println("Start saving the SKUs to the database");
		//Read the SKU table from the database
		Dataset<Row> skuDf = readSkus();
		List<Row> rowList = skuDf.select("SKU_ID").collectAsList();
		List<String> skuList = rowList.stream()
				.map(row -> row.getAs("SKU_ID").toString()).collect(Collectors.toList());
		//Getting the brand map
		Map<String, Integer> brandMap = getBrandMap();
		//Read the rating tables from the database -> Save only the SKUs that are not existed in the database
		Dataset<Row> skusDf;
		if (!skuList.isEmpty()) {
			 skusDf = finalDf.filter((Row row) -> !skuList.contains(row.getAs("SKU_ID")));
			 skusDf = skusDf.select("Brand", "SKU_ID", "KeyWord", "Product", "url", "img_url", "BaseSize", "source", "Created");
		}
		else {
			skusDf = finalDf.select("Brand", "SKU_ID", "KeyWord", "Product", "url", "img_url", "BaseSize", "source", "Created");
		}
		//Getting the brand id with the brand name
		MapFunction<Row, Row> toExtract = inputRow -> {
			String brandName = inputRow.getString(0); //Brand
			int brandId = brandMap.get(brandName);
			String skuId = inputRow.getString(1); //SKU ID
			String keyWord = inputRow.getString(2); // Keyword
			String product= inputRow.getString(3); // Product
			String url = inputRow.getString(4); // URL
			String img_url = inputRow.getString(5); // imgURL
			String baseSize = inputRow.getString(6); // BaseSize
			String source = inputRow.getString(7); // source
			Timestamp created = inputRow.getTimestamp(8); // create
			return RowFactory.create(
					brandId, skuId, keyWord, product, url, img_url, 
					baseSize, source, created 
					);
		};
		
		StructField[] skuFields = new StructField[] {
			DataTypes.createStructField("fk_brand_id", DataTypes.IntegerType, false),
			DataTypes.createStructField("SKU_ID", DataTypes.StringType, false),
			DataTypes.createStructField("KeyWord", DataTypes.StringType, false),
			DataTypes.createStructField("Product", DataTypes.StringType, false),
			DataTypes.createStructField("url", DataTypes.StringType, false),
			DataTypes.createStructField("img_url", DataTypes.StringType, false),
			DataTypes.createStructField("BaseSize", DataTypes.StringType, true),
			DataTypes.createStructField("source", DataTypes.StringType, false),
			DataTypes.createStructField("Created", DataTypes.TimestampType, false)
		};
		
		StructType skusTypes = new StructType(skuFields);
		
		skusDf = skusDf.map(toExtract, RowEncoder.apply(skusTypes));
		skusDf = skusDf.distinct();
		System.out.println(jdbcConnection);
		skusDf.write().mode("append").jdbc(jdbcConnection,"ecommerce_sku", connectionProperties);
	};
	
	private void saveRatings() {
		System.out.println("Start saving the SKUs' ratings to the database");
		Map<String, Integer> skuMap = getSkuMap();
		Dataset<Row> sampleRatingDf = readRatings();
		List<Row> rowList = sampleRatingDf.select("SKU_ID").collectAsList();
		List<String> sampleSkuList = rowList.stream().map(
				(Row row) -> row.getAs("SKU_ID").toString()
				).collect(Collectors.toList());
		Dataset<Row> ratingDf = finalDf.select("SKU_ID", "Ratings", "Stars", "Created");
		
		//Get the existed and non-existed Data
		Dataset<Row> nonExistedRatingDf = ratingDf.filter(
				(Row row) -> !sampleSkuList.contains(row.getAs("SKU_ID").toString())
				);
		Dataset<Row> ExistedRatingDf = ratingDf.filter(
				(Row row) -> sampleSkuList.contains(row.getAs("SKU_ID").toString())
				);
		nonExistedRatingDf = nonExistedRatingDf.distinct();
		//Create the function to update
		ForeachPartitionFunction<Row> toUpdate = iterator -> {
			String connectionString = "jdbc:mysql://localhost:3306/minhtriet";
			Properties connectionProperties = new Properties();
			connectionProperties.setProperty("user", "");
			connectionProperties.setProperty("password", "");
			connectionProperties.setProperty("driver", "com.mysql.cj.jdbc.Driver");
			try (Connection connection = DriverManager.getConnection(connectionString, connectionProperties);
				PreparedStatement preparedStatement = connection.prepareStatement(
					"UPDATE ecommerce_sku_ratings set Ratings = ?, Stars = ?, updated = ? where SKU_ID = ?"
					);) {
				while (iterator.hasNext()) {
					Row row = iterator.next();
					String skuID = row.getString(0);
					Integer ratings = row.isNullAt(1) ? null : row.getInt(1);
					String stars = row.isNullAt(2) ? null : row.getString(2);
					if (ratings == null) {
						preparedStatement.setNull(1, java.sql.Types.INTEGER);
						preparedStatement.setNull(2, java.sql.Types.VARCHAR);
					}
					else {
						preparedStatement.setInt(1, ratings);
						preparedStatement.setString(2, stars);
					}
					LocalDateTime currentTimestamp = LocalDateTime.now();
					Timestamp sqlTimestamp = Timestamp.valueOf(currentTimestamp);
					preparedStatement.setTimestamp(3, sqlTimestamp);
					preparedStatement.setString(4, skuID);
					preparedStatement.executeUpdate();
				}
			}
		catch (Exception e) {
			System.out.println("Error occured while connecting to the datbase as "+e);
		}
		};
		//Create the function to insert
		ForeachPartitionFunction<Row> toInsert = iterator -> {
			String connectionString = "jdbc:mysql://localhost:3306/minhtriet";
			Properties connectionProperties = new Properties();
			connectionProperties.put("user", "");
			connectionProperties.put("password", "");
			connectionProperties.put("driver", "com.mysql.cj.jdbc.Driver");
			try (Connection connection = DriverManager.getConnection(connectionString, connectionProperties);
				PreparedStatement preparedStatement = connection.prepareStatement(
					"INSERT INTO ecommerce_sku_ratings (SKU_ID, Ratings, Stars, created, updated, fk_sku_id)"
					+ "values (?, ?, ?, ?, ?, ?)"
						);
					) {
				while (iterator.hasNext()) {
					Row row = iterator.next();
					String skuId = row.getString(0);
					Integer ratings = row.isNullAt(1) ? null : row.getInt(1);
					String stars = row.isNullAt(2) ? null : row.getString(2);
					if (ratings == null) {
						preparedStatement.setNull(2, java.sql.Types.INTEGER);
						preparedStatement.setNull(3, java.sql.Types.VARCHAR);
					}
					else {
						preparedStatement.setInt(2, ratings);
						preparedStatement.setString(3, stars);
					}
					Timestamp created = row.getTimestamp(3);
					Timestamp updated = row.getTimestamp(3);
					int fkSkuId = skuMap.get(skuId);
					preparedStatement.setString(1, skuId);
					preparedStatement.setTimestamp(4, created);
					preparedStatement.setTimestamp(5, updated);
					preparedStatement.setInt(6, fkSkuId);
					preparedStatement.executeUpdate();
				}
			}
		catch (Exception e) {
			System.out.println("Error occured while connecting to the database as "+e);	
		}
		};
		nonExistedRatingDf.foreachPartition(toInsert);
		ExistedRatingDf.foreachPartition(toUpdate);
	}
	
	private void savePrices () {
		Dataset<Row> priceSampleDf = readPrices();
		Dataset<Row> priceDf = finalDf.select("SKU_ID", "Price", "Created");
		List<Row> rowList = priceSampleDf.select("SKU_ID").collectAsList();
		List<String> skuIdList = rowList.stream()
				.map((Row row) -> row.getAs("SKU_ID").toString())
				.collect(Collectors.toList());
		System.out.println("Start saving the prices for SKUs");
		Map<String, Integer> skuMap = getSkuMap();
		Dataset<Row> rowsToUpdate = priceDf.filter(
				(Row row) -> skuIdList.contains(row.getAs("SKU_ID").toString())
				); 
		Dataset<Row> rowsToInsert = priceDf.filter(
				(Row row) -> !skuIdList.contains(row.getAs("SKU_ID").toString())
				);
		rowsToInsert = rowsToInsert.distinct();
		//Create the functions to update the data by partitions
		ForeachPartitionFunction<Row> toUpdate = iterator -> {
			String connectionString = "jdbc:mysql://localhost:3306/minhtriet";
			Properties connectionProperties = new Properties();
			connectionProperties.put("user", "");
			connectionProperties.put("password", "");
			connectionProperties.put("driver", "com.mysql.cj.jdbc.Driver");
			try (Connection connection = DriverManager.getConnection(connectionString, connectionProperties);
				PreparedStatement preparedStatement = connection.prepareStatement("UPDATE ecommerce_sku_prices set price = ?, Updated = ? where SKU_ID = ?")){				
				while (iterator.hasNext()) {
					Row row = iterator.next();
					int skuPrice = row.getInt(1);
					String skuId = row.getString(0);
					LocalDateTime localDatetime = LocalDateTime.now();
					Timestamp sqlTime = Timestamp.valueOf(localDatetime);
					preparedStatement.setInt(1, skuPrice);
					preparedStatement.setTimestamp(2, sqlTime);
					preparedStatement.setString(3, skuId);
					preparedStatement.executeUpdate();
				};
			}
			catch (Exception e) {
				System.out.println("Error occured while making the connection to the database as "+e);
			}
	};
	
	//Insert the data that's not already existed in the database
	ForeachPartitionFunction<Row> toInsert = iterator -> {
		String connectionString = "jdbc:mysql://localhost:3306/minhtriet";
		Properties connectionProperties = new Properties();
		connectionProperties.put("user", "");
		connectionProperties.put("password", "");
		connectionProperties.put("driver", "com.mysql.cj.jdbc.Driver");
		try (Connection connection = DriverManager.getConnection(connectionString, connectionProperties);
			PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO ecommerce_sku_prices (SKU_ID, price, created, updated, fk_sku_id)"
					+ "values (?, ?, ?, ?, ?)")) {
			while (iterator.hasNext()) {
				Row row = iterator.next();
				String skuID = row.getAs("SKU_ID");
				int price = row.getAs("Price");
				Timestamp created = row.getAs("Created");
				Timestamp updated = row.getAs("Created");
				int fkSkuId = skuMap.get(skuID);
				preparedStatement.setString(1, skuID);
				preparedStatement.setInt(2, price);
				preparedStatement.setTimestamp(3, created);
				preparedStatement.setTimestamp(4, updated);
				preparedStatement.setInt(5, fkSkuId);
				preparedStatement.executeUpdate();
			};
		}
		catch (Exception e) {
			System.out.println("Error occured while making the connection to the database as "+e);
		};		
	};
		rowsToUpdate.foreachPartition(toUpdate);
		rowsToInsert.foreachPartition(toInsert);
	}
	
	private void saveStamp() {
		System.out.println("Start saving the SKU' stamps to the database");
		Map<String, Integer> skuMap = getSkuMap();
		Dataset<Row> stampDf = finalDf.select(
			"SKU_ID", "Price", "Ratings", "Stars", "Created"
				);
		MapFunction<Row, Row> toMapId = inputRow -> {
			String skuId = inputRow.getString(0);
			int price = inputRow.getInt(1);
			Integer ratings = inputRow.isNullAt(2) ? null : inputRow.getInt(2);
			String stars = inputRow.isNullAt(3) ? null : inputRow.getString(3);
			Timestamp created = inputRow.getTimestamp(4);
			int fkSkuId = skuMap.get(skuId);
			Row returnedRow = RowFactory.create(
				skuId, fkSkuId, price, ratings, stars, created
					);
			return returnedRow;
		};
		StructField[] stampFields = new StructField[] {
			DataTypes.createStructField("SKU_ID", DataTypes.StringType, false),
			DataTypes.createStructField("fk_sku_id", DataTypes.IntegerType, false),
			DataTypes.createStructField("historical_price", DataTypes.IntegerType, false),
			DataTypes.createStructField("historical_ratings", DataTypes.IntegerType, true),
			DataTypes.createStructField("historical_stars", DataTypes.StringType, true),
			DataTypes.createStructField("created", DataTypes.TimestampType, false)
		};
		StructType stampType = new StructType(stampFields);
		stampDf = stampDf.map(toMapId, RowEncoder.apply(stampType));
		stampDf.write().mode("append").jdbc(jdbcConnection, "ecommerce_sku_stamp", connectionProperties);
	};
	
	public void toDatabase() {
		System.out.println("Start saving the data to the database");
		//Start saving the data to the database
		saveBrands();
		saveSkus();
		savePrices();
		saveRatings();
		saveStamp();
		System.out.println("Done saving the data to the database");
	}
	
	public void toAggregate() {
		System.out.println("Start aggregating the market share report");
		Dataset<Row> skuDf = readSkus().withColumnRenamed("SKU_ID", "main_SKU_ID");
		Dataset<Row> ratingDf = readRatings();
		Dataset<Row> priceDf = readPrices();
		
		//Joining the first 2 data frames
		Column fromSkutoRating = skuDf.col("id").equalTo(ratingDf.col("fk_sku_id"));
		Dataset<Row> joinedDf = skuDf.join(ratingDf, fromSkutoRating);
		
		//Joining the last data frame
		Column fromSkutoPrice = skuDf.col("id").equalTo(priceDf.col("fk_sku_id"));
		joinedDf = joinedDf.join(priceDf, fromSkutoPrice);
		
		//Take out the aggregated data frame
		Dataset<Row> aggDf = joinedDf.select(
				joinedDf.col("KeyWord"),
				joinedDf.col("main_SKU_ID").alias("SKU_ID"),
				joinedDf.col("Product"),
				joinedDf.col("Ratings"),
				joinedDf.col("Price")
				);
		aggDf = aggDf.na().fill(0, new String[]{"Ratings"});
		
		WindowSpec rankSpec = Window.partitionBy("SKU_ID", "Price").orderBy(col("Price").desc());
		aggDf = aggDf.withColumn("rowNum", functions.row_number().over(rankSpec));
		aggDf = aggDf.filter(col("rowNum").equalTo(lit(1))).drop("rowNum");

		//Start doing the aggregations
		aggDf = aggDf.withColumn("RevenueIndex", aggDf.col("Ratings").multiply(aggDf.col("Price")));
		WindowSpec revenueSpec = Window.partitionBy("KeyWord");
		aggDf = aggDf.withColumn("TotalRevenue", 
				functions.sum(aggDf.col("RevenueIndex")).over(revenueSpec));
		aggDf = aggDf.withColumn("Proportion", 
				(aggDf.col("RevenueIndex").divide(aggDf.col("TotalRevenue")).multiply(100)));
		
		LocalDateTime currentTime = LocalDateTime.now();
		Timestamp sqlTime = Timestamp.valueOf(currentTime);
		aggDf = aggDf.withColumn("Created", lit(sqlTime));
		
		aggDf.write().mode("append")
					.jdbc(jdbcConnection, "ecommerce_marketshare", connectionProperties);
		
		System.out.println("Done aggregating the market share report");
		
	};
	
	public void stop() {
		Session.stop();
		System.out.println(" session has been closed");
	}
}
