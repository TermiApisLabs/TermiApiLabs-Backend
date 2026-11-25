package com.termiapislabs.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.termiapislabs")
@EntityScan(basePackages = "com.termiapislabs.model")
@EnableJpaRepositories(basePackages = "com.termiapislabs.repository")
public class TermiapislabsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TermiapislabsApiApplication.class, args);
    }
}
