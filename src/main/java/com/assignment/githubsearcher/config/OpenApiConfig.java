package com.assignment.githubsearcher.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "GitHub Repository Searcher API",
        version = "1.0.0",
        description = "REST API to search GitHub repositories, persist them in PostgreSQL with upsert support, and retrieve filtered results."
    )
)
public class OpenApiConfig {
}
