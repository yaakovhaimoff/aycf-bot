package com.aycf.flightFinder.mcp;

import com.aycf.flightFinder.features.flightsFromPdf.IFlightsFromPdfService;
import com.aycf.flightFinder.features.searchFlights.ISearchFlightsService;
import com.aycf.flightFinder.features.searchFlights.model.Destination;
import com.aycf.flightFinder.features.searchFlights.model.Flight;
import com.aycf.flightFinder.features.searchFlights.model.SearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
        USE when the user names a specific date (e.g. "June 10", "tomorrow", "next Friday") AND a route.
        DO NOT use if no date is mentioned — use searchNextDaysFlights instead.
        DO NOT use to check what routes exist — use listRoutesFromAirport or listAvailableRoutes instead.
        Search Wizz Air AYCF direct flights between two cities on ONE specific date.
        Returns flights with times, duration, and price, or a clear 'no flights' message.
        If this returns nothing, call searchConnectionFlights for the same route/date.
        To scan several upcoming dates at once instead, use searchNextDaysFlights.
        """)
    public String searchDirectFlight(
            @ToolParam(description = "Departure city, common name e.g. 'Tel Aviv', 'Rome', 'London'. "
                    + "For multi-airport cities you may name the airport: 'Rome Fiumicino', 'London Luton', 'Milan Bergamo'.")
            String origin,
            @ToolParam(description = "Arrival city. Same format as origin.")
            String destination,
            @ToolParam(description = "Date as ISO YYYY-MM-DD, e.g. 2026-06-10. Must be today or later. "
                    + "Resolve relative dates like 'tomorrow' or 'next Friday' to an absolute date before calling.")
            String date) {
        String resolvedOrigin = airportResolver.resolve(origin);
        String resolvedDest = airportResolver.resolve(destination);
        log.info("[MCP] searchDirectFlight: {} -> {} on {}", resolvedOrigin, resolvedDest, date);
        SearchRequest request = buildRequest(resolvedOrigin, resolvedDest, date);
        List<Flight> flights = searchFlightsService.searchDirectFlight(request);
        return formatFlights(flights, resolvedOrigin, resolvedDest, date);
    }

    @Tool(description = """
            USE when the user gives a route but NO specific date — e.g. "any flights to Rome?", "flights this week", "next few days".
            DO NOT use if the user named a specific date — use searchDirectFlight instead.
            DO NOT use to check what routes exist — use listRoutesFromAirport or listAvailableRoutes instead.
            Search for available Wizz Air AYCF direct flights over the next 4 days from today, concurrently.
            Use common city names like 'Tel Aviv', 'Rome', 'London'.
            For cities with multiple airports, be specific: 'Rome Fiumicino', 'London Luton', etc.
            """)
    public String searchNextDaysFlights(
            @ToolParam(description = "Departure city, common name e.g. 'Tel Aviv', 'Rome', 'London'. "
                    + "For multi-airport cities you may name the airport: 'Rome Fiumicino', 'London Luton', 'Milan Bergamo'.")
            String origin,
            @ToolParam(description = "Arrival city. Same format as origin.")
            String destination) {
        String resolvedOrigin = airportResolver.resolve(origin);
        String resolvedDest = airportResolver.resolve(destination);
        log.info("[MCP] searchNextDaysFlights: {} -> {}", resolvedOrigin, resolvedDest);
        SearchRequest request = buildRequest(resolvedOrigin, resolvedDest, LocalDate.now().toString());
        List<Flight> flights = searchFlightsService.searchNextThreeDaysFlights(request);
        return formatFlights(flights, resolvedOrigin, resolvedDest, "next 4 days");
    }

    @Tool(description = """
            USE when the user asks what one-stop connection options exist between two cities — e.g. "how can I connect from Tel Aviv to London?", "what are the connection airports?".
            DO NOT use when the user wants actual flight times/prices — use searchDirectFlight instead.
            Returns the list of available intermediate airports (connection routes) between origin and destination,
            derived from Wizz Air AYCF autocomplete and PDF route data. No flight availability is checked.
            Use common city names like 'Tel Aviv', 'Rome', 'London'.
            """)
    public String searchConnectionFlights(
            @ToolParam(description = "Departure city, common name e.g. 'Tel Aviv', 'Rome', 'London'. "
                    + "For multi-airport cities you may name the airport: 'Rome Fiumicino', 'London Luton', 'Milan Bergamo'.")
            String origin,
            @ToolParam(description = "Arrival city. Same format as origin.")
            String destination,
            @ToolParam(description = "Date as ISO YYYY-MM-DD, e.g. 2026-06-10. Must be today or later. "
                    + "Resolve relative dates like 'tomorrow' or 'next Friday' to an absolute date before calling.")
            String date) {
        String resolvedOrigin = airportResolver.resolve(origin);
        String resolvedDest = airportResolver.resolve(destination);
        log.info("[MCP] searchConnectionFlights: {} -> {} on {}", resolvedOrigin, resolvedDest, date);
        SearchRequest request = buildRequest(resolvedOrigin, resolvedDest, date);
        List<Destination> routes = searchFlightsService.searchFlightsWithConnections(request);
        return formatConnectionRoutes(routes, resolvedOrigin, resolvedDest);
    }

    @Tool(description = """
            USE when the user asks "where can I fly?", "what routes are available?", "show me all destinations", or any question about ALL routes across ALL origins.
            DO NOT use when the user names a specific origin — use listRoutesFromAirport instead.
            DO NOT use when the user is searching for actual flights — use searchDirectFlight or searchNextDaysFlights instead.
            List all available Wizz Air AYCF routes from the official PDF.
            Returns a map of origin cities to their available destination cities.
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
            USE when the user names a specific origin and asks what destinations they can fly to — e.g. "where can I fly from Tel Aviv?", "what routes from Rome?".
            DO NOT use when the user is searching for actual flights — use searchDirectFlight or searchNextDaysFlights instead.
            DO NOT use when no origin is specified — use listAvailableRoutes instead.
            List all available Wizz Air AYCF destinations from a specific origin airport, according to the official PDF.
            Returns instantly from cached PDF data — no flight search is performed.
            Always display the full list of destination names to the user, not just the count.
            """)
    public String listRoutesFromAirport(
            @ToolParam(description = "Departure city, common name e.g. 'Tel Aviv', 'Rome', 'London'. "
                    + "For multi-airport cities you may name the airport: 'Rome Fiumicino', 'London Luton', 'Milan Bergamo'.")
            String origin) {
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

    private String formatConnectionRoutes(List<Destination> routes, String origin, String destination) {
        if (routes.isEmpty()) {
            return String.format("No connection routes found from %s to %s.", origin, destination);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d connection route(s) from %s to %s:\n\n", routes.size(), origin, destination));
        for (int i = 0; i < routes.size(); i++) {
            sb.append(String.format("%d. %s → %s → %s\n", i + 1, origin, routes.get(i).destinationFull(), destination));
        }
        return sb.toString();
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
