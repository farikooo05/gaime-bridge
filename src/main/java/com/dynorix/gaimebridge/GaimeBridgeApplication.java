package com.dynorix.gaimebridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GaimeBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GaimeBridgeApplication.class, args);
    }
}
