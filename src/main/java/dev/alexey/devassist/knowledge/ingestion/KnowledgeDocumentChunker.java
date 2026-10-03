package dev.alexey.devassist.knowledge.ingestion;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentChunk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentChunker {
    private final RagChunkingProperties properties;

    public KnowledgeDocumentChunker(RagChunkingProperties properties) {
        this.properties = properties;
    }

    public List<KnowledgeDocumentChunk> chunk(String content, KnowledgeSourceType sourceType) {
        List<KnowledgeDocumentChunk> chunks = new ArrayList<>();
        List<Integer> headings = headingStarts(content);
        int start = 0;
        while (start < content.length()) {
            int end = chooseEnd(content, start, headings);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("chunkIndex", Integer.toString(chunks.size()));
            metadata.put("characterStart", Integer.toString(start));
            metadata.put("characterEnd", Integer.toString(end));
            metadata.put("sourceType", sourceType.name());
            String heading = headingAt(content, start, headings);
            if (heading != null) metadata.put("sectionHeading", heading);
            chunks.add(new KnowledgeDocumentChunk(chunks.size(), content.substring(start, end), metadata));
            if (end == content.length()) break;
            start = nextStart(content, start, end);
        }
        return List.copyOf(chunks);
    }

    private int chooseEnd(String content, int start, List<Integer> headings) {
        int limit = Math.min(content.length(), start + properties.maxChars());
        if (limit == content.length()) return limit;
        int preferredFrom = start + properties.maxChars() / 2;
        for (int priority = 0; priority < 4; priority++) {
            int boundary = bestBoundary(content, preferredFrom, limit, priority, headings);
            if (boundary > start) return boundary;
        }
        return safeBoundary(content, limit);
    }

    private int bestBoundary(String content, int from, int limit, int priority, List<Integer> headings) {
        for (int index = limit; index >= from; index--) {
            if (index < content.length() && Character.isLowSurrogate(content.charAt(index))) continue;
            if (isBoundary(content, index, priority, headings)) return index;
        }
        return -1;
    }

    private boolean isBoundary(String content, int index, int priority, List<Integer> headings) {
        if (index <= 0 || index >= content.length()) return false;
        return switch (priority) {
            case 0 -> Collections.binarySearch(headings, index) >= 0;
            case 1 -> index >= 2 && content.charAt(index - 1) == '\n' && content.charAt(index - 2) == '\n';
            case 2 -> content.charAt(index - 1) == '\n';
            default -> Character.isWhitespace(content.charAt(index - 1))
                    || (index >= 2 && ".!?".indexOf(content.charAt(index - 2)) >= 0
                    && Character.isWhitespace(content.charAt(index - 1)));
        };
    }

    private int safeBoundary(String content, int limit) {
        if (limit < content.length() && Character.isLowSurrogate(content.charAt(limit))) return limit - 1;
        return limit;
    }

    private int nextStart(String content, int previousStart, int end) {
        int start = Math.max(previousStart + 1, end - properties.overlapChars());
        if (start < content.length() && Character.isLowSurrogate(content.charAt(start))) start++;
        return start;
    }

    private String headingAt(String content, int offset, List<Integer> headings) {
        int position = Collections.binarySearch(headings, offset);
        if (position < 0) position = -position - 2;
        if (position < 0) return null;
        int lineStart = headings.get(position);
        int lineEnd = content.indexOf('\n', lineStart);
        if (lineEnd < 0) lineEnd = content.length();
        return content.substring(lineStart, lineEnd).strip();
    }

    private List<Integer> headingStarts(String content) {
        List<Integer> starts = new ArrayList<>();
        int lineStart = 0;
        boolean fenced = false;
        char marker = 0;
        int markerLength = 0;
        while (lineStart < content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = content.length();
            String line = content.substring(lineStart, lineEnd).stripLeading();
            int run = fenceRun(line);
            if (run >= 3 && (!fenced || line.charAt(0) == marker && run >= markerLength)) {
                if (fenced) fenced = false;
                else {
                    fenced = true;
                    marker = line.charAt(0);
                    markerLength = run;
                }
            } else if (!fenced && isHeading(line)) {
                starts.add(lineStart);
            }
            lineStart = lineEnd + 1;
        }
        return starts;
    }

    private int fenceRun(String line) {
        if (line.isEmpty() || (line.charAt(0) != '`' && line.charAt(0) != '~')) return 0;
        int index = 0;
        while (index < line.length() && line.charAt(index) == line.charAt(0)) index++;
        return index;
    }

    private boolean isHeading(String line) {
        if (line.isEmpty() || line.charAt(0) != '#') return false;
        int index = 0;
        while (index < line.length() && index < 6 && line.charAt(index) == '#') index++;
        return index < line.length() && line.charAt(index) == ' ';
    }
}
