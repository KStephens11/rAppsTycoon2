package com.rapptycoon.integration;

import com.intuit.karate.junit5.Karate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Karate integration test runner.
 * Boots the full Spring application on a random port and runs all feature files.
 * Triggered by maven-failsafe-plugin during the verify phase (./mvnw verify).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KarateRunner {

    @LocalServerPort
    private int port;

    @Karate.Test
    Karate testAll() {
        return Karate.run("classpath:karate")
                .systemProperty("server.port", String.valueOf(port))
                .relativeTo(getClass());
    }
}
