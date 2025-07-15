package com.aycf.flightFinder.model;

import lombok.Builder;

@Builder
public record Flight(String date, String departure, String arrival, String duration, String price) {
}
