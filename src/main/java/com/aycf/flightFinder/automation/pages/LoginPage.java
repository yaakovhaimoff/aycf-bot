package com.aycf.flightFinder.automation.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;

@Slf4j
public class LoginPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String baseUrl;
    public LoginPage(WebDriver driver, String baseUrl) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        this.baseUrl = baseUrl;
    }
    public boolean login(String email, String password) {
        openHomePage();
        clickSignIn();
        fillLoginForm(email, password);
        if (isLoginErrorDisplayed()) { return false; }
        closeSuccessModalIfPresent();
        return true;
    }
    public void openHomePage() {
        driver.get(baseUrl);
    }
    private void clickSignIn() {
        WebElement loginBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.CvoHeader-loginButton")));
        loginBtn.click();
        log.info("Login button clicked, waiting for login form to appear.");
    }
    private void fillLoginForm(String email, String password) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username"))).sendKeys(email);
        driver.findElement(By.name("password")).sendKeys(password);
        driver.findElement(By.id("kc-login")).click();
        log.info("Login form submitted, waiting for response.");
    }
    private boolean isLoginErrorDisplayed() {
        try {
            WebElement error = driver.findElement(By.id("input-error"));
            return error.isDisplayed() && error.getText().toLowerCase().contains("invalid email address or password");
        } catch (Exception e) {
            return false;
        }
    }
    private void closeSuccessModalIfPresent() {
        try {
            WebElement closeBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[data-testid='cvo-close']")));
            closeBtn.click();
        } catch (Exception ignored) {}
    }
}
