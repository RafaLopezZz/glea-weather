package com.glea.weather;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.verify;

@SpringBootTest(properties = { "spring.config.import=", "AEMET_API_KEY=test-only-placeholder" })
class WeatherApplicationTests {

    @Value("${AEMET_API_KEY:}")
    private String apiKey;

    @MockitoBean
    private AemetRunner runner;

    @jakarta.annotation.Resource
    private RestClient.Builder restClientBuilder;

	@Test
	void contextLoads() {
		org.junit.jupiter.api.Assertions.assertEquals("test-only-placeholder", apiKey);
		org.junit.jupiter.api.Assertions.assertNotNull(runner);
		org.junit.jupiter.api.Assertions.assertNotNull(restClientBuilder);
		verify(runner).run();
	}

}
