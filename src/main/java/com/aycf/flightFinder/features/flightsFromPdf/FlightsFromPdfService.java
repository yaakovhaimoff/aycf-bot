package com.aycf.flightFinder.features.flightsFromPdf;

import com.aycf.flightFinder.automation.webdriver.WebDriverSessionManager;
import com.aycf.flightFinder.features.searchFlights.model.Destination;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.openqa.selenium.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightsFromPdfService implements IFlightsFromPdfService {

    private final WebDriverSessionManager sessionManager;

    @Value("${app.admin.user}")
    private String adminUser;

    @Value("${app.admin.password}")
    private String adminPassword;
    private Map<String, List<String>> parsedRoutes = new HashMap<>();
    private final Set<String> allCities = Set.of(
            "Aalesund", "Aberdeen", "Abu Dhabi", "Agadir", "Alghero", "Alam", "Alicante", "Amman", "Ancona", "Antalya", "Athens",
            "Bacau", "Baku", "Banja Luka", "Barcelona", "Bari", "Basel/Mulhouse", "Belgrade", "Belfast", "Bergen", "Berlin", "Bilbao",
            "Billund", "Birmingham", "Bologna", "Bordeaux", "Bratislava", "Brindisi", "Brussels", "Bucharest", "Burgas", "Budapest",
            "Catania", "Castellon", "Chania", "Chisinau", "Cluj", "Cologne/Bonn", "Comiso", "Copenhagen", "Craiova",
            "Dalaman", "Debrecen", "Dortmund", "Dubrovnik", "Dubai",
            "Eindhoven",
            "Faro", "Frankfurt", "Friedrichshafen", "Fuerteventura",
            "Gdansk", "Genoa", "Girona", "Giza", "Glasgow", "Gothenburg", "Gran Canaria", "Gyumri",
            "Hamburg", "Haugesund", "Heraklion", "Hurghada",
            "Iasi", "Ibiza", "Istanbul",
            "Jeddah",
            "Karlsruhe/Baden-Baden", "Katowice", "Kerkyra", "Krakow", "Kutaisi", "Kosice", "Klaipeda/Palanga", "Kalamata",
            "Lamezia", "Larnaca", "Leeds/Bradford", "Leipzig/Halle", "Lisbon",
            "Liverpool", "Ljubljana", "London", "Lublin", "Lyon",
            "Maastricht", "Madeira", "Madinah", "Madrid", "Malaga", "Malmo", "Malta", "Marrakech", "Marsa", "Memmingen", "Milan", "Mykonos",
            "Naples", "Nice", "Nis", "Nuremberg",
            "Ohrid", "Olbia", "Oslo", "Oradea",
            "Palermo", "Palma De Mallorca", "Paris", "Paphos", "Perugia", "Pescara", "Pisa", "Podgorica", "Poprad/Tatry", "Porto", "Plovdiv","Poznan", "Prague", "Pristina",
            "Radom", "Reykjavik", "Rimini", "Rhodes", "Rome", "Rzeszow",
            "Salerno", "Santorini", "Sarajevo", "Sevilla", "Sharm el-Sheikh", "Sibiu", "Skopje", "Sofia", "Split", "Stavanger", "Suceava", "Santander",
            "Stockholm", "Stuttgart", "Szczecin",
            "Tallinn", "Targu-Mures", "Tel Aviv", "Tenerife", "Thessaloniki", "Timisoara", "Tirana", "Trieste", "Tromso", "Trondheim", "Turin", "Turku", "Tuzla",
            "Valencia", "Varna", "Venice", "Verona", "Vienna", "Vilnius",
            "Warsaw", "Wroclaw", "Yerevan",
            "Zakinthos Island", "Zaragoza");
    @PostConstruct
    public void init() {
        log.info("Loading routes PDF on startup");
        downloadPdf();
    }
    @Scheduled(cron = "0 0 7 * * *")
    public void scheduledPdfDownload() {
        log.info("Scheduled PDF download triggered at 7:00 AM");
        downloadPdf();
    }
    @Override
    @Async
    @Timed(value = "LoadFlightsPdfFile.time", description = "Time taken to load flights pdf file asynchronously")
    public void loadFlightsFromPdfAsync() {
        System.out.println("Async PDF download started");
        downloadPdf();
    }
    private void downloadPdf() {
        String pdfUrl = "https://multipass.wizzair.com/aycf-availability.pdf";
        String destinationPath = "wizzair-network.pdf";

        try {
            log.info("Downloading PDF with admin credentials: {}", adminUser);

            sessionManager.executeWithAuth(adminUser, adminPassword, driver -> {
                try {
                    Set<Cookie> cookies = driver.manage().getCookies();
                    String cookieHeader = buildCookieHeader(cookies);

                    log.info("Downloading PDF with authenticated session");
                    HttpsURLConnection connection = (HttpsURLConnection) new URL(pdfUrl).openConnection();
                    connection.setSSLSocketFactory(trustAllSslContext().getSocketFactory());
                    connection.setHostnameVerifier((h, s) -> true);
                    connection.setRequestProperty("Cookie", cookieHeader);
                    connection.setRequestMethod("GET");

                    try (InputStream in = connection.getInputStream()) {
                        Files.copy(in, Paths.get(destinationPath), StandardCopyOption.REPLACE_EXISTING);
                    }

                    parsedRoutes = parseRoutes(destinationPath, allCities);
                    log.info("Parsed routes size: {}", parsedRoutes.size());
                    log.info("Parsed routes: {}", parsedRoutes);

                } catch (IOException e) {
                    log.error("Failed to download/parse PDF: {}", e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            log.error("Failed to download PDF from URL: {}", pdfUrl, e);
        }
    }

    private javax.net.ssl.SSLContext trustAllSslContext() {
        try {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
            }}, new java.security.SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all SSL context", e);
        }
    }

    private String buildCookieHeader(Set<Cookie> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : cookies) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(cookie.getName()).append("=").append(cookie.getValue());
        }
        return sb.toString();
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
                    String cleanedFrom = replaceNotLettersWithSpaces(rawFrom);
                    String to = line.substring(rawFrom.length()).trim();
                    String cleanedTo = replaceNotLettersWithSpaces(to);
                    routesMap.computeIfAbsent(cleanedFrom, k -> new ArrayList<>()).add(cleanedTo);
                }
//                else {
//                    log.warn("No matching city found for line: " + line);
//                }
            }
        }
        return routesMap;
    }
    @Override
    public boolean hasRoute(String origin, String destination) {
        if (parsedRoutes.isEmpty()) {
            log.warn("Parsed routes empty on hasRoute call — downloading PDF now");
            downloadPdf();
        }
        String cleanedOrigin = cleanCityName(origin);
        String cleanedDestination = cleanCityName(destination);
        log.info("Checking route from '{}' to '{}'", cleanedOrigin, cleanedDestination);
        List<String> destinations = parsedRoutes.get(cleanedOrigin);
        return destinations != null && destinations.contains(cleanedDestination);
    }
    private String cleanCityName(String FullCityName) {
        log.info("Cleaning city name: '{}'", FullCityName);
        if (FullCityName == null || FullCityName.isBlank()) return "";
        String cityName = FullCityName.split("\\(")[0].trim();
        cityName = replaceNotLettersWithSpaces(cityName);
        for (String city : allCities) {
            if (cityName.equalsIgnoreCase(city) || cityName.toLowerCase().startsWith(city.toLowerCase())) {
                log.info("Matched city name: '{}' for {}", city, FullCityName);
                return city;
            }
        }
        log.info("City '{}' not found in known cities list.", cityName);
        return "";
    }
    private String replaceNotLettersWithSpaces(String city) {
        return city.replaceAll("[^\\p{L}]", " ").replaceAll("\\s+", " ").trim();
    }
    @Override
    public Map<String, List<String>> getAllRoutes() {
        // check if empty, if empty then fill it
        if (parsedRoutes.isEmpty()) {
            downloadPdf();
        }
        return Collections.unmodifiableMap(parsedRoutes);
    }

    @Override
    public List<String> getDestinationsFromOrigin(String originFull) {
        if (parsedRoutes.isEmpty()) {
            downloadPdf();
        }
        String cleanedOrigin = cleanCityName(originFull);
        return parsedRoutes.getOrDefault(cleanedOrigin, List.of());
    }

    @Override
    @Timed(value = "FlightsFromPdfService.getPossibleConnections.time", description = "Time taken to get possible connections from PDF")
    public List<Destination> getPossibleConnections(String originFull, String destinationFull, List<Destination> possibleConnections) {
        if (parsedRoutes.isEmpty()) {
            log.warn("Parsed routes empty on getPossibleConnections call — downloading PDF now");
            downloadPdf();
        }
        String origin = cleanCityName(originFull);
        String destination = cleanCityName(destinationFull);
        log.info("Finding connections from '{}' to '{}', with possible connections: {}", origin, destination, possibleConnections);

        List<String> validMidCities = parsedRoutes.getOrDefault(origin, List.of()).stream()
                .filter(mid -> parsedRoutes.getOrDefault(mid, List.of()).contains(destination))
                .toList();

        log.info("Valid mid cities: {}", validMidCities);

        List<Destination> connections = validMidCities.stream()
                .flatMap(mid -> possibleConnections.stream()
                        .filter(con -> cleanCityName(con.destinationFull()).equalsIgnoreCase(mid))
                        .map(con -> new Destination(con.destinationQuery(), con.destinationFull()))
                )
                .toList();
        log.info("Parsed PDF: Found {} possible connections from '{}' to '{}', connections are: {}", connections.size(), origin, destination, connections);
        return connections;
    }
}
