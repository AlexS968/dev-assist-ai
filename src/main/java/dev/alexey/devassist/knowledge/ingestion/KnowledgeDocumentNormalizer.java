package dev.alexey.devassist.knowledge.ingestion;

import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentNormalizer {
    public String normalize(String content) {
        String input = Normalizer.normalize(content.replace("\r\n", "\n").replace('\r', '\n'),
                Normalizer.Form.NFC);
        String[] lines = input.split("\n", -1);
        StringBuilder output = new StringBuilder();
        FenceState fence = new FenceState();
        int emptyRun = 0;
        for (String line : lines) {
            String clean = fence.active ? line : stripTrailingHorizontalWhitespace(line);
            fence.accept(clean.stripLeading());
            if (!fence.active && clean.isEmpty()) {
                emptyRun++;
                continue;
            }
            if (!output.isEmpty()) {
                output.append("\n".repeat(Math.min(emptyRun, 2) + 1));
            }
            output.append(clean);
            emptyRun = 0;
        }
        String normalized = output.toString();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Document content is blank after normalization");
        }
        return normalized;
    }

    private String stripTrailingHorizontalWhitespace(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) end--;
        return end == line.length() ? line : line.substring(0, end);
    }

    private static final class FenceState {
        private boolean active;
        private char marker;
        private int length;

        private void accept(String line) {
            int run = fenceRun(line);
            if (run < 3) return;
            if (active) {
                if (line.charAt(0) == marker && run >= length) active = false;
            } else {
                active = true;
                marker = line.charAt(0);
                length = run;
            }
        }

        private int fenceRun(String line) {
            if (line.isEmpty() || (line.charAt(0) != '`' && line.charAt(0) != '~')) return 0;
            int index = 0;
            while (index < line.length() && line.charAt(index) == line.charAt(0)) index++;
            return index;
        }
    }
}
