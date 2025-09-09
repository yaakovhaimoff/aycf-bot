package com.aycf.flightFinder.service;

import com.aycf.flightFinder.model.Flight;
import jakarta.servlet.http.HttpSession;
import org.openqa.selenium.WebDriver;
import java.util.List;

public interface IFlightSearchService {
    List<Flight> searchDirectFlight(WebDriver webDriver, String originQuery, String originFull,
                                    String destQuery, String destFull, String date);
    List<Flight> searchFlightsWithConnections(String originQuery, String originFull,
                                                     String destQuery, String destFull, String date, String sessionID);
    List <Flight> searchNextThreeDaysFlights(String originQuery, String originFull,
                                       String destQuery, String destFull, String session);
}
