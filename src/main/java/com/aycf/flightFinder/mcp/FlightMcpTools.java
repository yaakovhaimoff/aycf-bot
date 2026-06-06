package com.aycf.flightFinder.mcp;

import com.aycf.flightFinder.controller.model.SearchRequest;
import com.aycf.flightFinder.features.flightsFromPdf.IFlightsFromPdfService;
import com.aycf.flightFinder.features.searchFlights.ISearchFlightsService;
import com.aycf.flightFinder.features.searchFlights.model.Flight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightMcpTools {

    private final ISearchFlightsService searchFlightsService;
    private final IFlightsFromPdfService flightsFromPdfService;

    @Tool(description = """
            Search for available Wizz Air AYCF direct flights between two cities on a specific date.
            City names should be common city names like 'Tel Aviv', 'Rome', 'London', 'Paris', 'Barcelona', etc.
            Returns available flights with departure time, arrival time, duration, and price.
            If no flights are found, try searchConnectionFlights or a different date.
            """)
    public String searchDirectFlight(String origin, String destination, String date) {
        log.info("[MCP] searchDirectFlight: {} -> {} on {}", origin, destination, date);
        SearchRequest request = buildRequest(origin, destination, date);
        List<Flight> flights = searchFlightsService.searchDirectFlight(request);
        return formatFlights(flights, origin, destination, date);
    }

    @Tool(description = """
            Search for available Wizz Air AYCF direct flights over the next 4 days starting from today.
            Searches all 4 days concurrently. Use this when the user asks about 'upcoming days', 'next few days',
            or doesn't have a specific date in mind.
            City names should be common city names like 'Tel Aviv', 'Rome', 'London', etc.
            """)
    public String searchNextDaysFlights(String origin, String destination) {
        log.info("[MCP] searchNextDaysFlights: {} -> {}", origin, destination);
        SearchRequest request = buildRequest(origin, destination, LocalDate.now().toString());
        List<Flight> flights = searchFlightsService.searchNextThreeDaysFlights(request);
        return formatFlights(flights, origin, destination, "next 4 days");
    }

    @Tool(description = """
            Search for Wizz Air AYCF connecting flights (one-stop via an intermediate city) on a specific date.
            Slower than direct search — checks multiple intermediate airports.
            Use this when no direct flights are found, or when the user specifically asks about connections.
            City names should be common city names like 'Tel Aviv', 'Rome', 'London', etc.
            """)
    public String searchConnectionFlights(String origin, String destination, String date) {
        log.info("[MCP] searchConnectionFlights: {} -> {} on {}", origin, destination, date);
        SearchRequest request = buildRequest(origin, destination, date);
        List<Flight> flights = searchFlightsService.searchFlightsWithConnections(request);
        return formatFlights(flights, origin, destination, date);
    }

    @Tool(description = """
            List all available Wizz Air AYCF routes loaded from the official PDF.
            Returns a map of origin cities to their available destination cities.
            Use this to discover what routes exist, or to check if a route is available before searching.
            """)
    public String listAvailableRoutes() {
        log.info("[MCP] listAvailableRoutes");
        Map<String, List<String>> routes = flightsFromPdfService.getAllRoutes();
        if (routes.isEmpty()) {
            return "Route data not yet loaded. The PDF is downloaded on startup — please wait a moment and try again.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Available Wizz Air AYCF routes (").append(routes.size()).append(" origins):\n\n");
        routes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey())
                        .append(" → ")
                        .append(String.join(", ", e.getValue()))
                        .append("\n"));
        return sb.toString();
    }

    private SearchRequest buildRequest(String origin, String destination, String date) {
        return new SearchRequest(
                extractQuery(origin),
                origin,
                extractQuery(destination),
                destination,
                date
        );
    }

    private String extractQuery(String cityFull) {
        return cityFull.split("[^a-zA-Z]")[0].toLowerCase();
    }

    private String formatFlights(List<Flight> flights, String origin, String destination, String date) {
        if (flights.isEmpty()) {
            return String.format("No available Wizz Air AYCF flights found from %s to %s on %s.", origin, destination, date);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d flight(s) from %s to %s on %s:\n\n", flights.size(), origin, destination, date));
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            sb.append(String.format("%d. %s | Depart: %s → Arrive: %s | Duration: %s | Price: %s\n",
                    i + 1, f.date(), f.departure(), f.arrival(), f.duration(), f.price()));
        }
        return sb.toString();
    }
}
