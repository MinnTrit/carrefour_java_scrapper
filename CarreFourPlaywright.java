package playwright;

import com.microsoft.playwright.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.options.*;
import java.lang.StringBuilder;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collector;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import com.microsoft.playwright.Page.WaitForSelectorOptions;
import java.util.Random;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;
import java.util.stream.IntStream;


public class CarreFourPlaywright {
	String urlPath;
	boolean headlessOption;
	Page page;
	int totalPage;
	int countBrands;
	Browser browser;
	BrowserContext context;
	public CarreFourPlaywright(String urlPath, boolean headlessOption, int totalPage, Integer totalBrands) {
		this.urlPath = urlPath;
		this.headlessOption = headlessOption;
		this.totalPage = totalPage;
		this.countBrands = countBrands(totalBrands);
		initialize();
	};
	
	private void initialize() {
		System.out.println("Start connecting to the browser");
		String cookiesPath = "path/to/carrefour_cookies.txt";
		Playwright playwright = Playwright.create();
		Browser browser = playwright.chromium()
				.launch(new BrowserType.LaunchOptions().setHeadless(headlessOption));
		Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
		contextOptions.setViewportSize(1368, 1158);
		BrowserContext context = browser.newContext(contextOptions);
		this.context = context;
		List<Cookie> cookies = new ArrayList<>();
		
		//Initialize the file reader
		try {
			StringBuilder stringBuilder = new StringBuilder();
			FileReader reader = new FileReader(cookiesPath);
			BufferedReader br = new BufferedReader(reader);
			String line;
			while ((line = br.readLine()) != null) {
				stringBuilder.append(line);
			}
			br.close();
			String cookiesString = stringBuilder.toString();
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(cookiesString);
			for (JsonNode jsonObj : rootNode) {
				((ObjectNode) jsonObj).put("sameSite", "NONE");
				if (jsonObj.has("expirationDate")) {
					String name = jsonObj.get("name").asText();
					String value = jsonObj.get("value").asText();
					String path = jsonObj.get("path").asText();
					double expires = jsonObj.get("expirationDate").asDouble();
					boolean httpOnly = jsonObj.get("httpOnly").asBoolean();
					boolean secure = jsonObj.get("secure").asBoolean();
					String siteString = jsonObj.get("sameSite").asText();
					SameSiteAttribute sameSite = SameSiteAttribute.valueOf(siteString);
					String domain = jsonObj.get("domain").asText();
					Cookie cookie = new Cookie(name, value)
										.setDomain(domain)
										.setExpires(expires)
										.setHttpOnly(httpOnly)
										.setPath(path)
										.setSecure(secure)
										.setSameSite(sameSite);
					cookies.add(cookie);
				}
				else {
					String name = jsonObj.get("name").asText();
					String value = jsonObj.get("value").asText();
					String path = jsonObj.get("path").asText();
					boolean httpOnly = jsonObj.get("httpOnly").asBoolean();
					boolean secure = jsonObj.get("secure").asBoolean();
					String siteString = jsonObj.get("sameSite").asText();
					SameSiteAttribute sameSite = SameSiteAttribute.valueOf(siteString);
					String domain = jsonObj.get("domain").asText();
					Cookie cookie = new Cookie(name, value)
										.setDomain(domain)
										.setHttpOnly(httpOnly)
										.setPath(path)
										.setSecure(secure)
										.setSameSite(sameSite);
					cookies.add(cookie);
				}
			}
			context.addCookies(cookies);
			Page page = context.newPage();
			page.navigate(urlPath);
			this.page = page;
			this.browser = browser;
			toIgnore(page);
		}
		catch (IOException e) {
			System.out.println("Error occured as "+e.getMessage());
		}
		
	}
	
	public String getSearchingWord(ArrayList<String> inputList) {
		Random rand = new Random();
		int randomIndex = rand.nextInt(inputList.size());
		String searchingWord = inputList.get(randomIndex);
		System.out.println("The chosen searching word is "+searchingWord);
		return searchingWord;
	};
	
