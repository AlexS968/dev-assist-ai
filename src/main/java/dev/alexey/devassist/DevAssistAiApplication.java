package dev.alexey.devassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import dev.alexey.devassist.knowledge.ingestion.RagChunkingProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagChunkingProperties.class)
public class DevAssistAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevAssistAiApplication.class, args);
	}

}
