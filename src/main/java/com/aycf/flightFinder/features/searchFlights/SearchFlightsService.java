package com.aycf.flightFinder.features.searchFlights;

import com.aycf.flightFinder.automation.pages.LoginPage;
import com.aycf.flightFinder.automation.pages.SearchFlightsPage;
import com.aycf.flightFinder.automation.webdriver.WebDriverSessionManager;
import com.aycf.flightFinder.features.searchFlights.model.SearchRequest;
import com.aycf.flightFinder.features.UserCredentials.WizzCredentialProvider;
import com.aycf.flightFinder.features.UserCredentials.WizzCredentialProvider.WizzCredentials;
import com.aycf.flightFinder.features.flightsFromPdf.IFlightsFromPdfService;
import com.aycf.flightFinder.features.searchFlights.model.ConnectionFlightResult;
import com.aycf.flightFinder.features.searchFlights.model.Destination;
import com.aycf.flightFinder.features.searchFlights.model.Flight;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFlightsService implements ISearchFlightsService {
    private final WizzCredentialProvider credentialProvider;
    private final IFlightsFromPdfService flightsFromPdfService;
    private final WebDriverSessionManager sessionManager;
    private final int MAX_THREADS = 4;
    @Override
    @Timed(value = "flightFinder.searchDirectFlight", description = "Time taken to search direct flights")
    public List<Flight> searchDirectFlight(SearchRequest searchRequest) {
        String originFull = searchRequest.originFull();
        String destFull = searchRequest.destFull();
        String date = searchRequest.date();

        if (!flightsFromPdfService.hasRoute(originFull, destFull)) {
            log.info("Route not found in parsed Wizz network: {} -> {}", originFull, destFull);
            return List.of();
        }

        log.info("Route is available: {} -> {} on {}", originFull, destFull, date);
        WizzCredentials credentials = credentialProvider.getCredentialsForCurrentUser();

        try {
            return sessionManager.executeWithAuth(
                    credentials.email(),
                    credentials.password(),
                    driver -> {
                        LoginPage loginPage = new LoginPage(driver);
                        loginPage.openHomePage();
                        SearchFlightsPage searchPage = new SearchFlightsPage(driver, searchRequest);
                        return checkFlightAvailability(searchPage);
                    }
            );
        } catch (Exception e) {
            log.error("Error searching direct flight: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Timed(value = "flightFinder.searchFlightsWithConnections", description = "Time taken to resolve connection routes")
    public List<Destination> searchFlightsWithConnections(SearchRequest searchRequest) {
        WizzCredentials credentials = credentialProvider.getCredentialsForCurrentUser();

        List<Destination> possibleConnectionsFromUI = getPossibleConnectionsThreadSafe(searchRequest, credentials);

        List<Destination> validConnections = flightsFromPdfService.getPossibleConnections(
                searchRequest.originFull(),
                searchRequest.destFull(),
                possibleConnectionsFromUI
        );

        if (validConnections.isEmpty()) {
            log.info("No valid connections found for {} -> {}", searchRequest.originFull(), searchRequest.destFull());
        } else {
            log.info("Found {} valid connection routes for {} -> {}",
                    validConnections.size(), searchRequest.originFull(), searchRequest.destFull());
        }
        return validConnections;
    }

    @Override
    @Timed(value = "flightFinder.searchFlightViaConnection", description = "Time taken to search a specific connection route")
    public ConnectionFlightResult searchFlightViaConnection(SearchRequest firstLeg, SearchRequest secondLeg) {
        WizzCredentials credentials = credentialProvider.getCredentialsForCurrentUser();
        String via = firstLeg.destFull();
        try {
            return sessionManager.executeWithAuth(
                    credentials.email(),
                    credentials.password(),
                    driver -> {
                        LoginPage loginPage = new LoginPage(driver);
                        loginPage.openHomePage();

                        log.info("Searching first leg: {} -> {}", firstLeg.originFull(), via);
                        List<Flight> leg1 = checkFlightAvailability(new SearchFlightsPage(driver, firstLeg));

                        if (leg1.isEmpty()) {
                            log.info("No first-leg flights — skipping second leg");
                            return new ConnectionFlightResult(List.of(), List.of());
                        }

                        log.info("First leg found {} flight(s). Searching second leg: {} -> {}", leg1.size(), via, secondLeg.destFull());
                        loginPage.openHomePage();
                        List<Flight> leg2 = checkFlightAvailability(new SearchFlightsPage(driver, secondLeg));

                        log.info("Second leg found {} flight(s)", leg2.size());
                        return new ConnectionFlightResult(leg1, leg2);
                    }
            );
        } catch (Exception e) {
            log.error("Error searching connection flight via {}: {}", via, e.getMessage(), e);
            return new ConnectionFlightResult(List.of(), List.of());
        }
    }

    @Override
    @Timed(value = "flightFinder.searchNextDayFlights", description = "Time taken to search next 3 day flights")
    public List<Flight> searchNextThreeDaysFlights(SearchRequest searchRequest) {
        String originFull = searchRequest.originFull();
        String destFull = searchRequest.destFull();

        if (!flightsFromPdfService.hasRoute(originFull, destFull)) {
            log.info("Route not found in parsed Wizz network: {} -> {}", originFull, destFull);
            return List.of();
        }

        log.info("Route is available: {} -> {}", originFull, destFull);
        WizzCredentials credentials = credentialProvider.getCredentialsForCurrentUser();

        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        LocalDate baseDate = LocalDate.now();

        List<Callable<List<Flight>>> tasks = IntStream.range(0, MAX_THREADS)
                .mapToObj(i -> {
                    String date = baseDate.plusDays(i).toString();
                    SearchRequest datedRequest = new SearchRequest(
                            searchRequest.originQuery(),
                            searchRequest.originFull(),
                            searchRequest.destQuery(),
                            searchRequest.destFull(),
                            date
                    );
                    return (Callable<List<Flight>>) () -> searchFlightsForDate(datedRequest, credentials);
                })
                .toList();

        List<Flight> allFlights = new ArrayList<>();
        try {
            log.info("Launching threaded search for next 4 days with {} threads", MAX_THREADS);
            List<Future<List<Flight>>> futures = executor.invokeAll(tasks);
            for (Future<List<Flight>> future : futures) {
                try {
                    allFlights.addAll(future.get());
                } catch (Exception e) {
                    log.error("Error fetching flights from future: {}", e.getMessage(), e);
                }
            }
        } catch (InterruptedException e) {
            log.error("Thread pool interrupted: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            log.info("Finished threaded search for next days. Total flights found: {}", allFlights.size());
        }
        return allFlights;
    }

    private List<Flight> searchFlightsForDate(SearchRequest searchRequest, WizzCredentials credentials) {
        String date = searchRequest.date();

        try {
            return sessionManager.executeWithAuth(
                    credentials.email(),
                    credentials.password(),
                    driver -> {
                        log.info("[NextDay-{}] Searching flights for: {} -> {}",
                                date, searchRequest.originFull(), searchRequest.destFull());

                        LoginPage loginPage = new LoginPage(driver);
                        loginPage.openHomePage();
                        SearchFlightsPage searchPage = new SearchFlightsPage(driver, searchRequest);
                        List<Flight> flights = checkFlightAvailability(searchPage);

                        log.info("[NextDay-{}] Found {} flights", date, flights.size());
                        return flights;
                    }
            );
        } catch (Exception e) {
            log.error("[NextDay-{}] Error during search: {}", date, e.getMessage(), e);
            return List.of();
        }
    }

    private List<Destination> getPossibleConnectionsThreadSafe(SearchRequest searchRequest,
                                                                WizzCredentials credentials) {
        try {
            return sessionManager.executeWithAuth(
                    credentials.email(),
                    credentials.password(),
                    driver -> {
                        LoginPage loginPage = new LoginPage(driver);
                        loginPage.openHomePage();
                        SearchFlightsPage searchPage = new SearchFlightsPage(driver, searchRequest);
                        return getPossibleConnectionsFromUI(searchPage, searchRequest);
                    }
            );
        } catch (Exception e) {
            log.error("Error getting possible connections: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Flight> checkFlightAvailability(SearchFlightsPage searchPage) {
        searchPage.fillRoute();
        searchPage.selectDate();
        searchPage.clickSearch();
        return searchPage.scrapeResults();
    }

    private List<Destination> getPossibleConnectionsFromUI(SearchFlightsPage searchPage, SearchRequest searchRequest) {
        String originQuery = searchRequest.originQuery();
        String originFull = searchRequest.originFull();
        String destQuery = searchRequest.destQuery();
        String destFull = searchRequest.destFull();

        log.info("Getting destinations for origin: '{}'", originFull);
        List<Destination> originToConnectionDestinations = searchPage.getAvailableDestinations(
                "autocomplete-origin", "autocomplete-destination", originQuery, originFull);

        log.info("Getting destinations for connection: '{}'", destFull);
        List<Destination> connectionToDestinationDestinations = searchPage.getAvailableDestinations(
                "autocomplete-destination", "autocomplete-origin", destQuery, destFull);

        log.info("Found {} final destinations from '{}'", connectionToDestinationDestinations.size(), destFull);
        List<Destination> possibleConnections = filterPossibleConnections(
                originToConnectionDestinations, connectionToDestinationDestinations);
        log.info("Found {} possible connections from '{}' to '{}'", possibleConnections.size(), originFull, destFull);

        return possibleConnections;
    }

    private List<Destination> filterPossibleConnections(List<Destination> originToConnectionDestinations,
                                                        List<Destination> connectionToDestinationDestinations) {
        Set<String> validPrefixes = originToConnectionDestinations.stream()
                .map(Destination::destinationQuery)
                .collect(Collectors.toSet());
        return connectionToDestinationDestinations.stream()
                .filter(d -> validPrefixes.contains(d.destinationQuery()))
                .toList();
    }
}
