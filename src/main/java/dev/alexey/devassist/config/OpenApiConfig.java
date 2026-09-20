package dev.alexey.devassist.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI().info(new Info()
				.title("Dev Assist AI API")
				.version("v1")
				.description("REST API for managing and analyzing software incidents"));
	}
}
