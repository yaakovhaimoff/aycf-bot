package com.aycf.flightFinder.controller.model;

public record SearchRequest(
        String originQuery,
        String originFull,
        String destQuery,
        String destFull,
        String date
) {}
