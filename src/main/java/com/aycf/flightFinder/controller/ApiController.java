package com.aycf.flightFinder.controller;

import com.aycf.flightFinder.controller.model.SearchRequest;
import com.aycf.flightFinder.features.searchFlights.ISearchFlightsService;
import com.aycf.flightFinder.features.searchFlights.model.Flight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final ISearchFlightsService flightSearchService;
    @PostMapping("/search")
    public ResponseEntity<?> searchFlights(
            @RequestBody SearchRequest searchRequest,
            @AuthenticationPrincipal String email) {
        log.info("User {} searching direct flights: {} -> {} on {}",
                email, searchRequest.originFull(), searchRequest.destFull(), searchRequest.date());
        List<Flight> flights = flightSearchService.searchDirectFlight(searchRequest);

        return ResponseEntity.ok(Map.of(
                "flights", flights,
                "origin", searchRequest.originFull(),
                "destination", searchRequest.destFull(),
                "date", searchRequest.date(),
                "no_direct_flight", flights.isEmpty()
        ));
    }

    @PostMapping("/search-next-days")
    public ResponseEntity<?> searchNextThreeDays(
            @RequestBody SearchRequest searchRequest,
            @AuthenticationPrincipal String email) {
        log.info("User {} searching next days flights: {} -> {}",
                email, searchRequest.originFull(), searchRequest.destFull());
        List<Flight> flights = flightSearchService.searchNextThreeDaysFlights(searchRequest);

        return ResponseEntity.ok(Map.of(
                "flights", flights,
                "origin", searchRequest.originFull(),
                "destination", searchRequest.destFull()
        ));
    }

    @PostMapping("/search-connections")
    public ResponseEntity<?> searchConnections(
            @RequestBody SearchRequest searchRequest,
            @AuthenticationPrincipal String email) {
        log.info("User {} searching connections: {} -> {} on {}",
                email, searchRequest.originFull(), searchRequest.destFull(), searchRequest.date());
        List<Flight> flights = flightSearchService.searchFlightsWithConnections(searchRequest);

        return ResponseEntity.ok(Map.of(
                "flights", flights,
                "origin", searchRequest.originFull(),
                "destination", searchRequest.destFull(),
                "date", searchRequest.date(),
                "no_connections", flights.isEmpty()
        ));
    }
}
