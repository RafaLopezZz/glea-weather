package com.glea.weather;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two-step AEMET OpenData client for daily climatologies.
 * Step 1 asks the API (with the key) for a temporary "datos" URL; step 2
 * downloads that URL
 * (without the key) and returns its exact bytes. The bytes are validated
 * structurally but never
 * modified: numbers stay as text with decimal comma and special values such as
 * "Acum" are kept.
 */
@Component
public class AemetOpenDataClient {

    static final String API_BASE = "https://opendata.aemet.es/opendata/api/";
    private static final Pattern STATION_ID = Pattern.compile("[A-Za-z0-9]{1,20}");
    /** Charset observed on 2026-11-15 when the response does not declare one. */
    private static final Charset DEFAULT_CHARSET = Charset.forName("ISO-8859-15");

    private final RestClient restClient;
    private final String apiKey;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AemetOpenDataClient(RestClient.Builder builder, @Value("${AEMET_API_KEY:}") String apiKey) {
        this.restClient = builder.build();
        this.apiKey = apiKey;
    }

    /**
     * API path for one station and one [start, end) window, using AEMET's inclusive
     * end.
     */
    static String dailyClimatologyPath(String stationId, DateChunk chunk) {
        if (stationId == null || !STATION_ID.matcher(stationId).matches()) {
            throw new IllegalArgumentException("Invalid station identifier.");
        }
        return "valores/climatologicos/diarios/datos/fechaini/" + chunk.aemetStart()
                + "/fechafin/" + chunk.aemetEnd() + "/estacion/" + stationId;
    }

    /**
     * Downloads the daily records of one station for one window.
     *
     * @return the exact response bytes and their Content-Type
     * @throws IllegalStateException on configuration, HTTP, transport, JSON or
     *                               content mismatch
     */
    public AemetPayload fetchDaily(String stationId, DateChunk chunk) {
        String path = dailyClimatologyPath(stationId, chunk);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AEMET API key is missing or invalid.");
        }

        URI dataUri = requestDataUri(URI.create(API_BASE + path));
        ResponseEntity<byte[]> response = request(dataUri, false, "daily data");
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("AEMET daily data response body is empty.");
        }
        MediaType mediaType = response.getHeaders().getContentType();
        String contentType = mediaType == null ? null : mediaType.toString();
        Charset charset = mediaType != null && mediaType.getCharset() != null
                ? mediaType.getCharset()
                : DEFAULT_CHARSET;

        validateDailyRecords(new String(body, charset), stationId, chunk);
        return new AemetPayload(body, contentType);
    }

    /**
     * Step 1: asks for the temporary data URL and checks it points to AEMET over
     * HTTPS.
     */
    private URI requestDataUri(URI apiUri) {
        ResponseEntity<byte[]> response = request(apiUri, true, "daily envelope");
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("AEMET daily envelope response body is empty.");
        }
        JsonNode envelope = parse(new String(body, DEFAULT_CHARSET), "AEMET daily envelope is not valid JSON.");
        if (envelope == null || !envelope.isObject()) {
            throw new IllegalStateException("AEMET daily envelope must be an object.");
        }
        JsonNode status = envelope.get("estado");
        if (status == null || !status.isInt()) {
            throw new IllegalStateException("AEMET daily envelope has invalid estado.");
        }
        if (status.intValue() != 200) {
            throw new IllegalStateException("AEMET daily request failed. Estado: " + status.intValue());
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
        return uri;
    }

    /**
     * Every element must be an object of the requested station with a date inside
     * the window.
     */
    private void validateDailyRecords(String text, String stationId, DateChunk chunk) {
        JsonNode records = parse(text, "AEMET daily data is not valid JSON.");
        if (records == null || !records.isArray() || records.isEmpty()) {
            throw new IllegalStateException("AEMET daily data must be a non-empty array.");
        }
        for (JsonNode record : records) {
            if (!record.isObject()) {
                throw new IllegalStateException("AEMET daily record must be an object.");
            }
            JsonNode identifier = record.get("indicativo");
            if (identifier == null || !identifier.isString() || !stationId.equals(identifier.stringValue())) {
                throw new IllegalStateException("AEMET daily record does not match the requested station.");
            }
            JsonNode date = record.get("fecha");
            if (date == null || !date.isString()) {
                throw new IllegalStateException("AEMET daily record has no fecha.");
            }
            LocalDate day;
            try {
                day = LocalDate.parse(date.stringValue());
            } catch (DateTimeParseException exception) {
                throw new IllegalStateException("AEMET daily record has an invalid fecha.");
            }
            if (day.isBefore(chunk.start()) || !day.isBefore(chunk.end())) {
                throw new IllegalStateException("AEMET daily record is outside the requested window.");
            }
        }
    }

    /**
     * One GET; the API key travels only on the envelope request. Causes are dropped
     * on purpose.
     */
    private ResponseEntity<byte[]> request(URI uri, boolean includeApiKey, String stage) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(uri);
            if (includeApiKey) {
                request.header("api_key", apiKey);
            }
            return request.retrieve()
                    .onStatus(status -> status.is3xxRedirection(), (httpRequest, response) -> {
                        throw new IllegalStateException("AEMET " + stage + " HTTP request failed. Status: "
                                + response.getStatusCode().value());
                    })
                    .toEntity(byte[].class);
        } catch (HttpStatusCodeException exception) {
            throw new IllegalStateException("AEMET " + stage + " HTTP request failed. Status: "
                    + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new IllegalStateException("AEMET " + stage + " transport request failed.");
        }
    }

    private JsonNode parse(String body, String message) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException exception) {
            throw new IllegalStateException(message);
        }
    }
}
