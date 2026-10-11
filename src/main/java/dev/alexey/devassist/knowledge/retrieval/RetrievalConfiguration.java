package dev.alexey.devassist.knowledge.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfiguration {}
