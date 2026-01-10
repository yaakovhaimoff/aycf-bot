package com.aycf.flightFinder.controller;

import com.aycf.flightFinder.controller.model.SearchRequest;
import com.aycf.flightFinder.features.searchFlights.ISearchFlightsService;
import com.aycf.flightFinder.features.searchFlights.model.Flight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class WebController {

    private final ISearchFlightsService flightSearchService;

    @GetMapping
    public String loginPage() {
        return "login";
    }

    @GetMapping("/search")
    public String searchPage(@AuthenticationPrincipal UserDetails user, Model model) {
        if (user != null) {
            model.addAttribute("userEmail", user.getUsername());
        }
        return "searchFlights";
    }

    @PostMapping("/search")
    public String searchFlights(
            @RequestBody SearchRequest searchRequest,
            @AuthenticationPrincipal UserDetails user,
            Model model) {

        String originFull = searchRequest.originFull();
        String destFull = searchRequest.destFull();
        String date = searchRequest.date();

        log.info("Searching flights: {} -> {} on {}", originFull, destFull, date);

        List<Flight> flights = flightSearchService.searchDirectFlight(searchRequest);

        handleFlights(flights, model,
                "No Direct Flights Found from " + originFull + " to " + destFull + " on " + date,
                originFull, destFull);

        if (flights.isEmpty()) {
            model.addAttribute("no_direct_flight", true);
        }

        model.addAttribute("origin_query", searchRequest.originQuery());
        model.addAttribute("dest_query", searchRequest.destQuery());
        model.addAttribute("date", date);
        addUserToModel(user, model);

        return "searchFlights";
    }

    @PostMapping("/search-next-days")
    public String searchNextThreeDays(
            @RequestBody SearchRequest searchRequest,
            @AuthenticationPrincipal UserDetails user,
            Model model) {

        String originFull = searchRequest.originFull();
        String destFull = searchRequest.destFull();

        List<Flight> flights = flightSearchService.searchNextThreeDaysFlights(searchRequest);

        handleFlights(flights, model,
                "No Flights Found in Next Three Days from " + originFull + " to " + destFull,
                originFull, destFull);

        addUserToModel(user, model);
        return "searchFlights";
    }

    @PostMapping("/search-connections")
    public String searchFlightsWithConnections(
            @RequestBody SearchRequest searchRequest,
            @AuthenticationPrincipal UserDetails user,
            Model model) {

        String originFull = searchRequest.originFull();
        String destFull = searchRequest.destFull();
        String date = searchRequest.date();

        log.info("Searching connections: {} -> {} on {}", originFull, destFull, date);

        List<Flight> flights = flightSearchService.searchFlightsWithConnections(searchRequest);

        handleFlights(flights, model,
                "No Connecting Flights Found from " + originFull + " to " + destFull + " on " + date,
                originFull, destFull);

        model.addAttribute("date", date);
        addUserToModel(user, model);
        return "searchFlights";
    }

    private void handleFlights(List<Flight> flights, Model model, String errorMessage,
                               String originFull, String destFull) {
        if (flights.isEmpty()) {
            model.addAttribute("no_flights", true);
            model.addAttribute("error_message", errorMessage);
            log.info(errorMessage);
        } else {
            model.addAttribute("flights", flights);
            log.info("Found {} flights", flights.size());
        }
        model.addAttribute("show_results", true);
        model.addAttribute("origin_full", originFull);
        model.addAttribute("dest_full", destFull);
    }

    private void addUserToModel(UserDetails user, Model model) {
        if (user != null) {
            model.addAttribute("userEmail", user.getUsername());
        }
    }
}
