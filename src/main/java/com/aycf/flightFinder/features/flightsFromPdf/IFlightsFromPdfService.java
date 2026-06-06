package com.aycf.flightFinder.features.flightsFromPdf;

import com.aycf.flightFinder.features.searchFlights.model.Destination;

import java.util.List;
import java.util.Map;

public interface IFlightsFromPdfService {
    void loadFlightsFromPdfAsync();
    boolean hasRoute(String origin, String destination);
    List<Destination> getPossibleConnections(String originFull, String destinationFull, List<Destination> possibleConnections);
    Map<String, List<String>> getAllRoutes();
}
