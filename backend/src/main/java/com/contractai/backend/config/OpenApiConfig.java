package com.contractai.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description shown at the top of the Swagger UI page. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("AI Contract Intelligence API")
                .version("0.1")
                .description("Contract management API. Callers currently identify themselves with the "
                        + "temporary X-User-Id header (replaced by JWT in a later phase)."));
    }
}
