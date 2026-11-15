package com.glea.weather;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** JDBC wiring for manual catalogue imports; bean creation opens no connections. */
@Configuration(proxyBeanMethods = false)
public class StationConfiguration {

    @Bean
    DataSource dataSource(@Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return new DriverManagerDataSource(url, username, password);
    }

    @Bean
    JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    StationRepository stationRepository(JdbcClient jdbcClient) {
        return new StationRepository(jdbcClient);
    }
}
