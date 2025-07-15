package com.aycf.flightFinder.controller;

import com.aycf.flightFinder.automation.webdriver.WebDriverFactory;
import com.aycf.flightFinder.model.Flight;
import com.aycf.flightFinder.service.FlightSearchService;
import org.openqa.selenium.WebDriver;
import org.springframework.ui.Model;
import com.aycf.flightFinder.service.LoginService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/")
public class AppController {
    private final LoginService loginService;
    private final FlightSearchService flightSearchService;
    WebDriver driver;

    @Autowired
    public AppController(LoginService loginService,
                         FlightSearchService flightSearchService) {
        this.loginService = loginService;
        this.flightSearchService = flightSearchService;
    }

    @GetMapping
    public String loginPage() {
        log.info("Accessing login page get");
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String password,
                        Model model) {
        log.info("Accessing login page post");
        driver = WebDriverFactory.createDriver(WebDriverFactory.BrowserType.CHROME);
        boolean success = loginService.login(driver, email, password);

        if (success) {
            return "searchFlights";
        } else {
            model.addAttribute("error", true);
            return "login";
        }
    }

    @PostMapping("/search")
    public String searchFlights(@RequestParam("origin_query") String origin_query,
                                @RequestParam("origin_full") String origin_full,
                                @RequestParam("dest_query") String dest_query,
                                @RequestParam("dest_full") String dest_full,
                                @RequestParam("date") String date,
                                Model model) {
        log.info("Searching flights with origin: " + origin_query + ", destination: " + dest_query + ", date: " + date);
        List<Flight> flights = flightSearchService.search(driver, origin_query, origin_full, dest_query, dest_full, date);
        if (flights.isEmpty()) {
            model.addAttribute("error", "No flights found for the given criteria.");
            return "searchFlights";
        }
        model.addAttribute("flights", flights);
        model.addAttribute("show_results", true);
        model.addAttribute("origin_full", origin_full);
        model.addAttribute("dest_full", dest_full);
        model.addAttribute("date", date);

        return "searchFlights";
    }
}
