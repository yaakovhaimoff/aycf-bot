package com.aycf.flightFinder.automation.webdriver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
//import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import java.net.URL;

public class WebDriverFactory {

    public enum BrowserType {
        CHROME, FIREFOX
    }

    private static final String GRID_URL = "http://localhost:4444/wd/hub";

    public static WebDriver createDriver( BrowserType browserType ) {
        try {
            switch ( browserType ) {
                case CHROME:
                    ChromeOptions chromeOptions = new ChromeOptions();
                    chromeOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--incognito");
//                    chromeOptions.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage", "--incognito");
                    return new ChromeDriver( chromeOptions );

                case FIREFOX:
                default:
                    FirefoxOptions firefoxOptions = new FirefoxOptions();
                    firefoxOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage");
                    firefoxOptions.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
                    return new FirefoxDriver( firefoxOptions );
            }
        } catch ( Exception e ) {
            throw new RuntimeException("Failed to create WebDriver: " + e.getMessage(), e);
        }
    }
}