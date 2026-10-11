package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis;
import dev.alexey.devassist.analysis.validation.IncidentAnalysisValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class IncidentAnalysisCitationResolver {
    private final IncidentAnalysisValidator validator = new IncidentAnalysisValidator();

    public void validate(StructuredIncidentAnalysis analysis, IncidentAnalysisContext context) {
        validator.validateCitations(analysis, context);
    }

    public List<IncidentAnalysisCitation> resolve(StructuredIncidentAnalysis analysis,
            IncidentAnalysisContext context, java.util.UUID knowledgeBaseId) {
        validate(analysis, context);
        var byId = new HashMap<String, IncidentAnalysisSource>();
        context.sources().forEach(source -> byId.put(source.sourceId(), source));
        List<IncidentAnalysisCitation> citations = new ArrayList<>();
        for (String id : analysis.citationIds()) {
            var chunk = byId.get(id).chunk();
            citations.add(new IncidentAnalysisCitation(id, knowledgeBaseId, chunk.documentId(),
                    chunk.externalId(), chunk.documentTitle(), chunk.sourceType(), chunk.sourceUri(),
                    chunk.chunkId(), chunk.chunkIndex(), chunk.similarity()));
        }
        return List.copyOf(citations);
    }
}
