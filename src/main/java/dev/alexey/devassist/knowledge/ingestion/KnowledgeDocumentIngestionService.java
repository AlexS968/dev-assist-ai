package dev.alexey.devassist.knowledge.ingestion;

import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import dev.alexey.devassist.knowledge.model.IngestKnowledgeDocumentCommand;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentChunk;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentIngestionResult;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentIngestionStatus;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeDocumentIngestionService {
    private final KnowledgeBaseRepository bases;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeDocumentNormalizer normalizer;
    private final KnowledgeDocumentChunker chunker;
    private final Sha256ContentHasher hasher;

    public KnowledgeDocumentIngestionService(KnowledgeBaseRepository bases, KnowledgeDocumentRepository documents,
            KnowledgeChunkRepository chunks, KnowledgeDocumentNormalizer normalizer,
            KnowledgeDocumentChunker chunker, Sha256ContentHasher hasher) {
        this.bases = bases;
        this.documents = documents;
        this.chunks = chunks;
        this.normalizer = normalizer;
        this.chunker = chunker;
        this.hasher = hasher;
    }

    @Transactional
    public KnowledgeDocumentIngestionResult ingest(IngestKnowledgeDocumentCommand command) {
        validate(command);
        KnowledgeBase base = bases.findById(command.knowledgeBaseId())
                .orElseThrow(() -> new KnowledgeBaseNotFoundException(command.knowledgeBaseId()));
        String content = normalizer.normalize(command.content());
        String hash = hasher.hash(content);
        return documents.findByKnowledgeBase_IdAndExternalId(base.getId(), command.externalId())
                .map(existing -> update(existing, command, content, hash))
                .orElseGet(() -> create(base, command, content, hash));
    }

    private KnowledgeDocumentIngestionResult create(KnowledgeBase base, IngestKnowledgeDocumentCommand command,
            String content, String hash) {
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(base, command.externalId(),
                command.title(), command.sourceType(), command.sourceUri(), hash));
        long count = storeChunks(document, content, command);
        return result(document, KnowledgeDocumentIngestionStatus.CREATED, count);
    }

    private KnowledgeDocumentIngestionResult update(KnowledgeDocument document, IngestKnowledgeDocumentCommand command,
            String content, String hash) {
        boolean sameContent = document.getContentHash().equals(hash);
        boolean sameMetadata = document.getTitle().equals(command.title())
                && document.getSourceType() == command.sourceType()
                && Objects.equals(document.getSourceUri(), command.sourceUri());
        if (sameContent && sameMetadata) {
            return result(document, KnowledgeDocumentIngestionStatus.UNCHANGED, chunks.countByDocument_Id(document.getId()));
        }
        boolean sourceTypeChanged = document.getSourceType() != command.sourceType();
        document.update(command.title(), command.sourceType(), command.sourceUri(), hash);
        if (sameContent) {
            if (sourceTypeChanged) {
                for (KnowledgeChunk chunk : chunks.findByDocument_IdOrderByChunkIndexAsc(document.getId())) {
                    chunk.updateSourceTypeMetadata(command.sourceType());
                }
            }
            documents.flush();
            return result(document, KnowledgeDocumentIngestionStatus.UPDATED_METADATA,
                    chunks.countByDocument_Id(document.getId()));
        }
        chunks.deleteByDocumentId(document.getId());
        chunks.flush();
        long count = storeChunks(document, content, command);
        documents.flush();
        return result(document, KnowledgeDocumentIngestionStatus.UPDATED, count);
    }

    private long storeChunks(KnowledgeDocument document, String content, IngestKnowledgeDocumentCommand command) {
        List<KnowledgeDocumentChunk> pieces = chunker.chunk(content, command.sourceType());
        for (KnowledgeDocumentChunk piece : pieces) {
            chunks.save(new KnowledgeChunk(document, piece.chunkIndex(), piece.content(),
                    hasher.hash(piece.content()), piece.metadata()));
        }
        chunks.flush();
        return pieces.size();
    }

    private KnowledgeDocumentIngestionResult result(KnowledgeDocument document,
            KnowledgeDocumentIngestionStatus status, long count) {
        return new KnowledgeDocumentIngestionResult(document.getId(), document.getKnowledgeBase().getId(),
                document.getExternalId(), status, count, document.getContentHash());
    }

    private void validate(IngestKnowledgeDocumentCommand command) {
        if (command == null || command.knowledgeBaseId() == null || command.sourceType() == null) {
            throw new IllegalArgumentException("Missing required document fields");
        }
        requireText(command.externalId(), 255, "externalId");
        requireText(command.title(), 500, "title");
        if (command.sourceUri() != null) requireText(command.sourceUri(), 2000, "sourceUri");
        requireText(command.content(), 1_000_000, "content");
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }
}
