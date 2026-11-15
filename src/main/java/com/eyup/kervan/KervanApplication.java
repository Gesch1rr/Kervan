package com.eyup.kervan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KervanApplication {

    public static void main(String[] args) {
        SpringApplication.run(KervanApplication.class, args);
    }

}
