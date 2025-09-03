package com.aycf.flightFinder.service;

import com.aycf.flightFinder.model.Flight;
import org.openqa.selenium.WebDriver;
import java.util.List;

public interface IFlightSearchService {
    List<Flight> searchDirectFlight(WebDriver webDriver, String originQuery, String originFull,
                                    String destQuery, String destFull, String date);
    List<Flight> searchFlightsWithConnections(WebDriver webDriver, String originQuery, String originFull,
                                                     String destQuery, String destFull, String date, String sessionID);
}
