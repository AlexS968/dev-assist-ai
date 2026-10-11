package dev.alexey.devassist.knowledge.ingestion;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.model.IngestKnowledgeDocumentCommand;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeDocumentIngestionValidationTests {
    private final KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
    private final KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
    private final KnowledgeDocumentIngestionService service = new KnowledgeDocumentIngestionService(
            bases, documents, chunks, new KnowledgeDocumentNormalizer(),
            new KnowledgeDocumentChunker(new RagChunkingProperties(1500, 200)), new Sha256ContentHasher());

    @Test void rejectsInvalidFieldsBeforeDatabaseAccess() {
        UUID baseId = UUID.randomUUID();
        IngestKnowledgeDocumentCommand invalid = new IngestKnowledgeDocumentCommand(baseId, " ", "Title",
                KnowledgeSourceType.RUNBOOK, null, "Content");
        assertThatThrownBy(() -> service.ingest(invalid)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(bases, documents, chunks);
    }

    @Test void rejectsOversizedContentWithoutEchoingIt() {
        String content = "x".repeat(1_000_001);
        IngestKnowledgeDocumentCommand invalid = new IngestKnowledgeDocumentCommand(UUID.randomUUID(), "id", "Title",
                KnowledgeSourceType.RUNBOOK, null, content);
        assertThatThrownBy(() -> service.ingest(invalid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid content");
        verifyNoInteractions(bases, documents, chunks);
    }
}
