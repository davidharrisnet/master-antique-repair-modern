package com.masterantique.service;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The services' clock: the server's local time, as the legacy app used {@code DateTime.Now} for every date it wrote
 * (the time zone of the migrated {@code TIMESTAMP(3)} values is unknown; see the known gaps). Tests pass a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
