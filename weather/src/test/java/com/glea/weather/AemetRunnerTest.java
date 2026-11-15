package com.glea.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link AemetRunner}.
 */
public class AemetRunnerTest {

    private final StationRepository repository = mock(StationRepository.class);

    /** Tests that the runner fails when AEMET returns an unauthorized response. */
    @Test
    void shouldFailWhenAemetReturnsUnauthorized() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // Act
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });

        // Assert
        assertEquals("AEMET inventory HTTP request failed. Status: 401", error.getMessage());
        server.verify();
    }

    @Test
    void shouldRejectRedirectFromSecondRequest() {
        // Arrange
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();

        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        String datosUrl = "https://opendata.aemet.es/opendata/datos/test-7211B";

        // Primera petición: respuesta correcta
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(
                        """
                                {
                                    "estado": 200,
                                    "datos": "%s"
                                }
                                """.formatted(datosUrl),
                        MediaType.APPLICATION_JSON));

        // Segunda petición: HTTP 302 con JSON válido
        server.expect(requestTo(datosUrl))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [{"indicativo":"7211B"}]
                                """));

        // Act + Assert
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                runner::run);

        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("302"));

        server.verify();
    }

    @Test
    void shouldRejectRedirectFromInventoryRequest() {
        // Arrange
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();

        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // petición: HTTP 302 con JSON válido
        server.expect(requestTo(expectedUrl))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                    "estado": 200,
                                    "datos": "https://opendata.aemet.es/opendata/datos/test-7211B"
                                }
                                """));

        // Act + Assert
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                runner::run);

        assertEquals("AEMET inventory HTTP request failed. Status: 302",
                error.getMessage());

        server.verify();
    }

    /** Tests that the runner fails when the datos URL is missing. */
    @Test
    void shouldFailWhenDatosUrlIsMissing() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess("{\"estado\":200,\"descripcion\":\"exito\"}", MediaType.APPLICATION_JSON));

        // Act
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        // Assert
        assertEquals("Missing datos URL.", error.getMessage());
        server.verify();
    }

    /** Tests that the runner fails when the datos URL is empty. */
    @Test
    void shouldFailWhenDatosUrlIsEmpty() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess("{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"\"}",
                        MediaType.APPLICATION_JSON));

        // Act
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });

        // Assert
        assertEquals("Missing datos URL.", error.getMessage());
        server.verify();
    }

    /** Tests that the runner fails when the datos URL is not a string. */
    @Test
    void shouldFailWhenDatosUrlIsNotAString() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess("{\"estado\":200,\"descripcion\":\"exito\",\"datos\":123}",
                        MediaType.APPLICATION_JSON));

        // Act
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });

        // Assert
        assertEquals("Missing datos URL.", error.getMessage());
        server.verify();
    }

    /** Tests that the runner accepts a valid datos URL. */
    @Test
    void shouldAcceptWhenDatosUrlIsValid() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"https://opendata.aemet.es/opendata\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://opendata.aemet.es/opendata"))
                .andRespond(
                        withSuccess(
                                "[{\"indicativo\":\"7211B\",\"nombre\":\"TEST STATION\"}]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        assertDoesNotThrow(() -> {
            runner.run();
        });
        server.verify();
    }

    /** Tests that the runner fails when the datos URL is malformed. */
    @Test
    void shouldFailWhenDatosUrlIsMalformed() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"hola\"}",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });

        assertEquals("Invalid datos URL.", error.getMessage());
        server.verify();
    }

    /**
     * Tests that the runner fails when the datos URL uses HTTP instead of HTTPS.
     */
    @Test
    void shouldFailWhenDatosUrlUsesHttp() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"http://opendata.aemet.es/opendata/\"}",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });

        assertEquals("Invalid datos URL.", error.getMessage());
        server.verify();
    }

    /** Tests that the runner fails when the datos URL has an allowed host. */
    @Test
    void shouldFailWhenDatosUrlHostIsNotAllowed() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"https://example.com/opendata/\"}",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });

        assertEquals("Invalid datos URL.", error.getMessage());
        server.verify();
    }

    /** Tests that the runner fails when the datos URL has an invalid port. */
    @Test
    void shouldFailWhenDatosUrlPortIsNotAllowed() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"https://opendata.aemet.es:8443/opendata/\"}",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });

        assertEquals("Invalid datos URL.", error.getMessage());
        server.verify();
    }

    /** Tests that the runner accepts a valid datos URL with port 443. */
    @Test
    void shouldAcceptWhenDatosUrlUsesPort443() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"https://opendata.aemet.es:443/opendata/\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://opendata.aemet.es:443/opendata/"))
                .andRespond(
                        withSuccess(
                                "[{\"indicativo\":\"7211B\",\"nombre\":\"TEST STATION\"}]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        assertDoesNotThrow(() -> {
            runner.run();
        });
        server.verify();
    }

    /** Tests that the runner fails when the datos URL contains user info. */
    @Test
    void shouldFailWhenDatosUrlContainsUserInfo() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"https://user:password@opendata.aemet.es/opendata/\"}",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        assertEquals("Invalid datos URL.", error.getMessage());
        server.verify();
    }

    /** Tests that the runner fails when the datos URL has invalid syntax. */
    @Test
    void shouldFailWhenDatosUrlHasInvalidSyntax() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"https://opendata.aemet.es/una ruta\"}",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        assertEquals("Invalid datos URL.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldDownloadStationData() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "[{\"indicativo\":\"7211B\",\"nombre\":\"TEST STATION\"}]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        assertDoesNotThrow(() -> {
            runner.run();
        });
        server.verify();
    }

    @Test
    void shouldFailWhenDownloadedDataIsNotJson() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "<html><body>Not JSON</body></html>",
                                MediaType.TEXT_HTML));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        assertEquals("Downloaded content is not valid JSON.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldFailWhenDownloadedDataIsNotAnArray() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "{}",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        assertEquals("Downloaded station data must be an array.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldFailWhenStationEntryIsNotAnObject() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "[123]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        assertEquals("Downloaded station entry must be an object.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldFailWhenLaterStationEntryIsNotAnObject() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "[{\"indicativo\":\"7211B\"}, 123]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        assertEquals("Downloaded station entry must be an object.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldFailWhenStationIdentifierIsMissing() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "[{}]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            runner.run();
        });
        assertEquals("Station identifier is missing.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldFailWhenStationIdentifierIsNull() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "[{\"indicativo\":null,\"nombre\":\"TEST STATION\"}]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run());

        assertEquals("Station identifier is missing.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldFailWhenStationIdentifierIsNotAString() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "[{\"indicativo\":123,\"nombre\":\"TEST STATION\"}]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run());

        assertEquals("Station identifier must be a string.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldFailWhenStationIdentifierIsEmpty() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        AemetRunner runner = new AemetRunner(clientBuilder, "test-api-key", repository);

        String expectedUrl = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
        String datosUrl = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

        // Arrange
        server.expect(requestTo(expectedUrl))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(
                        withSuccess(
                                "{\"estado\":200,\"descripcion\":\"exito\",\"datos\":\"" + datosUrl + "\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(requestTo(datosUrl))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(
                        withSuccess(
                                "[{\"indicativo\":\"\",\"nombre\":\"TEST STATION\"}]",
                                MediaType.APPLICATION_JSON));

        // Act & Assert
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run());

        assertEquals("Station identifier must not be blank.", error.getMessage());
        server.verify();
    }

    private static final String INVENTORY_URL = "https://opendata.aemet.es/opendata/api/valores/climatologicos/inventarioestaciones/estaciones/7211B";
    private static final String DATA_URL = "https://opendata.aemet.es/opendata/datos/estacion_7211B.json";

    @Test
    void shouldDistinguishApplicationFailureFromHttpFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":401,\"descripcion\":\"private response text\"}", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET application request failed.", error.getMessage());
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void shouldRejectEmptyFirstResponseBodyWithoutExposingResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET inventory response body is empty.", error.getMessage());
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void shouldRejectNullFirstResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess());

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET inventory response body is empty.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldRejectWhitespaceFirstResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(" \r\n ", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET inventory response body is empty.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldDistinguishMalformedInventoryJsonFromInvalidEnvelopeStructure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess("{broken", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET inventory response is not valid JSON.", error.getMessage());
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void shouldRejectInventoryEnvelopeWithoutApplicationStatusAsStructureError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET inventory response has invalid estado.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldRejectNonObjectInventoryEnvelope() {
        for (String body : new String[] { "[]", "true" }) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
            server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

            assertEquals("AEMET inventory response must be an object.", error.getMessage());
            server.verify();
        }
    }

    @Test
    void shouldRejectNullAndStringApplicationStatusesAsInvalidStructure() {
        for (String body : new String[] { "{\"estado\":null}", "{\"estado\":\"200\"}" }) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
            server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

            assertEquals("AEMET inventory response has invalid estado.", error.getMessage());
            server.verify();
        }
    }

    @Test
    void shouldSanitizeSecondStageTransportFailureWithoutCause() {
        AtomicInteger requestCount = new AtomicInteger();
        ClientHttpRequestFactory requestFactory = (uri, method) -> new ClientHttpRequest() {
            private final HttpHeaders headers = new HttpHeaders();
            private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

            @Override
            public HttpMethod getMethod() {
                return method;
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public Map<String, Object> getAttributes() {
                return new HashMap<>();
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }

            @Override
            public ByteArrayOutputStream getBody() {
                return requestBody;
            }

            @Override
            public ClientHttpResponse execute() throws IOException {
                if (requestCount.incrementAndGet() == 2) {
                    throw new IOException("private transport detail");
                }
                byte[] body = ("{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.APPLICATION_JSON);
                return new ClientHttpResponse() {
                    @Override
                    public HttpStatusCode getStatusCode() {
                        return HttpStatus.OK;
                    }

                    @Override
                    public String getStatusText() {
                        return "OK";
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                        return responseHeaders;
                    }

                    @Override
                    public ByteArrayInputStream getBody() {
                        return new ByteArrayInputStream(body);
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET data transport request failed.", error.getMessage());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private transport detail"));
        assertEquals(2, requestCount.get());
    }

    @Test
    void shouldRejectWhitespaceSecondResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL)).andRespond(withSuccess(" \r\n ", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("Downloaded station data response body is empty.", error.getMessage());
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void shouldRejectNullSecondResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL)).andRespond(withSuccess());

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("Downloaded station data response body is empty.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldRejectEmptySecondResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("Downloaded station data response body is empty.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldRejectEmptyStationInventory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("Downloaded station data must not be empty.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldRejectWhitespaceStationIdentifier() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL))
                .andRespond(withSuccess("[{\"indicativo\":\" \\t \"}]", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("Station identifier must not be blank.", error.getMessage());
        server.verify();
    }

    @Test
    void shouldMapHttpServerFailureAtInventoryStage() {
        assertHttpServerFailure(INVENTORY_URL, false);
    }

    @Test
    void shouldMapHttpServerFailureAtDataStageWithoutCredential() {
        assertHttpServerFailure(DATA_URL, true);
    }

    @Test
    void shouldMapHttpClientFailureAtDataStageWithoutCredential() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL)).andExpect(headerDoesNotExist("api_key"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET data HTTP request failed. Status: 403", error.getMessage());
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void shouldRejectMissingApiKeyWithoutMakingARequest() {
        AemetRunner runner = new AemetRunner(RestClient.builder(), null, repository);

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET API key is missing or invalid.", error.getMessage());
        assertNull(error.getCause());

        IllegalStateException blankError = assertThrows(IllegalStateException.class,
                () -> new AemetRunner(RestClient.builder(), " \t ", repository).run());
        assertEquals("AEMET API key is missing or invalid.", blankError.getMessage());
    }

    @Test
    void shouldSanitizeTransportFailureAndDiscardSensitiveCause() {
        RestClient.Builder builder = RestClient.builder().requestFactory((uri, method) -> {
            throw new java.io.IOException("test-api-key?secret=query-payload");
        });
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals("AEMET inventory transport request failed.", error.getMessage());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("test-api-key"));
        assertFalse(error.toString().contains("query-payload"));
    }

    @Test
    void shouldNotPrintCredentialPayloadOrLinkedUrl() {
        String linkedUrl = "https://opendata.aemet.es/opendata/datos/estacion.json?private=query-value";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        server.expect(requestTo(INVENTORY_URL)).andExpect(header("api_key", "test-api-key"))
                .andRespond(withSuccess(
                        "{\"estado\":200,\"datos\":\"" + linkedUrl + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(linkedUrl)).andExpect(headerDoesNotExist("api_key"))
                .andRespond(withSuccess("[{\"indicativo\":\"7211B\"}]", MediaType.APPLICATION_JSON));

        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(captured));
            runner.run();
        } finally {
            System.setOut(originalOut);
        }

        String output = captured.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(output.contains("test-api-key"));
        assertFalse(output.contains("query-value"));
        assertFalse(output.contains("7211B"));
        server.verify();
    }

    @Test
    void shouldRejectStationDifferentFromRequestedOne() {
        // Arrange: preparar el cliente y el servidor simulado
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);

        server.expect(requestTo(INVENTORY_URL))
                .andExpect(header("api_key", "test-api-key"))
                .andRespond(withSuccess(
                        "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo(DATA_URL))
                .andExpect(headerDoesNotExist("api_key"))
                .andRespond(withSuccess(
                        "[{\"indicativo\":\"9999X\"}]",
                        MediaType.APPLICATION_JSON));

        // Act & Assert: exigir el rechazo de la estación incorrecta
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                runner::run);

        assertEquals(
                "Station identifier does not match requested station.",
                error.getMessage());

        server.verify();
    }

    @ParameterizedTest
    @MethodSource("optionalStationNames")
    void savesMissingStationWithExactValuesAndReturnsGeneratedId(String nameField, String name) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = inventoryResponse(builder,
                "[{\"indicativo\":\"7211B\"" + nameField + "}]");
        when(repository.findId("AEMET", "7211B")).thenReturn(Optional.empty());
        when(repository.save("AEMET", "7211B", name)).thenReturn(73L);

        assertEquals(73L, new AemetRunner(builder, "test-api-key", repository).importStationInventory());

        var order = inOrder(repository);
        order.verify(repository).findId("AEMET", "7211B");
        order.verify(repository).save("AEMET", "7211B", name);
        verifyNoMoreInteractions(repository);
        server.verify();
    }

    static Stream<Arguments> optionalStationNames() {
        return Stream.of(
                Arguments.of("", null),
                Arguments.of(",\"nombre\":null", null),
                Arguments.of(",\"nombre\":\"  Station O'Brien  \"", "  Station O'Brien  "),
                Arguments.of(",\"nombre\":\"\"", ""));
    }

    @Test
    void reusesExistingIdWithoutSavingDifferentName() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = inventoryResponse(builder,
                "[{\"indicativo\":\"7211B\",\"nombre\":\"Different name\"}]");
        when(repository.findId("AEMET", "7211B")).thenReturn(Optional.of(42L));

        assertEquals(42L, new AemetRunner(builder, "test-api-key", repository).importStationInventory());

        verify(repository).findId("AEMET", "7211B");
        verifyNoMoreInteractions(repository);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{broken", "null", "{}", "[]", "[123]", "[{}]",
            "[{\"indicativo\":\"7211B\"},123]",
            "[{\"indicativo\":\"7211B\"},{\"indicativo\":\"9999X\"}]",
            "[{\"indicativo\":\"7211B\",\"nombre\":123}]",
            "[{\"indicativo\":\"7211B\",\"nombre\":false}]",
            "[{\"indicativo\":\"7211B\",\"nombre\":{}}]",
            "[{\"indicativo\":\"7211B\",\"nombre\":[]}]",
            "[{\"indicativo\":\"7211B\"},{\"indicativo\":\"7211B\",\"nombre\":123}]"
    })
    void rejectsWholeInvalidInventoryBeforeAnyPersistence(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = inventoryResponse(builder, body);

        assertThrows(IllegalStateException.class,
                () -> new AemetRunner(builder, "test-api-key", repository).run());

        verifyNoInteractions(repository);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void propagatesLookupAndSaveFailuresInsteadOfHttpFailureOrSuccess(boolean failSave) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = inventoryResponse(builder, "[{\"indicativo\":\"7211B\"}]");
        var failure = new DataAccessResourceFailureException("Simulated database failure");
        if (failSave) {
            when(repository.findId("AEMET", "7211B")).thenReturn(Optional.empty());
            when(repository.save("AEMET", "7211B", null)).thenThrow(failure);
        } else {
            when(repository.findId("AEMET", "7211B")).thenThrow(failure);
        }

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> new AemetRunner(builder, "test-api-key", repository).run()));

        verify(repository).findId("AEMET", "7211B");
        if (failSave) {
            verify(repository).save("AEMET", "7211B", null);
        }
        verifyNoMoreInteractions(repository);
        server.verify();
    }

    private MockRestServiceServer inventoryResponse(RestClient.Builder builder, String body) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return server;
    }

    private void assertHttpServerFailure(String failedUrl, boolean failDataRequest) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AemetRunner runner = new AemetRunner(builder, "test-api-key", repository);
        if (failDataRequest) {
            server.expect(requestTo(INVENTORY_URL)).andRespond(withSuccess(
                    "{\"estado\":200,\"datos\":\"" + DATA_URL + "\"}", MediaType.APPLICATION_JSON));
        }
        var request = server.expect(requestTo(failedUrl));
        if (failDataRequest) {
            request.andExpect(headerDoesNotExist("api_key"));
        }
        request.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        IllegalStateException error = assertThrows(IllegalStateException.class, runner::run);

        assertEquals(failDataRequest ? "AEMET data HTTP request failed. Status: 500"
                : "AEMET inventory HTTP request failed. Status: 500", error.getMessage());
        assertNull(error.getCause());
        server.verify();
    }
}
