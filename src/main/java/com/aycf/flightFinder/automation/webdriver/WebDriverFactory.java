package com.aycf.flightFinder.automation.webdriver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.time.Duration;

public class WebDriverFactory {
    public enum BrowserType {
        CHROME, FIREFOX
    }
    public static WebDriver createDriver(BrowserType browserType) {
        try {
            WebDriver driver;
            boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "false"));

            switch (browserType) {
                case CHROME:
                    ChromeOptions chromeOptions = new ChromeOptions();
                    String chromeBin = System.getenv("CHROME_BIN");
                    if (chromeBin != null && !chromeBin.isBlank()) {
                        chromeOptions.setBinary(chromeBin);
                    }
                    String chromeDriverPath = System.getenv("CHROME_DRIVER");
                    if (chromeDriverPath != null && !chromeDriverPath.isBlank()) {
                        System.setProperty("webdriver.chrome.driver", chromeDriverPath);
                    }
                    chromeOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--incognito");
                    if (headless) {
                        chromeOptions.addArguments("--headless=new", "--disable-gpu", "--window-size=1920,1080");
                    }
                    driver = new ChromeDriver(chromeOptions);
                    break;

                case FIREFOX:
                default:
                    FirefoxOptions firefoxOptions = new FirefoxOptions();
                    firefoxOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage");
                    firefoxOptions.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
                    driver = new FirefoxDriver(firefoxOptions);
                    break;
            }

            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

            return driver;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create WebDriver: " + e.getMessage(), e);
        }
    }
}