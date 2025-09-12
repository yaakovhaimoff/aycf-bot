package com.aycf.flightFinder.components.searchFlights;

import com.aycf.flightFinder.model.Flight;
import org.openqa.selenium.WebDriver;
import java.util.List;

public interface ISearchFlightsService {
    List<Flight> searchDirectFlight(WebDriver webDriver, String originQuery, String originFull,
                                    String destQuery, String destFull, String date);
    List<Flight> searchFlightsWithConnections(String originQuery, String originFull,
                                                     String destQuery, String destFull, String date, String sessionID);
    List <Flight> searchNextThreeDaysFlights(String originQuery, String originFull,
                                       String destQuery, String destFull, String session);
}
