package com.aycf.flightFinder.automation.webdriver;

import com.aycf.flightFinder.features.login.LoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebDriverSessionManager {

    private final LoginService loginService;
    private static final AtomicInteger activeBrowsers = new AtomicInteger(0);

    public <T> T executeWithAuth(String email, String password, Function<WebDriver, T> operation) {
        WebDriver driver = createAndLogin(email, password);
        try {
            return operation.apply(driver);
        } finally {
            closeDriver(driver);
        }
    }

    public void executeWithAuth(String email, String password, Consumer<WebDriver> operation) {
        WebDriver driver = createAndLogin(email, password);
        try {
            operation.accept(driver);
        } finally {
            closeDriver(driver);
        }
    }

    private WebDriver createAndLogin(String email, String password) {
        WebDriver driver = WebDriverFactory.createDriver(WebDriverFactory.BrowserType.CHROME);
        int browsersNow = activeBrowsers.incrementAndGet();
        log.debug("Browser opened. Active browsers: {}", browsersNow);

        try {
            boolean success = loginService.login(driver, email, password);
            if (!success) {
                throw new RuntimeException("Login failed for: " + email);
            }
            return driver;
        } catch (Exception e) {
            closeDriver(driver);
            throw e;
        }
    }

    private void closeDriver(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                int remaining = activeBrowsers.decrementAndGet();
                log.debug("Browser closed. Remaining browsers: {}", remaining);
            }
        }
    }
}
