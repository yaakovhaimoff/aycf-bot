package com.aycf.flightFinder.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves human-friendly city names to the exact Wizz Air dropdown format.
 *
 * For cities served by multiple airports, the Wizz Air preferred airport is listed
 * first so it is chosen automatically when the user just says "Rome" or "London".
 *
 * e.g. "Tel Aviv" -> "Tel-Aviv (TLV)"
 *      "Rome"     -> "Rome Fiumicino (FCO)"   (FCO is Wizz Air's Rome hub)
 *      "London"   -> "London Luton (LTN)"     (LTN is Wizz Air's London hub)
 */
@Slf4j
@Component
public class AirportResolver {

    // For cities with multiple airports, Wizz Air's preferred airport is listed first.
    private static final List<String> AIRPORTS = List.of(
        "Aberdeen (ABZ)", "Abu Dhabi (AUH)", "Agadir (AGA)", "Alesund (AES)",
        "Alexandria (Borg El Arab) (HBE)", "Alghero (Sardinia) (AHO)", "Alicante (ALC)",
        "Almaty (ALA)", "Amman (AMM)", "Ancona (AOI)", "Antalya (AYT)", "Aqaba (AQJ)",
        "Astana (NQZ)", "Athens (ATH)", "Bacau (BCM)", "Baku (GYD)", "Banja Luka (BNX)",
        "Barcelona El Prat (BCN)", "Bari (BRI)",
        "Basel-Mulhouse-Freiburg (MLH)", "Basel-Mulhouse-Freiburg (BSL)",
        "Belgrad (BEG)", "Bergen (BGO)", "Berlin Brandenburg (BER)", "Bilbao (BIO)",
        "Billund (BLL)", "Birmingham (BHX)", "Bishkek (FRU)", "Bologna (BLQ)",
        "Bourgas (Black Sea) (BOJ)", "Brasov (GHV)", "Bratislava (BTS)", "Bremen (BRE)",
        "Brindisi (BDS)",
        "Brussels Charleroi (CRL)", "Brussels (BRU)",            // Wizz uses CRL
        "Bucharest (OTP)", "Bucharest Baneasa (BBU)",            // OTP is main
        "Budapest (BUD)", "Cairo (Sphinx) (SPX)", "Castellon (Valencia) (CDT)",
        "Catania (Sicily) (CTA)", "Chania (Crete) (CHQ)", "Chisinau (RMO)",
        "Cluj-Napoca (CLJ)", "Cologne (CGN)", "Comiso (CIY)", "Constanta (CND)",
        "Copenhagen (CPH)", "Corfu (CFU)", "Craiova (CRA)", "Dalaman (DLM)",
        "Dammam (DMM)", "Debrecen (DEB)", "Dortmund (DTM)", "Dubai (DXB)",
        "Dubrovnik (DBV)", "Eindhoven (EIN)", "Erbil (EBL)", "Faro (FAO)",
        "Frankfurt Hahn (HHN)", "Friedrichshafen (FDH)",
        "Fuerteventura (Canary Islands) (FUE)", "Funchal (Madeira) (FNC)",
        "Gabala (GBB)", "Gdansk (GDN)", "Geneva (GVA)", "Genoa (GOA)", "Girona (GRO)",
        "Glasgow (GLA)", "Gothenburg Landvetter (GOT)", "Gran Canaria (Canary Islands) (LPA)",
        "Grenoble (GNB)", "Hamburg (HAM)", "Haugesund (HAU)", "Heraklion (Crete) (HER)",
        "Hurghada (HRG)", "Iasi (IAS)", "Ibiza (IBZ)", "Istanbul (IST)", "Izmir (ADB)",
        "Jeddah (JED)", "Karlsruhe/Baden-Baden (FKB)", "Katowice (KTW)", "Kaunas (KUN)",
        "Kefalonia (EFL)", "Kos (KGS)", "Kosice (KSC)", "Kraków (KRK)", "Kutaisi (KUT)",
        "Larnaca (LCA)", "Leeds (LBA)", "Leipzig (LEJ)", "Lisbon (LIS)", "Liverpool (LPL)",
        "Ljubljana (LJU)",
        "London Luton (LTN)", "London Gatwick (LGW)",            // Wizz uses LTN
        "Lublin (LUZ)", "Lyon (LYS)", "Maastricht (MST)", "Madinah (MED)", "Madrid (MAD)",
        "Malaga (AGP)", "Malé (Maldives) (MLE)", "Malmo (MMX)", "Malta (MLA)",
        "Marrakesh (RAK)", "Marsa Alam (RMF)", "Memmingen/Munich West (FMM)",
        "Milan Bergamo (BGY)", "Milan Malpensa (MXP)",           // Wizz uses BGY
        "Muscat (MCT)", "Mykonos (JMK)", "Naples (NAP)", "Nice (NCE)", "Niš (INI)",
        "Nuremberg (NUE)", "Ohrid (OHD)", "Olbia (Sardinia) (OLB)", "Olsztyn-Mazury (SZY)",
        "Oslo Sandefjord Airport (TRF)", "Oslo Gardermoen (OSL)", // Wizz uses TRF
        "Palanga (PLQ)", "Palma de Mallorca (PMI)", "Paphos (PFO)",
        "Paris Beauvais (BVA)", "Paris Orly (ORY)",              // Wizz uses BVA
        "Perugia (PEG)", "Pescara (PSR)", "Pisa (Tuscany) (PSA)", "Plovdiv (PDV)",
        "Podgorica (TGD)", "Poprad-Tatry (TAT)", "Porto (OPO)", "Poznan (POZ)",
        "Prague (PRG)", "Prishtina (PRN)", "Radom (RDO)", "Rafic Hariri Intl (BEY)",
        "Reykjavik (KEF)", "Rhodes (RHO)", "Riga (RIX)", "Rimini (RMI)", "Riyadh (RUH)",
        "Rome Fiumicino (FCO)", "Rome Ciampino (CIA)",           // Wizz uses FCO
        "Rovaniemi (RVN)", "Rzeszów (RZE)", "Salalah (SLL)", "Salerno Costa d'Amalfi (QSR)",
        "Salzburg (SZG)", "Samarkand (SKD)", "Santander (SDR)", "Santorini (JTR)",
        "Sarajevo (SJJ)", "Satu Mare (SUJ)", "Seville (SVQ)", "Sharm El Sheikh (SSH)",
        "Sibiu (SBZ)", "Skiathos (JSI)", "Skopje (SKP)", "Sofia (SOF)", "Sohag (HMB)",
        "Split (SPU)", "Stavanger (SVG)",
        "Stockholm Skavsta (NYO)", "Stockholm Arlanda (ARN)",    // Wizz uses NYO
        "Stuttgart (STR)", "Suceava (SCV)", "Szczecin (SZZ)", "Tallinn (TLL)",
        "Tashkent (TAS)", "Tel-Aviv (TLV)", "Tenerife (Canary Islands) (TFS)",
        "Thessaloniki (SKG)", "Timisoara (TSR)", "Tirana (TIA)", "Tirgu Mures (TGM)",
        "Trieste (TRS)", "Tromso (TOS)", "Trondheim (TRD)", "Turin (TRN)",
        "Turkistan (HSA)", "Turku (TKU)", "Tuzla (TZL)", "Valencia (VLC)",
        "Varna (Black Sea) (VAR)",
        "Venice Treviso (TSF)", "Venice Marco Polo (VCE)",       // Wizz uses TSF
        "Verona (VRN)", "Vienna (VIE)", "Vilnius (VNO)", "Warsaw Chopin (WAW)",
        "Wroclaw (WRO)", "Yerevan (EVN)", "Zakynthos (ZTH)", "Zaragoza (ZAZ)"
    );

    /**
     * Resolves a city name to its Wizz Air dropdown string.
     * Always returns the first (preferred) match — never blocks on ambiguity.
     * If no match is found, returns the input unchanged as fallback.
     */
    public String resolve(String input) {
        String normalized = normalize(input);
        List<String> matches = AIRPORTS.stream()
                .filter(airport -> {
                    String cityPart = normalize(stripIata(airport));
                    return cityPart.contains(normalized) || normalized.contains(cityPart);
                })
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            log.warn("[MCP] No airport found for '{}' — using as-is", input);
            return input;
        }
        String resolved = matches.get(0);
        if (matches.size() > 1) {
            log.info("[MCP] '{}' matched {} airports, using preferred: {}", input, matches.size(), resolved);
        } else {
            log.info("[MCP] '{}' -> '{}'", input, resolved);
        }
        return resolved;
    }

    /** Extracts the autocomplete query string (first alphabetic word, lowercased). */
    public String extractQuery(String airportFull) {
        return airportFull.split("[^a-zA-Z]")[0].toLowerCase();
    }

    private String stripIata(String airport) {
        return airport.replaceAll("\\s*\\([A-Z]{3}\\)$", "");
    }

    private String normalize(String s) {
        return s.toLowerCase().replace("-", " ").replace("_", " ").trim();
    }
}