	public List<Map<String, String>> toScrape(String inputKeyword) {
		System.out.println("Start scrapping the page");
		searchKeyword(inputKeyword);
		LocalDateTime currentTime = LocalDateTime.now();
		String timeFormat = "yyyy-MM-dd HH:mm:ss";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timeFormat);
		String currentTimeString = currentTime.format(formatter);
		ArrayList<Map<String, String>> finalList = new ArrayList<>();
		//Start scrolling down the page
		for (int currentBox = 0; currentBox < countBrands; currentBox++) {
			String currentBrand = getBrand(currentBox);
			clickAndEraseBox(currentBox);
			page.waitForLoadState(LoadState.LOAD);
			toScroll(totalPage);
			List<String> nameList = getProductNames();
			List<String> priceList = getPrices();
			List<String> idList = getProductID();
			List<String> ratingList = getRatings();
			List<String> starsList = getStars();
			List<String> urlsList = getUrls();
			List<String> imgUrlList = getImgUrls();
			List<String> baseSizeList = getBasesize();
			for (int currentIndex = 0; currentIndex < nameList.size(); currentIndex++) {
				Map<String, String> productMap = new HashMap<>();
				String productName = nameList.get(currentIndex);
				String productPrice = priceList.get(currentIndex);
				String skuId = idList.get(currentIndex);
				String rating = ratingList.get(currentIndex);
				String stars = starsList.get(currentIndex);
				String url = urlsList.get(currentIndex);
				String imgUrl = imgUrlList.get(currentIndex);
				String baseSize = baseSizeList.get(currentIndex);
				productMap.put("KeyWord", inputKeyword);
				productMap.put("Brand", currentBrand);
				productMap.put("SKU_ID", skuId);
				productMap.put("Product", productName);
				productMap.put("Price", productPrice);
				productMap.put("Ratings", rating);
				productMap.put("Stars", stars);
				productMap.put("Create", currentTimeString);
				productMap.put("url", url);
				productMap.put("img_url", imgUrl);
				productMap.put("BaseSize", baseSize);
				productMap.put("source", "scrapping");
				finalList.add(productMap);
			}
			clickAndEraseBox(currentBox);
		}
		int totalSkus = finalList.size();
		System.out.println("Scrapped the total of "+totalSkus+" SKUs");
		return finalList;
	}
	
	private int countBrands(Integer totalBrands) {
		if (totalBrands == null) {
			ElementHandle brandElements = page.querySelector("div[id='filters-checkbox-options-525']");
			int numBox = brandElements.querySelectorAll("div.filters-checkbox__option").size();
			List<Integer> indexList = IntStream.range(0, numBox)
					.boxed().collect(Collectors.toList());
			int countBrands = indexList.size();
			return countBrands;
		}
		else {
			int countBrands = totalBrands;
			return countBrands;
		}
	};
	 
	private void toScroll(int totalPage) {
		page.waitForLoadState(LoadState.LOAD);
		for (int numPage = 0; numPage < totalPage; numPage++) {
			String barStatus = checkEnd();
			if (barStatus.equals("End")) {
				break;
			}
			scrollAndClick();
			}
	};
	
	private void toIgnore(Page page) {
		while (true) {
			try {
				ElementHandle ignoreWait = page.waitForSelector("button[id=\"onetrust-reject-all-handler\"]");
				if (ignoreWait != null) {
					ElementHandle ignoreButton = page.querySelector("button[id=\"onetrust-reject-all-handler\"]");
					if (ignoreButton != null) {
						ignoreButton.click();
						break;
					}
				}
			}
			catch (Exception e) {
				System.out.println("Error occured while skipping the cookies "+e.getMessage());
				continue;
			}
		}
	}
	
	private void searchKeyword(String inputKeyword) {
		ElementHandle searchBar = page.querySelector("div.pl-input-text input");
		if (searchBar != null) {
			searchBar.fill(inputKeyword);
			page.keyboard().press("Enter");
		}
	}
	
	private Collector<ElementHandle, List<String>, List<String>> getCollector(String queryType) {
		//Initialize the supplier
		Supplier<List<String>> supplier = ArrayList::new;
		
		//Initialize the accumulator
		BiConsumer<List<String>, ElementHandle> accumulator = new BiConsumer<>() {
			@Override
			public void accept(List<String> inputList, ElementHandle element) {
				if (queryType.equals("Prices")) {
					String priceString = element.querySelector("p").textContent().trim();
					inputList.add(priceString);
				}
				else {
					String bookString = element.querySelector("h3").textContent().trim();
					inputList.add(bookString);
				}
				
			}
		};
		
		//Initialize the combiner
		BinaryOperator<List<String>> combiner = new BinaryOperator<>() {
			@Override
			public List<String> apply(List<String>firstList, List<String> secondList) {
				ArrayList<String> finalList = new ArrayList<>(firstList);
				finalList.addAll(secondList);
				return finalList;
			}
			
		};
		
		Collector<ElementHandle, List<String>, List<String>> myCollector = Collector.of(supplier, accumulator, 
																			combiner, Collector.Characteristics.CONCURRENT,
																			Collector.Characteristics.IDENTITY_FINISH);
		return myCollector;
	}
	
	private List<String> getProductNames() {
		page.waitForLoadState(LoadState.LOAD);
		while (true) {
			try {
				List<String> nameList;
				ElementHandle waitElement = page.waitForSelector("div.ds-product-card__title.ds-product-card__shimzone--large");
				Collector<ElementHandle, List<String>, List<String>> collector = getCollector("Books");
				if (waitElement != null) {
					List<ElementHandle> elementList = page.querySelectorAll("div.ds-product-card__title.ds-product-card__shimzone--large");
					nameList = elementList.stream()
							           .map(element -> element.querySelector("h3").textContent().trim())
									   .collect(Collectors.toList());
				}
				else {
					nameList = new ArrayList<>();
				}
				return nameList;
			}
			catch (Exception e) {
				System.out.println("Error while getting the products "+e);
				continue;
			}
		}
	}
	
	private List<String> getPrices() {
		List<String> priceList;
		ElementHandle waitElement = page.waitForSelector("div.product-price__amount.product-price__amount--main");
		Collector<ElementHandle, List<String>, List<String>> collector = getCollector("Prices");
		if (waitElement != null) {
			List<ElementHandle> elementList = page.querySelectorAll("div.product-price__amount.product-price__amount--main");
			
			priceList = elementList.stream()
								   .map(element -> element.querySelector("p").textContent().trim())
								   .collect(Collectors.toList());
		}
		else {
			priceList = new ArrayList<>();
		}
		return priceList;
	}
	
	private List<String> getUrls() {
		List<ElementHandle> urlElements = page.querySelectorAll("div.ds-product-card__title.ds-product-card__shimzone--large");
		Function<ElementHandle, String> extractUrls = inputElement -> {
			ElementHandle aElement = inputElement.querySelector("a");
			if (aElement != null) {
				String endpointString = aElement.getAttribute("href");
				String urlString = "https://www.carrefour.fr" + endpointString;
				return urlString;
			}
			else {
				return null;
			}
		};
		List<String> urlList = urlElements.stream()
										  .map(extractUrls)
										  .collect(Collectors.toList());
		return urlList;
	}
	
	private List<String> getImgUrls() {
		List<ElementHandle> imgUrlsElements = page.querySelectorAll("div.product-card-image img");
		List<String> imgUrlsList = imgUrlsElements.stream()
												  .map(inputElement -> inputElement.getAttribute("src"))
												  .collect(Collectors.toList());
		return imgUrlsList;
	}
	
	private void clickAndEraseBox(int currentIndex) {
		ElementHandle waitElement = page.waitForSelector("div[aria-label='Marque']");
		if (waitElement != null) {
			ElementHandle brandElements = page.querySelector("div[aria-label='Marque']");
			List<ElementHandle> boxElements = brandElements.querySelectorAll("div.filters-checkbox__option div label div span");		
			ElementHandle currentBox = boxElements.get(currentIndex);
			if (currentBox != null) {
				currentBox.click();
			}
		}
	}
	
	private void scrollAndClick() {
		while (true) {
			try {
				page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
				ElementHandle waitElement = page.waitForSelector("div.pagination__button-wrap button[aria-live=\"polite\"] span.c-button__content");
				if (waitElement != null) {
					ElementHandle expandButton = page.querySelector("div.pagination__button-wrap button[aria-live=\"polite\"] span.c-button__content");
					if (expandButton != null) {
						expandButton.click();
					}
				}
				break;	
			}
			catch (Exception e) {
				System.out.println("Error occured while clicking the page "+e.getMessage());
				continue;
			}
		}
	}
	
	private String checkEnd() {
		page.waitForLoadState(LoadState.LOAD);
		WaitForSelectorOptions waitingOption = new WaitForSelectorOptions();
		waitingOption.setTimeout(3000);
		try {
			ElementHandle waitElement = page.waitForSelector("div.pagination__bar-bg div[style='width: 100%;']"
					, waitingOption);
			if (waitElement != null) {
					return "End";
				}
			else {
					return "Continue";
				}	
			}
		catch (Exception e) {
			return "Continue";
		}
	}
	
	private String getBrand(int currentBox) {
		ElementHandle waitElement = page.waitForSelector("div[aria-label='Marque']");
		if (waitElement != null) {
			ElementHandle brandLocator = page.querySelector("div[aria-label='Marque']");
			List<ElementHandle> brandElements = brandLocator.querySelectorAll("span.filters-checkbox__option__label");
			ElementHandle returnedBrand = brandElements.get(currentBox);
			if (returnedBrand != null) {
				String brandString = returnedBrand.textContent();
				return brandString;
			}
			else {
				return "Not Found";
			}
		}
		else {
			return "Not Found";
		}
	}
	
	private List<String> getProductID() {
		List<ElementHandle> articleList = page.querySelectorAll("li.product-grid-item article");
		List<String> idList = articleList.stream()
										 .map(element -> element.getAttribute("id"))
										 .collect(Collectors.toList());
		return idList;	
	};
	
	private List<String> getRatings() {
		List<ElementHandle> ratingAnchor = page.querySelectorAll("div.main-layout__infos-product");
		
		Function<ElementHandle, String> extractRatings = inputElement -> {
			ElementHandle ratingElement = inputElement.querySelector("div.main-layout__rating div p");
			if (ratingElement != null) {
				String ratingString = ratingElement.textContent().trim();
				String ratingPattern = "\\((\\d+)\\)";
				Pattern pattern = Pattern.compile(ratingPattern);
				Matcher matcher = pattern.matcher(ratingString);
				if (matcher.find()) {
					return  matcher.group(1);
				}
				else {
					return null;
				}
			}
			else {
				return null;
			}
		};
		
		List<String> ratingList = ratingAnchor.stream()
								   .map(extractRatings)
								   .collect(Collectors.toList());
		return ratingList;
	}
	
	private List<String> getStars() {
		List<ElementHandle> starsAnchor = page.querySelectorAll("div.main-layout__infos-product");
		
		Function<ElementHandle, String> extractStars = inputElement -> {
			ElementHandle starsElement= inputElement.querySelector("div.main-layout__rating a span");
			if (starsElement != null) {
				String starsString = starsElement.textContent().trim();
				String stringPattern = ".*de\\s*(.*)";
				Pattern starsPattern = Pattern.compile(stringPattern);
				Matcher matcher = starsPattern.matcher(starsString);
				if (matcher.find()) {
					String stars = matcher.group(1);
					stars = stars.replace(" sur ", "/");
					return stars;
				}
				else {
					return null;
				}
			}
			else {
				return null;
			}
		};
		List<String> starsList = starsAnchor.stream()
											.map(extractStars)
											.collect(Collectors.toList());
		return starsList;
	}
	
	private List<String> getBasesize() {
		List<ElementHandle> divElements = page.querySelectorAll("div.main-layout__infos");
		
		//Create the function to map the elements
		Function<ElementHandle, String> mapBaseSize = inputElement -> {
			ElementHandle divInfoElement = inputElement.querySelector("div.ds-product-card-refonte__shimzone--small");
			if (divInfoElement != null) {
				String baseSizeString = divInfoElement.querySelector("p").textContent();
				return baseSizeString;
			}
			else {
				return null;
			}
		};
		List<String> sizeList = divElements.stream()
				.map(mapBaseSize)
				.collect(Collectors.toList());
		return sizeList;
	};
	
 	public void closeBrowser() {
 		context.close();
 		page.close();
		browser.close();
		System.out.println("Closed browser connection");
	}
 	
}
