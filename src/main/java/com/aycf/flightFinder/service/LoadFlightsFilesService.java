package com.aycf.flightFinder.service;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class LoadFlightsFilesService {
    private Map<String, List<String>> parsedRoutes;
    private final Set<String> allCities = Set.of(
            "Aalesund", "Aberdeen", "Alghero", "Alam", "Alicante", "Amman", "Antalya", "Athens", "Bacau", "Banja Luka",
            "Barcelona", "Bari", "Basel/Mulhouse", "Belgrade", "Belfast", "Bergen", "Berlin", "Bilbao", "Billund", "Birmingham",
            "Bologna", "Bratislava", "Brussels", "Bucharest", "Burgas", "Budapest", "Catania", "Castellon", "Chania", "Chisinau",
            "Cluj", "Comiso", "Copenhagen", "Craiova", "Dalaman", "Debrecen", "Dortmund", "Dubrovnik", "Eindhoven", "Faro", "Frankfurt",
            "Friedrichshafen", "Fuerteventura", "Gdansk", "Genoa", "Girona", "Glasgow", "Gothenburg", "Gran Canaria",
            "Hamburg", "Haugesund", "Heraklion", "Hurghada", "Iasi", "Ibiza", "Istanbul", "Jeddah", "Karlsruhe/Baden-Baden",
            "Katowice", "Kerkyra", "Krakow", "Kutaisi", "Larnaca", "Leeds/Bradford", "Leipzig/Halle", "Lisbon",
            "Liverpool", "Ljubljana", "London", "Lublin", "Lyon", "Madeira", "Madinah", "Madrid", "Malaga", "Malmo", "Malta", "Marsa",
            "Memmingen", "Milan", "Mykonos", "Naples", "Nice", "Nuremberg", "Ohrid", "Olbia", "Oslo", "Palma De Mallorca",
            "Paris", "Perugia", "Pescara", "Pisa", "Podgorica", "Porto", "Poznan", "Prague", "Pristina", "Reykjavik", "Rimini",
            "Rhodes", "Rome", "Rzeszow", "Salerno", "Santorini", "Sarajevo", "Sevilla", "Sharm el-Sheikh", "Sibiu", "Skopje",
            "Sofia", "Split", "Stavanger", "Stockholm", "Stuttgart", "Targu-Mures", "Tel Aviv", "Tenerife", "Thessaloniki",
            "Timisoara", "Tirana", "Trieste", "Tromso", "Trondheim", "Turin", "Turku", "Tuzla", "Valencia", "Varna", "Venice", "Verona",
            "Vienna", "Vilnius", "Warsaw", "Wroclaw", "Yerevan", "Zakinthos Island", "Zaragoza");
    @Async
    @Timed(value = "LoadFlightsFilesService.time", description = "Time taken to load flights files asynchronously")
    public void loadUserFilesAsync() {
        downloadPdf();
    }
    public Map<String, List<String>> getParsedRoutes() {
        return parsedRoutes;
    }
    private void downloadPdf() {
        String pdfUrl = "https://multipass.wizzair.com/aycf-availability.pdf";
        try (InputStream in = new URL(pdfUrl).openStream()) {
            String destinationPath = "wizzair-network.pdf";
            Files.copy(in, Paths.get(destinationPath), StandardCopyOption.REPLACE_EXISTING);
            parsedRoutes = parseRoutes(destinationPath, allCities);
            log.info("Parsed routes size: " + parsedRoutes.size() + "");
//            parsedRoutes.forEach((from, toList) -> {
//                log.info("Flight from " + from + " → " + toList);
//            });
        } catch (IOException e) {
            log.error("Failed to download PDF from URL: " + pdfUrl, e);
        }
    }
    private Map<String, List<String>> parseRoutes(String pdfPath, Set<String> knownCities) throws IOException {
        Map<String, List<String>> routesMap = new HashMap<>();
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            String[] lines = text.split("\\R");

            for (String line : lines) {
                line = line.trim();

                String[] words = line.split("\\s+");
                if (words.length == 0) continue;

                String firstWord = words[0];
                Optional<String> match = knownCities.stream()
                        .filter(city -> city.startsWith(firstWord))
                        .findFirst();
                if (match.isPresent()) {
                    String rawFrom = match.get();
                    String cleanedFrom = cleanCityName(rawFrom);
                    String to = line.substring(rawFrom.length()).trim();
                    String cleanedTo = cleanCityName(to);
                    routesMap.computeIfAbsent(cleanedFrom, k -> new ArrayList<>()).add(cleanedTo);
                }
//                else {
//                    log.warn("No matching city found for line: " + line);
//                }
            }
        }
        return routesMap;
    }
    private String cleanCityName(String city) {
        return city.replaceAll("[^\\p{L}]", " ").replaceAll("\\s+", " ").trim();
    }
}
