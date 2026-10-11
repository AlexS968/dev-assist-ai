package dev.alexey.devassist.knowledge.ingestion;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Sha256ContentHasherTests {
    private final Sha256ContentHasher hasher = new Sha256ContentHasher();

    @Test void usesKnownUtf8Sha256Vector() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hasher.hash("é"))
                .isEqualTo("4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c");
    }

    @Test void isDeterministicLowercaseAndContentSensitive() {
        String hash = hasher.hash("First");
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(hasher.hash("First")).isEqualTo(hash);
        assertThat(hasher.hash("Second")).isNotEqualTo(hash);
    }
}
