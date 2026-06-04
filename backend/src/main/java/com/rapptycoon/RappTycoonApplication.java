package com.rapptycoon;

import com.rapptycoon.config.GameProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameProperties.class)
public class RappTycoonApplication {

    public static void main(String[] args) {
        SpringApplication.run(RappTycoonApplication.class, args);
    }
}
