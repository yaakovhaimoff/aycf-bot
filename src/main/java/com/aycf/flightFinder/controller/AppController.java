package com.aycf.flightFinder.controller;

import com.aycf.flightFinder.automation.webdriver.WebDriverFactory;
import com.aycf.flightFinder.model.Flight;
import com.aycf.flightFinder.model.UserCredentials;
import com.aycf.flightFinder.service.ICredential;
import com.aycf.flightFinder.service.IFlightSearchService;
import jakarta.servlet.http.HttpSession;
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
    private final IFlightSearchService flightSearchService;
    private final ICredential credentialService;
    WebDriver driver;

    @Autowired
    public AppController(LoginService loginService,
                         IFlightSearchService flightSearchService,
                         ICredential credentialService) {
        this.loginService = loginService;
        this.flightSearchService = flightSearchService;
        this.credentialService = credentialService;
    }

    @GetMapping
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String password,
                        HttpSession session,
                        Model model) {
        log.info("Accessing login page post");
        driver = WebDriverFactory.createDriver(WebDriverFactory.BrowserType.CHROME);
        credentialService.store(session.getId(), new UserCredentials(email, password));
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
        List<Flight> flights = flightSearchService.searchDirectFlight(driver, origin_query, origin_full, dest_query, dest_full, date);
        handleFlights(flights, model, "no_direct_flights", "No Direct Flights Found");
        model.addAttribute("show_results", true);
        model.addAttribute("origin_query", origin_query);
        model.addAttribute("origin_full", origin_full);
        model.addAttribute("dest_query", dest_query);
        model.addAttribute("dest_full", dest_full);
        model.addAttribute("date", date);
        return "searchFlights";
    }

    @PostMapping("/search-connections")
    public String searchFlightsWithConnections(@RequestParam("origin_query") String origin_query,
                                           @RequestParam("origin_full") String origin_full,
                                           @RequestParam("dest_query") String dest_query,
                                           @RequestParam("dest_full") String dest_full,
                                           @RequestParam("date") String date,
                                           HttpSession session,
                                           Model model) {
        log.info("Searching flights with connections from: " + origin_query + " to " + dest_query + " on " + date);
        List<Flight> flights = flightSearchService.searchFlightsWithConnections(driver, origin_query, origin_full, dest_query, dest_full, date, session.getId());
        handleFlights(flights, model, "no_connections", "No Connecting Flights Found");
        model.addAttribute("origin_full", origin_full);
        model.addAttribute("dest_full", dest_full);
        model.addAttribute("date", date);
        return "searchFlights";
    }

    private void handleFlights(List<Flight> flights, Model model, String modelAttribute, String errorMessage) {
        if (flights.isEmpty()) {
            model.addAttribute(modelAttribute, true);
            log.info(errorMessage);
        } else {
            model.addAttribute("flights", flights);
            log.info("in flight not empty");
        }
        model.addAttribute("show_results", true);
    }
}
