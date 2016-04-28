package mc;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.util.*;

import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.internal.ProfilesIni;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.sun.javafx.iio.ImageStorageException;

public class MoatPageVisitor {

	public static String site = "https://moat.com/advertiser/";
	public static String downloadPath = "/Users/dhowe/Desktop/AdCollage/Cars/";

	public static WebDriver getDriver() {
		String profileName = "Vanilla";

		FirefoxProfile ffp = new ProfilesIni().getProfile(profileName);
		if (ffp == null)
			throw new RuntimeException("Unable to load profile: " + profileName);

		WebDriver driver = new FirefoxDriver(ffp);
		return driver;
	}

	public static int imagesForAdvertiser(WebDriver driver, String name, Deque<String> pending) {

		try {

			return imagesForAdvertiser(driver, site + name, name, pending);

		} catch (Exception e) {

			System.err.println(e.getMessage());
			return 0;
		}
	}

	public static int imagesForAdvertiser(WebDriver driver, String url, String name, Deque<String> pending)
			throws Exception {

		if (!new File(downloadPath).exists())
			throw new RuntimeException("No such path: " + downloadPath);

		System.out.println("\nVisiting " + url);
		driver.get(url);

		Downloader downloader = new Downloader(driver, downloadPath + name);

		WebDriverWait wait = new WebDriverWait(driver, 20);
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("img-holder")));

		List<WebElement> links = driver.findElements(By.cssSelector(".related-brands a"));
		for (Iterator it = links.iterator(); it.hasNext();) {

			WebElement link = (WebElement) it.next();
			String href = link.getAttribute("href");
			if (href != null) {
				String related = href.substring(href.lastIndexOf("/") + 1);
				if (!pending.contains(related)) {
					pending.push(related);
				} else
					System.out.println("Skipping related: " + related);
			}
		}
		System.out.println("  Pending " + pending);


		// className("pull-right header-half related-brands"));
		List<String> paths = new ArrayList<String>();
		List<WebElement> wimgs = driver.findElements(By.className("img-holder"));
		System.out.println("  Found " + wimgs.size() + " image tags for '" + name + "'");
		for (Iterator iterator = wimgs.iterator(); iterator.hasNext();) {
			WebElement img = (WebElement) iterator.next();
			// String src = img.getAttribute("src");
			
			String toFile = null;
			try {
				toFile = downloader.downloadImage(img);
				if (toFile != null) {
					paths.add(toFile);
					System.out.print(".");
				}
				else {
					System.out.print("d");
				}
			} catch (Throwable ef) {
				System.err.println("Unexpected Exception: "+ef.getMessage());
				//throw ef;
			}
		}
		System.out.println();

		return paths.size();
	}

	public static void main(String[] args) throws Exception {

		String startBrand = args.length > 0 ? args[0] : "google";

		int total = 0;
		Deque<String> pending = new ArrayDeque<String>();
		Deque<String> completed = new ArrayDeque<String>();
		WebDriver driver = MoatPageVisitor.getDriver();
		pending.add(startBrand);

		while (!pending.isEmpty()) {

			String next = pending.pop();
			while (completed.contains(next)) {
				if (!pending.isEmpty())
					next = pending.pop();
			}

			int found = imagesForAdvertiser(driver, next, pending);
			total += found;
			System.out.println("Downloaded " + found + "/" + total + " images for '" + next + "', " + pending.size()
					+ " brand(s) waiting: "+pending);

			completed.push(next);
		}

		System.out.println("Downloaded " + total + " images");

		driver.close();
	}
}
