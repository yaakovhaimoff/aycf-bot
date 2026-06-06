package com.aycf.flightFinder.automation.pages;

import com.aycf.flightFinder.features.searchFlights.model.SearchRequest;
import com.aycf.flightFinder.features.searchFlights.model.Destination;
import com.aycf.flightFinder.features.searchFlights.model.Flight;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
public class SearchFlightsPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String originQuery;
    private final String originFull;
    private final String destQuery;
    private final String destFull;
    private final String date;
    public SearchFlightsPage(WebDriver driver, SearchRequest searchRequest) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        this.originQuery = searchRequest.originQuery();
        this.originFull = searchRequest.originFull();
        this.destQuery = searchRequest.destQuery();
        this.destFull = searchRequest.destFull();
        this.date = searchRequest.date();
    }

    public List<Destination> getAvailableDestinations(String inputIdPrefix, String destinationIdPrefix, String originQuery, String originFull) {
        log.info("Getting destinations available from: '{}'", originFull);

        selectLocationInput(inputIdPrefix, originQuery, originFull);

        String destinationInputSelector = String.format("input[id^='%s']", destinationIdPrefix);
        WebElement destInput = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(destinationInputSelector)));
        destInput.clear();
        destInput.click();

        List<Destination> destinations = new ArrayList<>();

        try {
            WebElement dropdownUl = waitForVisibleDropdownWithItems();
            List<WebElement> options = dropdownUl.findElements(By.tagName("li"));

            log.info("Available destinations:");
            for (WebElement option : options) {
                try {
                    String text = option.getText().strip();
                    log.info(" - {}", text);

                    String[] parts = text.split("\\(");
                    String prefix = parts[0].trim().split("\\s+")[0].toLowerCase();
                    String full = text.trim();
                    destinations.add(new Destination(prefix, full));
                } catch (StaleElementReferenceException ignored) {
                }
            }
            log.info("Found {} destinations.", destinations.size());
        } catch (TimeoutException e) {
            log.error("No destination dropdown options appeared.");
        }

        return destinations;
    }
    public void fillRoute() {
        waitForPageReady();
        selectLocationInput("autocomplete-origin", originQuery, originFull);
        selectLocationInput("autocomplete-destination", destQuery, destFull);
    }
    private void waitForPageReady() {
        log.info("Waiting for page to be ready...");
        wait.until(driver -> {
            var loaders = driver.findElements(By.cssSelector(".loading, .spinner, [class*='loading'], [class*='Loading']"));
            return loaders.stream().noneMatch(WebElement::isDisplayed);
        });
        log.info("Page is ready.");
    }
    private void selectLocationInput(String inputIdPrefix, String query, String exactText) {
        log.info("Starting selection for '{}'", exactText);

        String inputSelector = String.format("input[id^='%s']", inputIdPrefix);
        log.info("Waiting for input field: {}", inputSelector);
        WebElement inputField = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(inputSelector)));

        log.info("Clearing and typing query...");
        inputField.clear();
        inputField.sendKeys(query);

        log.info("Searching for matching dropdown item...");
        try {
            WebElement dropdownUl = waitForVisibleDropdownWithItems();
            var options = dropdownUl.findElements(By.tagName("li"));
            for (WebElement option : options) {
                try {
                    String optionText = option.getText().strip();
                    if (exactText.toLowerCase().contains(optionText.toLowerCase())
                            || optionText.toLowerCase().contains(exactText.toLowerCase())) {
                        log.info("Match found: {} — attempting click...", optionText);
                        option.click();
                        log.info("Selected location: {}", optionText);
                        return;
                    }
                } catch (StaleElementReferenceException e) {
                    continue;
                }
            }
        } catch (TimeoutException e) {
            log.error("No dropdown with visible items appeared for: {}", exactText);
            return;
        }

        log.error("Could not find dropdown option for: {}", exactText);
    }
    private WebElement waitForVisibleDropdownWithItems() throws TimeoutException {
        return wait.until(driver1 -> {
            var dropdowns = driver1.findElements(By.cssSelector("ul[role='listbox']"));
            for (WebElement dropdown : dropdowns) {
                if (dropdown.isDisplayed()) {
                    var items = dropdown.findElements(By.tagName("li"));
                    if (!items.isEmpty()) {
                        return dropdown;
                    }
                }
            }
            return null;
        });
    }
    public void selectDate() {
        WebElement dateSelector = wait.until(ExpectedConditions.elementToBeClickable(By.id("Departure-date")));
        dateSelector.click();
        selectCalendarDate();
    }
    private void selectCalendarDate() {
        log.info("Selecting date from calendar: {}", date);
        String xpath = String.format("//td[@title='%s' and contains(@class, 'cell') and not(contains(@class, 'disabled'))]", date);
        try {
            WebElement dateCell = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
            dateCell.click();
            log.info("Clicked date: {}", date);
        } catch (Exception e) {
            log.error("Could not select date {}: {}", date, e.getMessage());
        }
    }
    public void clickSearch() {
        try {
            WebElement searchButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.SearchCombo-submit")));
            searchButton.click();
            log.info("Search button clicked, waiting for results...");
        } catch (Exception e) {
            log.error("Error clicking search button: {}", e.getMessage());
        }
    }
    public List<Flight> scrapeResults() {
        log.info("Waiting for flight results to load...");
        try {
            wait.until(driver -> {
                List<WebElement> results = driver.findElements(By.className("CvoCollapsibleDirectFlightRow-content"));
                List<WebElement> noResults = driver.findElements(By.cssSelector("article.AvailabilityPage-noResultMessage"));
                return !results.isEmpty() || !noResults.isEmpty();
            });

            log.info("Page loaded, scraping results...");

            List<WebElement> noResults = driver.findElements(By.cssSelector("article.AvailabilityPage-noResultMessage"));
            if (!noResults.isEmpty()) {
                log.info("No flights found for the selected date.");
                return List.of();
            }

            List<WebElement> flights = driver.findElements(By.className("CvoCollapsibleDirectFlightRow-content"));
            if (flights.isEmpty()) {
                log.warn("No flight rows found but no 'no results' message either.");
                return List.of();
            }

            log.info("Found {} flight(s):", flights.size());
            return flights.stream().map(this::parseFlight).toList();
        } catch (Exception e) {
            log.error("Timeout or error waiting for results: {}", e.getMessage());
            return List.of();
        }
    }

    private Flight parseFlight(WebElement flight) {
        String date = flight.findElement(By.className("CvoCollapsibleDirectFlightRow-date")).getText();
        String depTime = flight.findElement(By.className("CvoCollapsibleDirectFlightRow-departure")).getText();
        String arrTime = flight.findElement(By.className("CvoCollapsibleDirectFlightRow-arrival")).getText();
        String duration = flight.findElement(By.className("CvoCollapsibleDirectFlightRow-duration")).getText();
        String price = flight.findElement(By.className("CvoCollapsibleDirectFlightRow-price")).getText();
        log.info("✈️  Flight: {} → {}, duration: {}, Price: {}", depTime, arrTime, duration, price);
        return Flight.builder()
                .date(date)
                .departure(depTime)
                .arrival(arrTime)
                .duration(duration)
                .price(price)
                .build();
    }
}
