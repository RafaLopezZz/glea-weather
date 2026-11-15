package com.glea.weather;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads, validates and resolves the AEMET station inventory in the JDBC catalogue.
 * The API key is sent only to the fixed inventory endpoint; the returned data
 * URL
 * must pass the HTTPS host allowlist before it is requested.
 */
@Component
public class AemetRunner implements CommandLineRunner {

    private static final String STATION_ID = "7211B";
    private static final String INVENTORY_URL = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/"
            + STATION_ID;

    private final RestClient restClient;
    private final String apiKey;
    private final StationRepository stationRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * Creates the runner using Spring's shared client builder.
     *
     * @param builder configured client builder
     * @param apiKey  configured credential, which may be absent or blank
     * @param stationRepository station catalogue
     */
    public AemetRunner(RestClient.Builder builder, @Value("${AEMET_API_KEY:}") String apiKey,
            StationRepository stationRepository) {
        this.restClient = builder.build();
        this.apiKey = apiKey;
        this.stationRepository = stationRepository;
    }

    /**
     * Fetches and fully validates the inventory before resolving its catalogue id.
     *
     * @param args unused command-line arguments
     * @throws IllegalStateException when configuration, transport, HTTP, JSON, or
     *                               data validation fails
     * @throws org.springframework.dao.DataAccessException when catalogue persistence fails
     */
    @Override
    public void run(String... args) {
        importStationInventory();
    }

    /** Returns the existing or generated catalogue id after validating the entire response. */
    long importStationInventory() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AEMET API key is missing or invalid.");
        }

        String envelopeBody = request(URI.create(INVENTORY_URL), true, "inventory");
        if (envelopeBody == null || envelopeBody.isBlank()) {
            throw new IllegalStateException("AEMET inventory response body is empty.");
        }

        JsonNode envelope = parseJson(envelopeBody, "AEMET inventory response is not valid JSON.");
        if (envelope == null || !envelope.isObject()) {
            throw new IllegalStateException("AEMET inventory response must be an object.");
        }
        JsonNode status = envelope.get("estado");
        if (status == null || !status.isInt()) {
            throw new IllegalStateException("AEMET inventory response has invalid estado.");
        }
        if (status.intValue() != 200) {
            throw new IllegalStateException("AEMET application request failed.");
        }
        JsonNode dataUrl = envelope.get("datos");
        if (dataUrl == null || !dataUrl.isString() || dataUrl.stringValue().isBlank()) {
            throw new IllegalStateException("Missing datos URL.");
        }

        URI uri;
        try {
            uri = URI.create(dataUrl.stringValue());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid datos URL.");
        }
        if (!"https".equals(uri.getScheme())
                || !"opendata.aemet.es".equals(uri.getHost())
                || (uri.getPort() != 443 && uri.getPort() != -1)
                || uri.getUserInfo() != null) {
            throw new IllegalStateException("Invalid datos URL.");
        }

        String dataBody = request(uri, false, "data");
        if (dataBody == null || dataBody.isBlank()) {
            throw new IllegalStateException("Downloaded station data response body is empty.");
        }
        JsonNode stations = parseJson(dataBody, "Downloaded content is not valid JSON.");
        if (stations == null || !stations.isArray()) {
            throw new IllegalStateException("Downloaded station data must be an array.");
        }
        if (stations.isEmpty()) {
            throw new IllegalStateException("Downloaded station data must not be empty.");
        }
        StationValidator validator = new StationValidator();
        for (JsonNode station : stations) {
            if (!station.isObject()) {
                throw new IllegalStateException("Downloaded station entry must be an object.");
            }
            JsonNode identifier = station.get("indicativo");
            if (identifier == null || identifier.isNull()) {
                throw new IllegalStateException("Station identifier is missing.");
            }
            if (!identifier.isString()) {
                throw new IllegalStateException("Station identifier must be a string.");
            }
            if (identifier.stringValue().isBlank()) {
                throw new IllegalStateException("Station identifier must not be blank.");
            }

            if (!STATION_ID.equals(identifier.stringValue())) {
                throw new IllegalStateException("Station identifier does not match requested station.");
            }
            validator.validate("AEMET", identifier.stringValue());
            JsonNode name = station.get("nombre");
            if (name != null && !name.isNull() && !name.isString()) {
                throw new IllegalStateException("Station name must be a string or null.");
            }
        }

        // Every entry refers to the requested station; resolve it once after full validation.
        JsonNode station = stations.get(0);
        String identifier = station.get("indicativo").stringValue();
        JsonNode name = station.get("nombre");
        String stationName = name == null || name.isNull() ? null : name.stringValue();
        return stationRepository.findId("AEMET", identifier)
                .orElseGet(() -> stationRepository.save("AEMET", identifier, stationName));
    }

    /**
     * Executes one request and maps HTTP or transport failures without retaining
     * their causes.
     *
     * @param uri           validated request URI
     * @param includeApiKey whether this is the fixed inventory request
     * @param stage         safe stage label for the exception message
     * @return response body, possibly {@code null}
     * @throws IllegalStateException when HTTP or transport execution fails
     */
    private String request(URI uri, boolean includeApiKey, String stage) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(uri);
            if (includeApiKey) {
                request.header("api_key", apiKey);
            }
            return request.retrieve()
                    .onStatus(status -> status.is3xxRedirection(),
                            (httpRequest, response) -> {
                                throw new IllegalStateException(
                                        "AEMET " + stage
                                                + " HTTP request failed. Status: "
                                                + response.getStatusCode().value());
                            })
                    .body(String.class);
        } catch (HttpStatusCodeException exception) {
            throw new IllegalStateException("AEMET " + stage + " HTTP request failed. Status: "
                    + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new IllegalStateException("AEMET " + stage + " transport request failed.");
        }
    }

    /**
     * Parses a response body while discarding parser details that may include
     * received content.
     *
     * @param body    response text
     * @param message sanitized stage-specific error message
     * @return parsed JSON tree, possibly {@code null} for a JSON {@code null} root
     * @throws IllegalStateException when the body is malformed JSON
     */
    private JsonNode parseJson(String body, String message) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException exception) {
            throw new IllegalStateException(message);
        }
    }
}
