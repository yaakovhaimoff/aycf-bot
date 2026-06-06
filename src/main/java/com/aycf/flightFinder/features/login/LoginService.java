package com.aycf.flightFinder.features.login;

import com.aycf.flightFinder.automation.pages.LoginPage;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoginService {

    @Timed(value = "flightFinder.login", description = "Time taken to perform login")
    public boolean login(WebDriver driver, String email, String password) {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.openHomePage();
        loginPage.clickSignIn();
        loginPage.fillLoginForm(email, password);
        if (loginPage.isLoginErrorDisplayed()) { return false; }
        loginPage.closeSuccessModalIfPresent();
        log.info("Login successful for user: {}", email);
        return true;
    }
}
