package com.aycf.flightFinder.components.searchFlights;

import com.aycf.flightFinder.automation.pages.LoginPage;
import com.aycf.flightFinder.automation.pages.SearchFlightsPage;
import com.aycf.flightFinder.automation.webdriver.WebDriverFactory;
import com.aycf.flightFinder.components.flightsFromPdf.IFlightsFromPdfService;
import com.aycf.flightFinder.components.login.LoginService;
import com.aycf.flightFinder.components.UserCredentials.ICredential;
import com.aycf.flightFinder.model.Destination;
import com.aycf.flightFinder.model.Flight;
import com.aycf.flightFinder.model.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import io.micrometer.core.annotation.Timed;

@Slf4j
@Service
public class SearchFlightsService implements ISearchFlightsService {
    private final ICredential credentialService;
    private List<Destination> possibleConnectionsFromUI;
    private static final AtomicInteger activeBrowsers = new AtomicInteger(0);
    private final IFlightsFromPdfService FlightsFromPdfService;

    @Autowired
    public SearchFlightsService(ICredential credentialService,
                                IFlightsFromPdfService FlightsFromPdfService) {
        this.credentialService = credentialService;
        this.FlightsFromPdfService = FlightsFromPdfService;
    }
    @Override
    @Timed(value = "flightFinder.searchDirectFlight", description = "Time taken to search direct flights")
    public List<Flight> searchDirectFlight(WebDriver webDriver, String originQuery, String originFull,
                                           String destQuery, String destFull, String date) {
        LoginPage loginPage = new LoginPage(webDriver);
        loginPage.openHomePage();
        SearchFlightsPage searchPage = new SearchFlightsPage(webDriver, originQuery, originFull, destQuery, destFull, date);
        possibleConnectionsFromUI = getPossibleConnectionsFromUI(searchPage, originQuery, originFull, destQuery, destFull);
        if (!FlightsFromPdfService.hasRoute(originFull, destFull)) {
            log.info("Route not found in parsed Wizz network: {} → {}", originFull, destFull);
            return List.of();
        }
        log.info("Route is available from : {} → {} on {}", originFull, destFull, date);
        return checkFlightAvailability(searchPage);
    }
    @Override
    @Timed(value = "flightFinder.searchFlightsWithConnections", description = "Time taken to search flights with connections")
    public List<Flight> searchFlightsWithConnections(String originQuery, String originFull,
                                                     String destQuery, String destFull,
                                                     String date, String sessionID) {
        int MAX_THREADS = 3;
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        AtomicInteger counter = new AtomicInteger(0);
        List<Destination> possibleConnections = FlightsFromPdfService.getPossibleConnections(originFull, destFull, this.possibleConnectionsFromUI);
        List<Callable<List<Flight>>> tasks = possibleConnections.stream()
                .map(connection -> (Callable<List<Flight>>) () -> {
                    int ConnectionNumber = counter.getAndIncrement();
                    return processConnection(connection, originQuery, originFull, destQuery, destFull, date, ConnectionNumber, sessionID);
                })
                .toList();
        List<Flight> allFlights = new ArrayList<>();
        try {
            log.info("Starting connection search for {} possible connections with up to {} Connections",
                    possibleConnections.size(), MAX_THREADS);

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
    private List<Flight> processConnection(Destination connection,
                                           String originQuery, String originFull,
                                           String destQuery, String destFull,
                                           String date, int ConnectionNumber, String sessionID) {
        log.info("[Connection-{}] Starting connection: {} → {}", ConnectionNumber, originFull, connection.destinationFull());

        WebDriver driver = WebDriverFactory.createDriver(WebDriverFactory.BrowserType.CHROME);
        int browsersNow = activeBrowsers.incrementAndGet();
        log.info("[Connection-{}] Browser opened for {}. Active browsers: {}", ConnectionNumber, connection.destinationFull(), browsersNow);

        List<Flight> validFlights = new ArrayList<>();

        try {
            String connectionQuery = connection.destinationQuery();
            String connectionFull = connection.destinationFull();

            log.info("[Connection-{}] Logging in for connection via {}", ConnectionNumber, connectionFull);
            UserCredentials credentials = credentialService.get(sessionID);
            LoginService loginService = new LoginService();
            loginService.login(driver, credentials.email(), credentials.password());

            log.info("[Connection-{}] Searching first-leg flights: {} → {}", ConnectionNumber, originFull, connectionFull);
            List<Flight> firstLegFlights = checkFlightAvailability(
                    new SearchFlightsPage(driver, originQuery, originFull, connectionQuery, connectionFull, date)
            );

            if (firstLegFlights.isEmpty()) {
                log.info("[Connection-{}] No first-leg flights for connection via {}", ConnectionNumber, connectionFull);
                return validFlights;
            }

            log.info("[Connection-{}] Searching second-leg flights: {} → {}", ConnectionNumber, connectionFull, destFull);
            LoginPage loginPage = new LoginPage(driver);
            loginPage.openHomePage();

            List<Flight> secondLegFlights = checkFlightAvailability(
                    new SearchFlightsPage(driver, connectionQuery, connectionFull, destQuery, destFull, date)
            );

            if (!secondLegFlights.isEmpty()) {
                validFlights.addAll(firstLegFlights);
                validFlights.addAll(secondLegFlights);
                log.info("[Connection-{}] Found {} flights via {}", ConnectionNumber, validFlights.size(), connectionFull);
            } else {
                log.info("[Connection-{}] No second-leg flights for connection via {}", ConnectionNumber, connectionFull);
            }

        } catch (Exception e) {
            log.error("[Connection-{}] Error in processConnection for {}: {}",
                    ConnectionNumber, connection.destinationFull(), e.getMessage(), e);
        } finally {
            driver.quit();
            int remaining = activeBrowsers.decrementAndGet();
            log.info("[Connection-{}] Browser closed for {}. Active browsers: {}",
                    ConnectionNumber, connection.destinationFull(), remaining);
        }

        return validFlights;
    }
    @Override
    @Timed(value = "flightFinder.searchNextDayFlights", description = "Time taken to search next 3 day flights")
    public List<Flight> searchNextThreeDaysFlights(String originQuery, String originFull,
                                             String destQuery, String destFull, String sessionID) {
        if (!FlightsFromPdfService.hasRoute(originFull, destFull)) {
            log.info("Route not found in parsed Wizz network: {} → {}", originFull, destFull);
            return List.of();
        }
        log.info("Route is available from : {} → {}", originFull, destFull);
        int MAX_THREADS = 3;
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        List<Flight> allFlights = new ArrayList<>();
        LocalDate baseDate = LocalDate.now();

        List<Callable<List<Flight>>> tasks = IntStream.range(0, 4)
                .mapToObj(i -> {
                    String date = baseDate.plusDays(i).toString();
                    return (Callable<List<Flight>>) () ->
                            searchFlightsForDate(date, originQuery, originFull, destQuery, destFull, sessionID);
                })
                .toList();
        try {
            log.info("Launching threaded search for next 3 days with {} threads", MAX_THREADS);
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
            log.info("Finished threaded search for next 3 days. Total flights found: {}", allFlights.size());
        }
        return allFlights;
    }
    private List<Flight> searchFlightsForDate(String date,
                                              String originQuery, String originFull,
                                              String destQuery, String destFull,
                                              String sessionID) {
        WebDriver driver = WebDriverFactory.createDriver(WebDriverFactory.BrowserType.CHROME);
        int browsersNow = activeBrowsers.incrementAndGet();
        log.info("[NextDay-{}] Browser opened. Active browsers: {}", date, browsersNow);

        List<Flight> flights = new ArrayList<>();

        try {
            log.info("[NextDay-{}] Searching flights for: {} → {} on {}", date, originFull, destFull, date);
            UserCredentials credentials = credentialService.get(sessionID);
            LoginService loginService = new LoginService();
            loginService.login(driver, credentials.email(), credentials.password());

            SearchFlightsPage searchPage = new SearchFlightsPage(driver, originQuery, originFull, destQuery, destFull, date);
            flights = checkFlightAvailability(searchPage);

            log.info("[NextDay-{}] Found {} flights", date, flights.size());
        } catch (Exception e) {
            log.error("[NextDay-{}] Error during search: {}", date, e.getMessage(), e);
        } finally {
            driver.quit();
            int remaining = activeBrowsers.decrementAndGet();
            log.info("[NextDay-{}] Browser closed. Remaining browsers: {}", date, remaining);
        }
        return flights;
    }
    private List<Flight> checkFlightAvailability(SearchFlightsPage searchPage){
        searchPage.fillRoute();
        searchPage.selectDate();
        searchPage.clickSearch();
        return searchPage.scrapeResults();
    }
    private List<Destination> getPossibleConnectionsFromUI(SearchFlightsPage searchPage, String originQuery, String originFull,
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
