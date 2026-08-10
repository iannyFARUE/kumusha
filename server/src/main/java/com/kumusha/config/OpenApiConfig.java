package com.kumusha.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the Kumusha API.
 *
 * <p>This configuration provides metadata for the API documentation
 * and ensures the "Try it out" functionality works correctly.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:3001}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kumusha API")
                        .version("1.0.0")
                        .description("Java Spring Boot backend demonstrating MongoDB operations with the sample_airbnb dataset. " +
                                "This API provides CRUD operations for stay listings, aggregation reporting, geospatial " +
                                "proximity search, MongoDB Search and MongoDB Vector Search.")
                        .contact(new Contact()
                                .name("Kumusha")
                                .url("https://www.mongodb.com/docs"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("http://localhost:3001")
                                .description("Default local server")
                ));
    }
}
