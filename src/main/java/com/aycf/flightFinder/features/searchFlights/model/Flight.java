package com.aycf.flightFinder.features.searchFlights.model;

import lombok.Builder;

@Builder
public record Flight(String date, String departure, String arrival, String duration, String price) {
}
