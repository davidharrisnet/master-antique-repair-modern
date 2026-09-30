package com.masterantique.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The legacy text rules plus the 4,000-byte Oracle limit. */
class TextRulesTest {

    @Test
    void trimsAndKeepsLineBreaksAndTabs() {
        assertThat(TextRules.validText("  one\r\ntwo\tthree  ", "Comment")).isEqualTo("one\r\ntwo\tthree");
    }

    @Test
    void blankOrNullIsRequired() {
        assertThatThrownBy(() -> TextRules.validText("   \n ", "Description"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Description is required.");
        assertThatThrownBy(() -> TextRules.validText(null, "Comment"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Comment is required.");
    }

    @Test
    void atMost2000CharactersAfterTrimming() {
        assertThat(TextRules.validText(" " + "a".repeat(2000) + " ", "Comment")).hasSize(2000);
        assertThatThrownBy(() -> TextRules.validText("a".repeat(2001), "Comment"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2,000 characters");
    }

    @Test
    void controlCharactersOtherThanCrLfTabAreRejected() {
        assertThatThrownBy(() -> TextRules.validText("bell\u0007", "Comment"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("control character");
        assertThatThrownBy(() -> TextRules.validText("a\u0000b", "Comment"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextRules.validText("a\u009Fb", "Comment"))   // C1 control
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiByteTextIsLimitedTo4000Bytes() {
        String twoThousandThreeByteChars = "€".repeat(2000);        // 2,000 characters, 6,000 bytes
        assertThatThrownBy(() -> TextRules.validText(twoThousandThreeByteChars, "Comment"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4,000 bytes");
        assertThat(TextRules.validText("é".repeat(2000), "Comment")).hasSize(2000);   // exactly 4,000 bytes
    }

    @Test
    void charactersAreCountedAsCodePoints() {
        String emoji = new String(Character.toChars(0x1FA91));                  // one character, two UTF-16 units
        assertThat(TextRules.validText(emoji.repeat(1000), "Comment")).isNotEmpty();   // 1,000 chars, 4,000 bytes
    }
}
