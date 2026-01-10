package com.aycf.flightFinder.features.searchFlights;

import com.aycf.flightFinder.controller.model.SearchRequest;
import com.aycf.flightFinder.features.searchFlights.model.Flight;

import java.util.List;

public interface ISearchFlightsService {

    List<Flight> searchDirectFlight(SearchRequest searchRequest);

    List<Flight> searchFlightsWithConnections(SearchRequest searchRequest);

    List<Flight> searchNextThreeDaysFlights(SearchRequest searchRequest);
}
