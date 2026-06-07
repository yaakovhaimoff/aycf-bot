package com.aycf.flightFinder.features.searchFlights.model;

import java.util.List;

public record ConnectionFlightResult(List<Flight> firstLeg, List<Flight> secondLeg) {}
