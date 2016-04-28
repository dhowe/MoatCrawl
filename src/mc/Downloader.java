package mc;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.FileAlreadyExistsException;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.openqa.selenium.*;

import com.sun.javafx.iio.ImageStorageException;

public class Downloader {

	public static boolean SILENT = true;

	private WebDriver driver;
	private File downloadPath;
	private boolean followRedirects = true;
	private boolean useCookies = true;
	private int lastHttpStatus = 0;

	public Downloader(WebDriver driver) {
		this(driver, System.getProperty("java.io.tmpdir"));
	}

	public Downloader(WebDriver driver, String path) {
		
		this.driver = driver;
		
		if (!path.endsWith("/")) path += "/";
		
		this.downloadPath = new File(path);
		
		if (!downloadPath.exists())
			downloadPath.mkdir();
		
		if (!downloadPath.exists())
			throw new RuntimeException("Unable to create download path: "+downloadPath);
	}

	/**
	 * Specify if the FileDownloader class should follow redirects when trying to
	 * download a file
	 *
	 * @param value
	 */
	public void followRedirectsWhenDownloading(boolean value) {
		this.followRedirects = value;
	}

	/**
	 * Download the file specified in the href attribute of a WebElement
	 *
	 * @param element
	 * @return
	 * @throws Exception
	 */
	public String downloadFile(WebElement element) throws Exception {
		return download(element, "href");
	}

	/**
	 * Download the image specified in the src attribute of a WebElement
	 *
	 * @param element
	 * @return
	 * @throws Exception
	 */
	public String downloadImage(WebElement element) throws Exception {
		return download(element, "src");
	}

	/**
	 * Gets the HTTP status code of the last download file attempt
	 *
	 * @return
	 */
	public int getHTTPStatusOfLastDownloadAttempt() {
		return this.lastHttpStatus;
	}

	/**
	 * Mimic the cookie state of WebDriver (Defaults to true) This will enable you
	 * to access files that are only available when logged in. If set to false the
	 * connection will be made as an anonymouse user
	 *
	 * @param value
	 */
	public void mimicWebDriverCookieState(boolean value) {
		this.useCookies = value;
	}

	/**
	 * Load in all the cookies WebDriver currently knows about so that we can
	 * mimic the browser cookie state
	 *
	 * @param seleniumCookieSet
	 * @return
	 */
	private BasicCookieStore mimicCookieState(Set seleniumCookieSet) {
		BasicCookieStore mimicWebDriverCookieStore = new BasicCookieStore();
		for (Iterator iterator = seleniumCookieSet.iterator(); iterator.hasNext();) {
			Cookie seleniumCookie = (Cookie) iterator.next();
			BasicClientCookie duplicateCookie = new BasicClientCookie(seleniumCookie.getName(), seleniumCookie.getValue());
			duplicateCookie.setDomain(seleniumCookie.getDomain());
			duplicateCookie.setSecure(seleniumCookie.isSecure());
			duplicateCookie.setExpiryDate(seleniumCookie.getExpiry());
			duplicateCookie.setPath(seleniumCookie.getPath());
			mimicWebDriverCookieStore.addCookie(duplicateCookie);
		}

		return mimicWebDriverCookieStore;
	}

	private String download(WebElement element, String attribute) throws Exception { 

		String fileToDownloadLocation = element.getAttribute(attribute);
		
		if (fileToDownloadLocation.trim().equals(""))
			throw new NullPointerException("The element you have specified does not link to anything!");

		URL fileToDownload = null;
		try {
			fileToDownload = new URL(fileToDownloadLocation);
		} catch (MalformedURLException e) {

			e.printStackTrace();
		}

		InputStream content = doDownload(fileToDownload);
		File out = null;
		try {

			BufferedImage imBuff = ImageIO.read(content);

			// FileUtils.copyInputStreamToFile(content, downloadedFile);
			out = new File(this.downloadPath, toHash(imBuff) + ".png");

			if (out.exists()) {
				return null;
				//throw new FileAlreadyExistsException("Image exists: "+out.getAbsolutePath());
			}
					
			ImageIO.write(imBuff, "png", out);

		} catch (Exception e) {
			
			e.printStackTrace();
		}
		finally {
			try {
				content.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		String dfap = out.getAbsolutePath();
		if (!SILENT)
			System.out.println("Writing '" + dfap + "'");

		return dfap;
	}

	private InputStream doDownload(URL fileToDownload) {

		HttpClient client = new DefaultHttpClient();
		BasicHttpContext localContext = new BasicHttpContext();

		if (!SILENT)
			System.out.println("Mimic WebDriver cookie state: " + this.useCookies);
		if (this.useCookies) {
			localContext.setAttribute(ClientContext.COOKIE_STORE, mimicCookieState(this.driver.manage().getCookies()));
		}

		HttpGet httpget = null;
		try {
			httpget = new HttpGet(fileToDownload.toURI());
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		HttpParams httpRequestParameters = httpget.getParams();
		httpRequestParameters.setParameter(ClientPNames.HANDLE_REDIRECTS, this.followRedirects);
		httpget.setParams(httpRequestParameters);

		if (!SILENT)
			System.out.println("Sending GET request for: " + httpget.getURI());

		HttpResponse response = null;
		try {
			response = client.execute(httpget, localContext);
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.lastHttpStatus = response.getStatusLine().getStatusCode();
		if (this.lastHttpStatus != 200)
			System.err.println("ERROR: " + this.lastHttpStatus);
		if (!SILENT)
			System.out.println("HTTP GET request status: " + this.lastHttpStatus);
		// if (!SILENT) System.out.println("Downloading file: " +
		// downloadedFile.getName());

		InputStream content = null;
		try {
			content = response.getEntity().getContent();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return content;
	}

	static String toHash(BufferedImage buffImg) throws Exception {

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(buffImg, "jpg", bos);
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(bos.toByteArray());
		return bytesToHex(md.digest());
	}

	static String bytesToHex(byte[] inBytes) throws Exception {
		String hexString = "";
		for (int i = 0; i < inBytes.length; i++) {
			hexString += Integer.toString((inBytes[i] & 0xff) + 0x100, 16).substring(1);
		}
		return hexString;
	}

	public static void main(String[] args) throws Exception {

		WebDriver driver = MoatPageVisitor.getDriver();
		Downloader dler = new Downloader(driver, MoatPageVisitor.downloadPath+"test");
		driver.get("http://rednoise.org/adntest/simple.html");
		WebElement image = driver.findElement(By.className("img-holder"));
		String downLoc = dler.downloadImage(image);
		if (downLoc != null)
			System.out.println("Done? " + new File(downLoc).exists());
		else
			System.err.println("NULL RETURN!");
		driver.close();
	}

}