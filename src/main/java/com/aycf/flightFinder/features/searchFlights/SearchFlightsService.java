package com.aycf.flightFinder.features.searchFlights;

import com.aycf.flightFinder.automation.pages.LoginPage;
import com.aycf.flightFinder.automation.pages.SearchFlightsPage;
import com.aycf.flightFinder.automation.webdriver.WebDriverSessionManager;
import com.aycf.flightFinder.controller.model.SearchRequest;
import com.aycf.flightFinder.features.UserCredentials.WizzCredentialProvider;
import com.aycf.flightFinder.features.UserCredentials.WizzCredentialProvider.WizzCredentials;
import com.aycf.flightFinder.features.flightsFromPdf.IFlightsFromPdfService;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    @Timed(value = "flightFinder.searchFlightsWithConnections", description = "Time taken to search flights with connections")
    public List<Flight> searchFlightsWithConnections(SearchRequest searchRequest) {
        WizzCredentials credentials = credentialProvider.getCredentialsForCurrentUser();

        List<Destination> possibleConnectionsFromUI = getPossibleConnectionsThreadSafe(searchRequest, credentials);

        List<Destination> validConnections = flightsFromPdfService.getPossibleConnections(
                searchRequest.originFull(),
                searchRequest.destFull(),
                possibleConnectionsFromUI
        );

        if (validConnections.isEmpty()) {
            log.info("No valid connections found for {} -> {}", searchRequest.originFull(), searchRequest.destFull());
            return List.of();
        }

        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        AtomicInteger counter = new AtomicInteger(0);

        List<Callable<List<Flight>>> tasks = validConnections.stream()
                .map(connection -> (Callable<List<Flight>>) () -> {
                    int connectionNumber = counter.getAndIncrement();
                    return processConnection(connection, searchRequest, connectionNumber, credentials);
                })
                .toList();

        List<Flight> allFlights = new ArrayList<>();
        try {
            log.info("Starting connection search for {} possible connections with up to {} threads",
                    validConnections.size(), MAX_THREADS);

            List<Future<List<Flight>>> futures = executor.invokeAll(tasks);

            for (Future<List<Flight>> future : futures) {
                try {
                    allFlights.addAll(future.get());
                } catch (Exception e) {
                    log.error("Error processing connection: {}", e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            log.error("Execution interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            log.info("All connection tasks finished. Total flights found: {}", allFlights.size());
        }
        return allFlights;
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

    private List<Flight> processConnection(Destination connection, SearchRequest searchRequest,
                                           int connectionNumber, WizzCredentials credentials) {
        String originFull = searchRequest.originFull();
        String destQuery = searchRequest.destQuery();
        String destFull = searchRequest.destFull();
        String date = searchRequest.date();
        String connectionQuery = connection.destinationQuery();
        String connectionFull = connection.destinationFull();

        log.info("[Connection-{}] Starting: {} -> {} -> {}",
                connectionNumber, originFull, connectionFull, destFull);

        try {
            return sessionManager.executeWithAuth(
                    credentials.email(),
                    credentials.password(),
                    driver -> {
                        List<Flight> validFlights = new ArrayList<>();

                        log.info("[Connection-{}] Searching first-leg: {} -> {}", connectionNumber, originFull, connectionFull);
                        LoginPage loginPage = new LoginPage(driver);
                        loginPage.openHomePage();
                        List<Flight> firstLegFlights = checkFlightAvailability(
                                new SearchFlightsPage(driver, searchRequest)
                        );

                        if (firstLegFlights.isEmpty()) {
                            log.info("[Connection-{}] No first-leg flights", connectionNumber);
                            return validFlights;
                        }

                        log.info("[Connection-{}] Searching second-leg: {} -> {}", connectionNumber, connectionFull, destFull);
                        loginPage.openHomePage();

                        SearchRequest secondLegRequest = new SearchRequest(
                                connectionQuery, connectionFull, destQuery, destFull, date
                        );
                        List<Flight> secondLegFlights = checkFlightAvailability(
                                new SearchFlightsPage(driver, secondLegRequest)
                        );

                        if (!secondLegFlights.isEmpty()) {
                            validFlights.addAll(firstLegFlights);
                            validFlights.addAll(secondLegFlights);
                            log.info("[Connection-{}] Found {} total flights via {}",
                                    connectionNumber, validFlights.size(), connectionFull);
                        } else {
                            log.info("[Connection-{}] No second-leg flights", connectionNumber);
                        }

                        return validFlights;
                    }
            );
        } catch (Exception e) {
            log.error("[Connection-{}] Error: {}", connectionNumber, e.getMessage(), e);
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
