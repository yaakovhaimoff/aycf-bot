package com.aycf.flightFinder.mcp;

import com.aycf.flightFinder.features.flightsFromPdf.IFlightsFromPdfService;
import com.aycf.flightFinder.features.searchFlights.ISearchFlightsService;
import com.aycf.flightFinder.features.searchFlights.model.Flight;
import com.aycf.flightFinder.features.searchFlights.model.SearchRequest;
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
    private final AirportResolver airportResolver;

    @Tool(description = """
            Search for available Wizz Air AYCF direct flights between two cities on a specific date.
            Use common city names like 'Tel Aviv', 'Rome', 'London', 'Paris', 'Barcelona'.
            For cities with multiple airports (Rome, London, Milan, Paris, Stockholm, Brussels, Oslo, Venice),
            you can be specific: 'Rome Fiumicino', 'London Luton', 'Milan Bergamo', etc.
            Date format: YYYY-MM-DD.
            Returns available flights with departure/arrival times, duration, and price.
            If no flights found, try searchConnectionFlights or a different date.
            """)
    public String searchDirectFlight(String origin, String destination, String date) {
        String resolvedOrigin = airportResolver.resolve(origin);
        String resolvedDest = airportResolver.resolve(destination);
        log.info("[MCP] searchDirectFlight: {} -> {} on {}", resolvedOrigin, resolvedDest, date);
        SearchRequest request = buildRequest(resolvedOrigin, resolvedDest, date);
        List<Flight> flights = searchFlightsService.searchDirectFlight(request);
        return formatFlights(flights, resolvedOrigin, resolvedDest, date);
    }

    @Tool(description = """
            Search for available Wizz Air AYCF direct flights over the next 4 days from today, concurrently.
            Use this when the user asks about 'upcoming days', 'next few days', or has no specific date.
            Use common city names like 'Tel Aviv', 'Rome', 'London'.
            For cities with multiple airports, be specific: 'Rome Fiumicino', 'London Luton', etc.
            """)
    public String searchNextDaysFlights(String origin, String destination) {
        String resolvedOrigin = airportResolver.resolve(origin);
        String resolvedDest = airportResolver.resolve(destination);
        log.info("[MCP] searchNextDaysFlights: {} -> {}", resolvedOrigin, resolvedDest);
        SearchRequest request = buildRequest(resolvedOrigin, resolvedDest, LocalDate.now().toString());
        List<Flight> flights = searchFlightsService.searchNextThreeDaysFlights(request);
        return formatFlights(flights, resolvedOrigin, resolvedDest, "next 4 days");
    }

    @Tool(description = """
            Search for Wizz Air AYCF connecting flights (one-stop) between two cities on a specific date.
            Slower than direct search — checks multiple intermediate airports.
            Use when no direct flights are found or user asks about connections.
            Use common city names like 'Tel Aviv', 'Rome', 'London'. Date format: YYYY-MM-DD.
            """)
    public String searchConnectionFlights(String origin, String destination, String date) {
        String resolvedOrigin = airportResolver.resolve(origin);
        String resolvedDest = airportResolver.resolve(destination);
        log.info("[MCP] searchConnectionFlights: {} -> {} on {}", resolvedOrigin, resolvedDest, date);
        SearchRequest request = buildRequest(resolvedOrigin, resolvedDest, date);
        List<Flight> flights = searchFlightsService.searchFlightsWithConnections(request);
        return formatFlights(flights, resolvedOrigin, resolvedDest, date);
    }

    @Tool(description = """
            List all available Wizz Air AYCF routes from the official PDF.
            Returns a map of origin cities to their available destination cities.
            Use this to discover valid routes before searching.
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

    @Tool(description = """
            List all available Wizz Air AYCF destinations from a specific origin airport, according to the official PDF.
            Use this to discover what routes exist from a given city before deciding where to search.
            Use a common city name like 'Tel Aviv', 'Rome', 'London'.
            Returns instantly from cached PDF data — no flight search is performed.
            Always display the full list of destination names to the user, not just the count.
            """)
    public String listRoutesFromAirport(String origin) {
        String resolvedOrigin = airportResolver.resolve(origin);
        log.info("[MCP] listRoutesFromAirport: {}", resolvedOrigin);
        List<String> destinations = flightsFromPdfService.getDestinationsFromOrigin(resolvedOrigin);
        if (destinations.isEmpty()) {
            return String.format("No routes found from %s in the Wizz Air AYCF PDF. " +
                    "The city name may not match — try listAvailableRoutes to see all origins.", resolvedOrigin);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Available AYCF destinations from %s (%d route(s)):\n\n", resolvedOrigin, destinations.size()));
        destinations.stream().sorted().forEach(d -> sb.append("• ").append(d).append("\n"));
        return sb.toString();
    }

    private SearchRequest buildRequest(String originFull, String destFull, String date) {
        return new SearchRequest(
                airportResolver.extractQuery(originFull),
                originFull,
                airportResolver.extractQuery(destFull),
                destFull,
                date
        );
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
