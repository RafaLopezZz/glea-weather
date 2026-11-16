package com.glea.weather.station;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.glea.weather.aemet.AemetClientConfiguration;

class StationConfigurationTest {

    @Test
    void constructsRealRunnerAndJdbcWiringWithoutOpeningConnections() {
        new ApplicationContextRunner()
                .withUserConfiguration(StationConfiguration.class, AemetClientConfiguration.class, AemetRunner.class)
                .withPropertyValues("AEMET_API_KEY=test-only-placeholder",
                        "spring.datasource.url=jdbc:no-real-driver:offline",
                        "spring.datasource.username=test-only-user",
                        "spring.datasource.password=test-only-password")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AemetRunner.class)
                            .hasSingleBean(StationRepository.class).hasSingleBean(JdbcClient.class);
                    var dataSource = context.getBean(DriverManagerDataSource.class);
                    assertThat(dataSource.getUrl()).isEqualTo("jdbc:no-real-driver:offline");
                    assertThat(dataSource.getUsername()).isEqualTo("test-only-user");
                    assertThat(dataSource.getPassword()).isEqualTo("test-only-password");
                });
    }
}
