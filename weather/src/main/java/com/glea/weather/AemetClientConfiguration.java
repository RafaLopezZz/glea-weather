package com.glea.weather;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Configures finite, non-redirecting HTTP transport for AEMET requests. */
@Configuration(proxyBeanMethods = false)
public class AemetClientConfiguration {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Supplies the client builder used by the runner without relying on Boot's defaults.
     *
     * @return a RestClient builder with finite connect/read timeouts and redirects disabled
     */
    @Bean
    RestClient.Builder aemetRestClientBuilder() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    /**
     * Creates the JDK transport with a finite connection timeout and no redirects.
     *
     * @return a fresh HTTP client; it performs no request until used
     */
    static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
