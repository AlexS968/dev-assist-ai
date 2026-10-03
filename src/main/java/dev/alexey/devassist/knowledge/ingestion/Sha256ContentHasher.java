package dev.alexey.devassist.knowledge.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class Sha256ContentHasher {
    public String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
