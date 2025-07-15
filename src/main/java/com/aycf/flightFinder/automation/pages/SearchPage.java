package com.aycf.flightFinder.automation.pages;

import com.aycf.flightFinder.model.Flight;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
public class SearchPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    private String originQuery;
    private String originFull;
    private String destQuery;
    private String destFull;
    private String date;
    public SearchPage(WebDriver driver, String originQuery, String originFull, String destQuery, String destFull, String date) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        this.originQuery = originQuery;
        this.originFull = originFull;
        this.destQuery = destQuery;
        this.destFull = destFull;
        this.date = date;
    }
    public List<Flight> checkFlightAvailability(){
        fillRoute();
        selectDate();
        clickSearch();
        return scrapeResults();
    }
    public void fillRoute() {
        selectLocationInput("autocomplete-origin", originQuery, originFull);
        selectLocationInput( "autocomplete-destination", destQuery, destFull);
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
                    log.info("   {}", optionText);
                    if (exactText.toLowerCase().contains(optionText.toLowerCase())) {
                        log.info("Match found: {} — attempting click...", optionText);
                        option.click();
                        log.info("Selected location: {}", optionText);
                        return;
                    } else {
                        log.info("Not this option!");
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
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
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
    private void selectDate() {
        log.info("Selecting date: {}", date);
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
    private void clickSearch() {
        try {
            WebElement searchButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.SearchCombo-submit")));
            searchButton.click();
            log.info("Search button clicked, waiting for results...");
        } catch (Exception e) {
            log.error("Error clicking search button: {}", e.getMessage());
        }
    }
    private List<Flight> scrapeResults() {
        log.info("Waiting for flight results to load...");
        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted: {}", e.getMessage());
        }
        log.info("Scraping results...");

        try {
            List<WebElement> noResults = driver.findElements(By.cssSelector("article.AvailabilityPage-noResultMessage"));
            if (!noResults.isEmpty()) {
                log.warn("No flights found for the selected date.");
                return List.of();
            }

            List<WebElement> flights = driver.findElements(By.className("CvoCollapsibleDirectFlightRow-content"));
            if (flights.isEmpty()) {
                log.info("No flight rows found but no 'no results' message either.");
                return List.of();
            }

            log.info("Found {} flight(s):", flights.size());
            return flights.stream().map(this::parseFlight).toList();
        } catch (Exception e) {
            log.error("Unexpected error while scraping results: {}", e.getMessage());
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
