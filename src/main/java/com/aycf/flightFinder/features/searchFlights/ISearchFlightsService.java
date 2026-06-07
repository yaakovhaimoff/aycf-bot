package com.aycf.flightFinder.features.searchFlights;

import com.aycf.flightFinder.features.searchFlights.model.SearchRequest;
import com.aycf.flightFinder.features.searchFlights.model.Destination;
import com.aycf.flightFinder.features.searchFlights.model.Flight;

import java.util.List;

public interface ISearchFlightsService {

    List<Flight> searchDirectFlight(SearchRequest searchRequest);

    List<Destination> searchFlightsWithConnections(SearchRequest searchRequest);

    List<Flight> searchNextThreeDaysFlights(SearchRequest searchRequest);
}
