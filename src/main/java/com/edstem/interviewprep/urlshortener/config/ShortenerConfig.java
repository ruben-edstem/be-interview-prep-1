package com.edstem.interviewprep.urlshortener.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ShortenerProperties.class)
public class ShortenerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
