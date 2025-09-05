package com.aycf.flightFinder.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebDriverCleanupLogoutHandler implements LogoutHandler {
    @Override
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object driverObj = session.getAttribute("webdriver");
            if (driverObj instanceof WebDriver driver) {
                driver.quit();
                log.info("WebDriver instance closed successfully for session {}", session.getId());
            }
            session.removeAttribute("webdriver");
        }
    }
}
