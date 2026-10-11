package dev.alexey.devassist.knowledge.ingestion;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeDocumentNormalizerTests {
    private final KnowledgeDocumentNormalizer normalizer = new KnowledgeDocumentNormalizer();

    @Test void normalizesLineEndingsWhitespaceAndEmptyRuns() {
        String input = "\n\n# Title  \r\n\r\n\r\n\r\n- item\t\rbody  \n\n";
        assertThat(normalizer.normalize(input)).isEqualTo("# Title\n\n\n- item\nbody");
    }

    @Test void appliesNfcWithoutChangingMarkdownOrCodeContents() {
        String input = "# Cafe\u0301\n- List\n```java\n  code  \n\n```\nTail";
        String output = normalizer.normalize(input);
        assertThat(output).contains("# Café", "- List", "  code  \n\n```");
        assertThat(normalizer.normalize(output)).isEqualTo(output);
    }

    @Test void rejectsBlankNormalizedDocument() {
        assertThatThrownBy(() -> normalizer.normalize(" \n\t\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void longLineTrimsOnlyTrailingSpacesAndTabsOutsideFence() {
        String longLine = "a".repeat(100_000);
        String input = longLine + " \t\t\n```text  \n" + longLine + " \t\n```\nTail \t";
        assertThat(normalizer.normalize(input)).isEqualTo(
                longLine + "\n```text\n" + longLine + " \t\n```\nTail");
    }
}
