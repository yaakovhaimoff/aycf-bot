package com.aycf.flightFinder.features.searchFlights.model;

public record SearchRequest(
        String originQuery,
        String originFull,
        String destQuery,
        String destFull,
        String date
) {}
