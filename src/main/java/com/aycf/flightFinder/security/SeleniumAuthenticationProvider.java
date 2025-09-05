package com.aycf.flightFinder.security;

import com.aycf.flightFinder.automation.webdriver.WebDriverFactory;
import com.aycf.flightFinder.model.UserCredentials;
import com.aycf.flightFinder.service.ICredential;
import com.aycf.flightFinder.service.LoginService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;

@Slf4j
@Component
public class SeleniumAuthenticationProvider implements AuthenticationProvider {
    private final LoginService loginService;
    private final ICredential credentialService;

    @Autowired
    public SeleniumAuthenticationProvider(LoginService loginService,
                                          ICredential credentialService) {
        this.loginService = loginService;
        this.credentialService = credentialService;
    }
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = authentication.getCredentials().toString();
        WebDriver driver = WebDriverFactory.createDriver(WebDriverFactory.BrowserType.CHROME);

        if (!loginService.login(driver, email, password)) {
            throw new BadCredentialsException("Invalid email or password");
        }
        log.info("User {} authenticated successfully.", email);

        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        storeWebDriverInSession(attr, driver);
        storeCredentials(attr, email, password);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new UsernamePasswordAuthenticationToken(email, password, authorities);
    }
    private void storeWebDriverInSession(ServletRequestAttributes attr, WebDriver driver) {
        log.info("Storing webdriver with session {}", attr.getRequest().getSession().getId());
        attr.getRequest().getSession().setAttribute("webdriver", driver);
    }
    private void storeCredentials(ServletRequestAttributes attr, String email, String password) {
        HttpSession session = attr.getRequest().getSession();
        credentialService.store(session.getId(), new UserCredentials(email, password));
    }
    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
