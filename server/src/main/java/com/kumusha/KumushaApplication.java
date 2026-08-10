package com.kumusha;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Main Spring Boot application class for the Kumusha API.
 *
 * <p>Kumusha ("home" in Shona) is a stay explorer built on the MongoDB {@code sample_airbnb}
 * dataset. It demonstrates CRUD operations, aggregation pipelines, geospatial queries,
 * MongoDB Search and MongoDB Vector Search using Spring Data MongoDB.
 *
 * @version 1.0
 */
@SpringBootApplication
@RestController
public class KumushaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KumushaApplication.class, args);
    }

    /**
     * Root endpoint providing basic information about the API.
     * Hidden from Swagger UI documentation.
     */
    @Hidden
    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "name", "kumusha",
                "version", "1.0.0",
                "description", "Java Spring Boot backend demonstrating MongoDB operations with the sample_airbnb dataset",
                "endpoints", Map.of("listings", "/api/listings")
        );
    }
}
