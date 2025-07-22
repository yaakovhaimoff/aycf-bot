package com.aycf.flightFinder.service;

import com.aycf.flightFinder.automation.pages.LoginPage;
import com.aycf.flightFinder.automation.pages.SearchFlightsPage;
import com.aycf.flightFinder.model.Destination;
import com.aycf.flightFinder.model.Flight;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchFlightsService implements IFlightSearchService {
    private List<Destination> possibleConnections;
    @Override
    public List<Flight> searchDirectFlight(WebDriver webDriver, String originQuery, String originFull,
                                           String destQuery, String destFull, String date) {
        SearchFlightsPage searchPage = new SearchFlightsPage(webDriver, originQuery, originFull, destQuery, destFull, date);
        possibleConnections = getPossibleConnections(searchPage, originQuery, originFull, destQuery, destFull);
        return checkFlightAvailability(searchPage);
    }
    @Override
    public List<Flight> searchFlightsWithConnections(WebDriver webDriver, String originQuery, String originFull,
                                                     String destQuery, String destFull, String date) {
        String LOGIN_URL = "https://multipass.wizzair.com";
        LoginPage loginPage = new LoginPage(webDriver, LOGIN_URL);
        List<Flight> validConnections = new ArrayList<>();
        for (Destination connection : possibleConnections) {
            String connectionQuery = connection.destinationQuery();
            String connectionFull = connection.destinationFull();
            loginPage.openHomePage();

            List<Flight> firstLegFlights = checkFlightAvailability(
                    new SearchFlightsPage(webDriver, originQuery, originFull, connectionQuery, connectionFull, date)
            );

            if (!firstLegFlights.isEmpty()) {
                loginPage.openHomePage();
                List<Flight> secondLegFlights = checkFlightAvailability(
                        new SearchFlightsPage(webDriver, connectionQuery, connectionFull, destQuery, destFull, date)
                );

                if (!secondLegFlights.isEmpty()) {
                    validConnections.addAll(firstLegFlights);
                    validConnections.addAll(secondLegFlights);
                }
            }
        }
        return validConnections;
    }
    private List<Flight> checkFlightAvailability(SearchFlightsPage searchPage){
        searchPage.fillRoute();
        searchPage.selectDate();
        searchPage.clickSearch();
        return searchPage.scrapeResults();
    }
    private List<Destination> getPossibleConnections(SearchFlightsPage searchPage, String originQuery, String originFull,
                                                    String destQuery, String destFull ){
        log.info("Getting destinations for origin: '{}'", originFull);
        List<Destination> originToConnectionDestinations = searchPage.getAvailableDestinations("autocomplete-origin", "autocomplete-destination", originQuery, originFull);
        log.info("Getting destinations for connection: '{}'", destFull);
        List<Destination> connectionToDestinationDestinations = searchPage.getAvailableDestinations("autocomplete-destination", "autocomplete-origin", destQuery, destFull);
        log.info("Found {} final destinations from '{}'", connectionToDestinationDestinations.size(), destFull);
        List<Destination> possibleConnections = filterPossibleConnections(originToConnectionDestinations, connectionToDestinationDestinations);
        log.info("Found {} possible connections from '{}' to '{}'", possibleConnections.size(), originFull, destFull);
        return possibleConnections;
    }
    private List<Destination> filterPossibleConnections(List<Destination> originToConnectionDestinations, List<Destination>connectionToDestinationDestinations) {
        Set<String> validPrefixes = originToConnectionDestinations.stream()
                .map(Destination::destinationQuery)
                .collect(Collectors.toSet());
        return connectionToDestinationDestinations.stream()
                .filter(d -> validPrefixes.contains(d.destinationQuery()))
                .toList();
    }
}