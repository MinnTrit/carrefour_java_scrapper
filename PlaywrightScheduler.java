package playwright;
import java.util.List;
import java.util.Map;
import java.io.Serializable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.util.ArrayList;

public class PlaywrightScheduler implements Job, Serializable {
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		System.out.println("Executing job at "+ new java.util.Date());
		//Initialize playwright to scrape the data
		String urlPath = "https://www.carrefour.fr";
		boolean headlessOption = false;
		int totalPage = 1;
		Integer totalBrands = 1;
		
		ArrayList<String> productList = new ArrayList<>();
		productList.add("Chocolate");
		productList.add("Wine");
		productList.add("Juices");
		productList.add("Coffee");		
		
		CarreFourPlaywright playwright = new CarreFourPlaywright(urlPath, headlessOption, totalPage, totalBrands);
		String searchingKeyword = playwright.getSearchingWord(productList);
		
		List<Map<String, String>> nameList = playwright.toScrape(searchingKeyword);
		playwright.closeBrowser();
		DataFrameProcessor processor = new DataFrameProcessor(searchingKeyword);
		Dataset<Row> testDf = processor.toDataframe(nameList);
		
		//Start saving the data to the database
		processor.toDatabase();
		processor.stopSpark();
		System.out.println("Job ended at "+ new java.util.Date());
	}
}
