package dev.alexey.devassist.knowledge.ingestion;

import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeChunkMetadataTests {
    @Test void replacesOnlySourceTypeUsingDefensiveCopy() {
        Map<String, String> original = new HashMap<>(Map.of("sourceType", "RUNBOOK", "characterStart", "12"));
        KnowledgeChunk chunk = new KnowledgeChunk(null, 0, "content", "hash", original);
        chunk.updateSourceTypeMetadata(KnowledgeSourceType.ARCHITECTURE_NOTE);
        original.put("characterStart", "wrong");
        assertThat(chunk.getMetadata()).containsEntry("sourceType", "ARCHITECTURE_NOTE")
                .containsEntry("characterStart", "12");
        Map<String, String> exposed = chunk.getMetadata();
        assertThatThrownBy(() -> exposed.put("characterStart", "wrong"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsNullSourceType() {
        KnowledgeChunk chunk = new KnowledgeChunk(null, 0, "content", "hash", Map.of("sourceType", "RUNBOOK"));
        assertThatThrownBy(() -> chunk.updateSourceTypeMetadata(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
