package com.aycf.flightFinder.service;

import com.aycf.flightFinder.automation.pages.LoginPage;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LoginService {
    public boolean login(WebDriver driver, String email, String password) {
        String LOGIN_URL = "https://multipass.wizzair.com";
        LoginPage loginPage = new LoginPage(driver, LOGIN_URL);
            return loginPage.login(email, password);
    }
}
