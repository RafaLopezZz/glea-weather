package com.glea.weather.aemet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.glea.weather.acquisition.DateChunk;

class AemetOpenDataClientTest {

        private static final DateChunk JANUARY = new DateChunk(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1));
        private static final String API_URL = "https://opendata.aemet.es/opendata/api/valores/climatologicos/diarios/datos/"
                        + "fechaini/2025-01-01T00:00:00UTC/fechafin/2025-01-31T23:59:59UTC/estacion/7211B";
        private static final String DATA_URL = "https://opendata.aemet.es/opendata/sh/abc123";
        private static final Charset LATIN9 = Charset.forName("ISO-8859-15");
        private static final MediaType TEXT_LATIN9 = new MediaType("text", "plain", LATIN9);

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final AemetOpenDataClient client = new AemetOpenDataClient(builder, "test-api-key");

        private void expectEnvelope(String json) {
                server.expect(requestTo(API_URL))
                                .andExpect(header("api_key", "test-api-key"))
                                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        }

        private void expectOkEnvelope() {
                expectEnvelope("{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + DATA_URL + "\"}");
        }

        @Test
        void buildsTheDailyPathWithAemetInclusiveEnd() {
                assertEquals("valores/climatologicos/diarios/datos/fechaini/2025-01-01T00:00:00UTC"
                                + "/fechafin/2025-01-31T23:59:59UTC/estacion/7211B",
                                AemetOpenDataClient.dailyClimatologyPath("7211B", JANUARY));
        }

        @Test
        void rejectsStationIdentifiersThatCouldAlterThePath() {
                assertThrows(IllegalArgumentException.class,
                                () -> AemetOpenDataClient.dailyClimatologyPath("../x", JANUARY));
                assertThrows(IllegalArgumentException.class,
                                () -> AemetOpenDataClient.dailyClimatologyPath("", JANUARY));
                assertThrows(IllegalArgumentException.class,
                                () -> AemetOpenDataClient.dailyClimatologyPath(null, JANUARY));
        }

        @Test
        void returnsTheExactBytesAndContentTypeWithoutForwardingTheKey() {
                // "PEÑAS" with a non-ASCII byte in ISO-8859-15, decimal comma and a special
                // value: all must survive.
                byte[] original = """
                                [ {"fecha":"2025-01-01","indicativo":"7211B","nombre":"PEÑAS","tmin":"7,8","prec":"Acum"} ]
                                """
                                .getBytes(LATIN9);
                expectOkEnvelope();
                server.expect(requestTo(DATA_URL))
                                .andExpect(headerDoesNotExist("api_key"))
                                .andRespond(withSuccess(original, TEXT_LATIN9));

                AemetPayload payload = client.fetchDaily("7211B", JANUARY);

                assertArrayEquals(original, payload.body());
                assertEquals("text/plain;charset=ISO-8859-15", payload.contentType());
                server.verify();
        }

        @Test
        void failsWithoutApiKeyAndSendsNothing() {
                AemetOpenDataClient noKey = new AemetOpenDataClient(builder, " ");

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> noKey.fetchDaily("7211B", JANUARY));

                assertEquals("AEMET API key is missing or invalid.", error.getMessage());
                server.verify();
        }

        @Test
        void reportsApplicationErrorFromTheEnvelopeAndDoesNotDownload() {
                expectEnvelope("{\"estado\":404,\"descripcion\":\"No hay datos que satisfagan esos criterios\"}");

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> client.fetchDaily("7211B", JANUARY));

                assertEquals("AEMET daily request failed. Estado: 404", error.getMessage());
                server.verify();
        }

        @Test
        void reportsHttpStatusSuchAsRateLimit() {
                server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> client.fetchDaily("7211B", JANUARY));

                assertEquals("AEMET daily envelope HTTP request failed. Status: 429", error.getMessage());
                server.verify();
        }

        @Test
        void refusesDataUrlOutsideAemet() {
                expectEnvelope("{\"estado\":200,\"datos\":\"https://evil.example.com/x\"}");

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> client.fetchDaily("7211B", JANUARY));

                assertEquals("Invalid datos URL.", error.getMessage());
                server.verify();
        }

        @Test
        void refusesEmptyData() {
                expectOkEnvelope();
                server.expect(requestTo(DATA_URL)).andRespond(withSuccess(new byte[0], TEXT_LATIN9));

                assertThrows(IllegalStateException.class, () -> client.fetchDaily("7211B", JANUARY));
                server.verify();
        }

        @Test
        void refusesDataThatIsNotAJsonArray() {
                expectOkEnvelope();
                server.expect(requestTo(DATA_URL))
                                .andRespond(withSuccess(
                                                "{\"fecha\":\"2025-01-01\"}".getBytes(StandardCharsets.US_ASCII),
                                                TEXT_LATIN9));

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> client.fetchDaily("7211B", JANUARY));

                assertEquals("AEMET daily data must be a non-empty array.", error.getMessage());
        }

        @Test
        void refusesRecordsOfAnotherStation() {
                expectOkEnvelope();
                server.expect(requestTo(DATA_URL)).andRespond(withSuccess(
                                "[{\"fecha\":\"2025-01-01\",\"indicativo\":\"7209\"}]"
                                                .getBytes(StandardCharsets.US_ASCII),
                                TEXT_LATIN9));

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> client.fetchDaily("7211B", JANUARY));

                assertEquals("AEMET daily record does not match the requested station.", error.getMessage());
        }

        @Test
        void refusesRecordsOutsideTheRequestedWindow() {
                expectOkEnvelope();
                // 2025-02-01 is the exclusive end of [2025-01-01, 2025-02-01): it must be
                // rejected.
                server.expect(requestTo(DATA_URL)).andRespond(withSuccess(
                                "[{\"fecha\":\"2025-02-01\",\"indicativo\":\"7211B\"}]"
                                                .getBytes(StandardCharsets.US_ASCII),
                                TEXT_LATIN9));

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> client.fetchDaily("7211B", JANUARY));

                assertEquals("AEMET daily record is outside the requested window.", error.getMessage());
        }

        @Test
        void refusesRecordsWithInvalidDate() {
                expectOkEnvelope();
                server.expect(requestTo(DATA_URL)).andRespond(withSuccess(
                                "[{\"fecha\":\"01/01/2025\",\"indicativo\":\"7211B\"}]"
                                                .getBytes(StandardCharsets.US_ASCII),
                                TEXT_LATIN9));

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> client.fetchDaily("7211B", JANUARY));

                assertEquals("AEMET daily record has an invalid fecha.", error.getMessage());
        }

        @Test
        void payloadCannotBeChangedFromOutside() {
                byte[] bytes = { 1, 2, 3 };
                AemetPayload payload = new AemetPayload(bytes, null);

                bytes[0] = 9;
                payload.body()[1] = 9;

                assertArrayEquals(new byte[] { 1, 2, 3 }, payload.body());
        }
}
