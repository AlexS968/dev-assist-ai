package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalService;
import dev.alexey.devassist.knowledge.retrieval.RetrievalProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class GroundingConfiguration {
    @Bean
    IncidentAnalysisGroundingService incidentAnalysisGroundingService(Environment environment,
            ObjectProvider<KnowledgeRetrievalService> retrieval,
            ObjectProvider<GroundingProperties> grounding,
            ObjectProvider<RetrievalProperties> retrievalProperties) {
        boolean enabled = environment.getProperty("app.rag.enabled", Boolean.class, false);
        if (!enabled) return new IncidentAnalysisGroundingService(null, null, false);
        GroundingProperties limits = grounding.getObject();
        if (limits.maxChunks() > retrievalProperties.getObject().maxTopK()) {
            throw new IllegalArgumentException("RAG grounding maxChunks exceeds retrieval maxTopK");
        }
        return new IncidentAnalysisGroundingService(retrieval.getObject(), limits, true);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
    @EnableConfigurationProperties(GroundingProperties.class)
    static class EnabledProperties {}
}
