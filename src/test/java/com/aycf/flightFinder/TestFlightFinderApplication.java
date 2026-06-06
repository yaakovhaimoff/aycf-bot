package com.aycf.flightFinder;

import org.springframework.boot.SpringApplication;

public class TestFlightFinderApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlightFinderApplication::main).run(args);
	}
}
