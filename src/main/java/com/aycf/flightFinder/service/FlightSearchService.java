package com.aycf.flightFinder.service;

import com.aycf.flightFinder.automation.pages.SearchPage;
import com.aycf.flightFinder.model.Flight;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FlightSearchService {
    public List<Flight> search(WebDriver webDriver, String originQuery, String originFull,
                               String destQuery, String destFull, String date) {
        SearchPage searchPage = new SearchPage(webDriver, originQuery, originFull, destQuery, destFull, date);
        return searchPage.checkFlightAvailability();
    }
}