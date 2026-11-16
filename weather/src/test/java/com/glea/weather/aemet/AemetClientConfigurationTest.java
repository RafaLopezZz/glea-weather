package com.glea.weather.aemet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AemetClientConfigurationTest {

    @Test
    void configuresFiniteConnectTimeoutAndDisablesRedirects() {
        var client = AemetClientConfiguration.httpClient();

        assertEquals(AemetClientConfiguration.CONNECT_TIMEOUT, client.connectTimeout().orElseThrow());
        assertEquals(java.net.http.HttpClient.Redirect.NEVER, client.followRedirects());
        assertNotNull(new AemetClientConfiguration().aemetRestClientBuilder());
    }
}
