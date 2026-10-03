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
        boolean fenced = false;
        char fenceMarker = 0;
        int fenceLength = 0;
        int emptyRun = 0;
        for (String line : lines) {
            String clean = fenced ? line : line.replaceFirst("[ \t]+$", "");
            String trimmed = clean.stripLeading();
            int run = fenceRun(trimmed);
            if (run >= 3 && (!fenced || trimmed.charAt(0) == fenceMarker && run >= fenceLength)) {
                if (fenced) {
                    fenced = false;
                } else {
                    fenced = true;
                    fenceMarker = trimmed.charAt(0);
                    fenceLength = run;
                }
            }
            if (!fenced && clean.isEmpty()) {
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

    private int fenceRun(String line) {
        if (line.isEmpty() || (line.charAt(0) != '`' && line.charAt(0) != '~')) return 0;
        int index = 0;
        while (index < line.length() && line.charAt(index) == line.charAt(0)) index++;
        return index;
    }
}
