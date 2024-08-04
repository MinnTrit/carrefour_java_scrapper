package playwright;
import com.microsoft.playwright.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class MainPlaywright {
	String urlPath;
	boolean headlessOption;
	Page page;
	int totalPage;
	Browser browser;
	public MainPlaywright(String urlPath, boolean headlessOption, int totalPage) {
		this.urlPath = urlPath;
		this.headlessOption = headlessOption;
		this.browser = initialize();
		this.totalPage = totalPage;
	};
	
	private Browser initialize() {
		System.out.println("Start connecting to the browser");
		Playwright playwright = Playwright.create();
		Browser browser = playwright.chromium()
				.launch(new BrowserType.LaunchOptions().setHeadless(headlessOption));
		Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
		contextOptions.setViewportSize(1368, 1158);
		BrowserContext context = browser.newContext(contextOptions);
		Page page = context.newPage();
		page.navigate(urlPath);
		this.page = page;
		return browser;
	}
	
	public List<Map<String, String>> toScrape() {
		System.out.println("Start getting all the books");
		List<Map<String, String>> finalList = new ArrayList<>();
		for (int pageNum = 0; pageNum < totalPage; pageNum++) {
			List<String> bookList = getAllBooks();
			List<String> ratingList = getAllRatings();
			List<String> priceList = getAllPrices();
			for (int index = 0; index < bookList.size(); index++) {
				Map<String, String> bookMap = new HashMap<>();
				String bookName = bookList.get(index);
				String ratingCount = ratingList.get(index);
				String bookPrice = priceList.get(index);
				bookMap.put("BookName", bookName);
				bookMap.put("Rating", ratingCount);
				bookMap.put("Price", bookPrice);
				finalList.add(bookMap);
			}
			System.out.println("Get all the books on the page number "+pageNum);
			getNextButton();
		}
		return finalList;
	}
	
	private List<String> getAllBooks() {
		List<String> bookList = new ArrayList<>();
		List<ElementHandle> h3Elements = page.querySelectorAll("li.col-xs-6.col-sm-4.col-md-3.col-lg-3 h3");
		for (ElementHandle h3Element : h3Elements) {
			String bookName = h3Element.textContent();
			bookList.add(bookName);
		}
		return bookList;
	}
	
	private List<String> getAllRatings() {
		List<String> ratingList = new ArrayList<>();
		List<ElementHandle> liList = page.querySelectorAll("li.col-xs-6.col-sm-4.col-md-3.col-lg-3");
		for (ElementHandle li : liList) {
			String ratingString = li.querySelector("article.product_pod").querySelector("p").getAttribute("class");
			ratingString = ratingString.replace("star-rating ", "");
			ratingList.add(ratingString);
		}
		return ratingList;
	}
	
	private List<String> getAllPrices() {
		List<ElementHandle> liElements = page.querySelectorAll("li.col-xs-6.col-sm-4.col-md-3.col-lg-3");
		List<String> priceList = liElements.stream()
				.map(liElement -> liElement.querySelector("div.product_price p").textContent())
				.collect(Collectors.toList());
		return priceList;
	}
	
	private void getNextButton() {
		page.evaluate("window.scrollBy(0, document.body.scrollHeight)");
		ElementHandle buttonWait = page.waitForSelector("ul.pager li.next a");
		if (buttonWait != null) {
			ElementHandle nextButton = page.querySelector("ul.pager li.next a");
			if (nextButton.isVisible()) {
				nextButton.click();
			}
		}
	};
	
	public void closeBrowser() {
		browser.close();
		System.out.println("Closed the browser");
	}
	
}
