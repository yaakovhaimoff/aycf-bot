package com.aycf.flightFinder.components.flightsFromPdf;

import com.aycf.flightFinder.model.Destination;

import java.util.List;

public interface IFlightsFromPdfService {
    void loadFlightsFromPdfAsync();
    boolean hasRoute(String origin, String destination);
    List<Destination> getPossibleConnections(String originFull, String destinationFull, List<Destination> possibleConnections);
}
