package dev.alexey.devassist.knowledge.ingestion;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentChunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeDocumentChunkerTests {
    private List<KnowledgeDocumentChunk> chunks(String content, int max, int overlap) {
        return new KnowledgeDocumentChunker(new RagChunkingProperties(max, overlap))
                .chunk(content, KnowledgeSourceType.RUNBOOK);
    }

    @Test void smallDocumentIsOneExactChunk() {
        String content = "# Heading\n\nA paragraph.";
        List<KnowledgeDocumentChunk> result = chunks(content, 200, 20);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).isEqualTo(content);
        assertThat(result.getFirst().metadata()).containsEntry("sectionHeading", "# Heading")
                .containsEntry("characterStart", "0")
                .containsEntry("characterEnd", Integer.toString(content.length()));
    }

    @Test void largeDocumentPrefersHeadingBoundaryAndPreservesCoverage() {
        String content = "# First\n" + "a".repeat(150) + "\n# Second\n" + "b".repeat(150);
        List<KnowledgeDocumentChunk> result = chunks(content, 200, 0);
        assertThat(result).hasSizeGreaterThan(1);
        assertThat(result.get(1).content()).startsWith("# Second");
        assertThat(result.stream().map(KnowledgeDocumentChunk::content).toList()).allSatisfy(part ->
                assertThat(part).isNotEmpty().hasSizeLessThanOrEqualTo(200));
        assertThat(String.join("", result.stream().map(KnowledgeDocumentChunk::content).toList()))
                .isEqualTo(content);
    }

    @Test void overlapUsesExactSourceOffsetsAndIsDeterministic() {
        String content = "# Start\n" + "word ".repeat(100);
        List<KnowledgeDocumentChunk> result = chunks(content, 200, 30);
        assertThat(result).hasSizeGreaterThan(2);
        for (int index = 0; index < result.size(); index++) {
            KnowledgeDocumentChunk chunk = result.get(index);
            int start = Integer.parseInt(chunk.metadata().get("characterStart"));
            int end = Integer.parseInt(chunk.metadata().get("characterEnd"));
            assertThat(chunk.chunkIndex()).isEqualTo(index);
            assertThat(chunk.content()).isEqualTo(content.substring(start, end));
            if (index > 0) assertThat(start).isLessThan(Integer.parseInt(result.get(index - 1).metadata().get("characterEnd")));
        }
        assertThat(chunks(content, 200, 30)).isEqualTo(result);
    }

    @Test void longUnicodeLineHardSplitsWithoutBreakingSurrogatePairs() {
        String content = "😀".repeat(250);
        List<KnowledgeDocumentChunk> result = chunks(content, 201, 0);
        assertThat(result).hasSizeGreaterThan(1);
        assertThat(String.join("", result.stream().map(KnowledgeDocumentChunk::content).toList())).isEqualTo(content);
        assertThat(result.stream().map(KnowledgeDocumentChunk::content).toList()).allSatisfy(part ->
                assertThat(part).hasSizeLessThanOrEqualTo(201));
    }

    @Test void headingLikeCodeLineDoesNotBecomeSectionBoundary() {
        String content = "# Real\n```text\n# Not a heading\n```\n" + "text ".repeat(60);
        List<KnowledgeDocumentChunk> result = chunks(content, 200, 0);
        assertThat(result).isNotEmpty().allSatisfy(piece ->
                assertThat(piece.metadata()).containsEntry("sectionHeading", "# Real"));
    }

    @Test void nearMaximumOverlapStillAdvances() {
        String content = "x".repeat(250);
        List<KnowledgeDocumentChunk> result = chunks(content, 200, 199);
        assertThat(result).hasSize(51);
        assertThat(result.getLast().metadata()).containsEntry("characterEnd", "250");
    }

    @Test void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new RagChunkingProperties(199, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagChunkingProperties(200, 200)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagChunkingProperties(10001, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
