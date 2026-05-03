package tn.esprit.fahamni.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tn.esprit.fahamni.utils.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class GoogleMapsLocationService {

    private static final URI AUTOCOMPLETE_URI = URI.create("https://places.googleapis.com/v1/places:autocomplete");
    private static final String GEOCODE_ENDPOINT = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String apiKey;
    private final String regionCode;
    private final String languageCode;

    public GoogleMapsLocationService() {
        this(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
            AppConfig.getGoogleMapsApiKey(),
            AppConfig.getGoogleMapsRegionCode(),
            AppConfig.getGoogleMapsLanguageCode()
        );
    }

    GoogleMapsLocationService(HttpClient httpClient, String apiKey, String regionCode, String languageCode) {
        this.httpClient = httpClient == null ? HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build() : httpClient;
        this.apiKey = normalize(apiKey);
        this.regionCode = firstNonBlank(normalize(regionCode), "tn");
        this.languageCode = firstNonBlank(normalize(languageCode), "fr");
    }

    public boolean isConfigured() {
        return apiKey != null;
    }

    public void validate() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "GOOGLE_MAPS_API_KEY absente. Configurez une cle Google Maps valide pour verifier les localisations."
            );
        }
    }

    public List<LocationSuggestion> autocomplete(String input) {
        return autocomplete(input, UUID.randomUUID().toString());
    }

    public List<LocationSuggestion> autocomplete(String input, String sessionToken) {
        validate();

        String normalizedInput = requireText(input, "Saisissez d'abord une adresse ou un nom de lieu.");
        String normalizedSessionToken = firstNonBlank(normalize(sessionToken), UUID.randomUUID().toString());

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("input", normalizedInput);
        payload.put("languageCode", languageCode);
        payload.put("regionCode", regionCode);
        payload.put("sessionToken", normalizedSessionToken);

        ArrayNode includedRegionCodes = payload.putArray("includedRegionCodes");
        includedRegionCodes.add(regionCode);

        HttpRequest request = HttpRequest.newBuilder(AUTOCOMPLETE_URI)
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
            .build();

        String body = send(request, "Google Places Autocomplete");
        JsonNode root = parseJson(body, "Google Places Autocomplete");

        Set<String> seenPlaceIds = new LinkedHashSet<>();
        List<LocationSuggestion> suggestions = new ArrayList<>();
        for (JsonNode suggestionNode : root.path("suggestions")) {
            JsonNode placePrediction = suggestionNode.path("placePrediction");
            String placeId = normalize(placePrediction.path("placeId").asText(null));
            String displayText = normalize(placePrediction.path("text").path("text").asText(null));
            if (placeId == null || displayText == null || !seenPlaceIds.add(placeId)) {
                continue;
            }
            suggestions.add(new LocationSuggestion(placeId, displayText));
        }

        return suggestions;
    }

    public ResolvedLocation geocodePlaceId(String placeId) {
        validate();

        String normalizedPlaceId = requireText(placeId, "Aucun lieu Google Maps n'a ete selectionne.");
        URI uri = URI.create(
            GEOCODE_ENDPOINT
                + "?place_id=" + encode(normalizedPlaceId)
                + "&language=" + encode(languageCode)
                + "&region=" + encode(regionCode)
                + "&key=" + encode(apiKey)
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();

        String body = send(request, "Google Geocoding");
        JsonNode root = parseJson(body, "Google Geocoding");
        ResolvedLocation resolvedLocation = parseGeocodeResult(root, "Google Geocoding");
        if (resolvedLocation == null) {
            throw new IllegalStateException("Google Geocoding n'a retourne aucun detail exploitable pour ce lieu.");
        }
        return resolvedLocation;
    }

    public ResolvedLocation geocodeAddress(String address) {
        validate();

        String normalizedAddress = requireText(address, "Saisissez d'abord une adresse ou un nom de lieu.");
        URI uri = URI.create(
            GEOCODE_ENDPOINT
                + "?address=" + encode(normalizedAddress)
                + "&language=" + encode(languageCode)
                + "&region=" + encode(regionCode)
                + "&components=" + encode("country:" + regionCode.toUpperCase(Locale.ROOT))
                + "&key=" + encode(apiKey)
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();

        String body = send(request, "Google Geocoding");
        JsonNode root = parseJson(body, "Google Geocoding");
        return parseGeocodeResult(root, "Google Geocoding");
    }

    private ResolvedLocation parseGeocodeResult(JsonNode root, String sourceLabel) {
        String status = normalize(root.path("status").asText(null));
        if (status == null) {
            throw new IllegalStateException(sourceLabel + " a retourne une reponse incomplete.");
        }
        if ("ZERO_RESULTS".equals(status)) {
            return null;
        }
        if (!"OK".equals(status)) {
            String errorMessage = normalize(root.path("error_message").asText(null));
            throw new IllegalStateException(
                errorMessage == null
                    ? sourceLabel + " a echoue avec le statut " + status + "."
                    : sourceLabel + " a echoue : " + errorMessage
            );
        }

        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }

        JsonNode firstResult = results.get(0);
        String formattedAddress = normalize(firstResult.path("formatted_address").asText(null));
        String placeId = normalize(firstResult.path("place_id").asText(null));
        JsonNode locationNode = firstResult.path("geometry").path("location");
        double latitude = locationNode.path("lat").asDouble(Double.NaN);
        double longitude = locationNode.path("lng").asDouble(Double.NaN);
        boolean partialMatch = firstResult.path("partial_match").asBoolean(false);

        List<String> resultTypes = new ArrayList<>();
        for (JsonNode typeNode : firstResult.path("types")) {
            String type = normalize(typeNode.asText(null));
            if (type != null) {
                resultTypes.add(type);
            }
        }

        if (formattedAddress == null || Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalStateException(sourceLabel + " a retourne une adresse incomplete.");
        }

        return new ResolvedLocation(placeId, formattedAddress, latitude, longitude, partialMatch, List.copyOf(resultTypes));
    }

    private String send(HttpRequest request, String sourceLabel) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new IllegalStateException(sourceLabel + " a retourne HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
        } catch (IOException exception) {
            throw new IllegalStateException(sourceLabel + " est inaccessible pour le moment.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(sourceLabel + " a ete interrompu.", exception);
        }
    }

    private JsonNode parseJson(String body, String sourceLabel) {
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException exception) {
            throw new IllegalStateException(sourceLabel + " a retourne une reponse JSON invalide.", exception);
        }
    }

    private String requireText(String value, String errorMessage) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "reponse vide";
        }
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }

    private static String firstNonBlank(String firstValue, String fallback) {
        return firstValue == null ? fallback : firstValue;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record LocationSuggestion(String placeId, String displayText) {
        @Override
        public String toString() {
            return displayText;
        }
    }

    public record ResolvedLocation(
        String placeId,
        String formattedAddress,
        double latitude,
        double longitude,
        boolean partialMatch,
        List<String> resultTypes
    ) {
        public String coordinatesLabel() {
            return String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude);
        }
    }
}
