package dev.alexey.devassist.analysis.grounding;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GroundingPropertiesTests {
    @Test
    void acceptsDefaultsAndBounds() {
        assertThat(new GroundingProperties(6000, 5).maxContextChars()).isEqualTo(6000);
        assertThat(new GroundingProperties(500, 1).maxChunks()).isEqualTo(1);
        assertThat(new GroundingProperties(50000, 20).maxChunks()).isEqualTo(20);
    }

    @Test
    void rejectsOutOfRangeLimits() {
        assertThatThrownBy(() -> new GroundingProperties(499, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroundingProperties(50001, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroundingProperties(6000, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroundingProperties(6000, 21)).isInstanceOf(IllegalArgumentException.class);
    }
}
