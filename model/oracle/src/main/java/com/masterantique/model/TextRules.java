package com.masterantique.model;

import java.nio.charset.StandardCharsets;

/**
 * The legacy text rules for ticket descriptions and comments ({@code Ticket.CreateSubmitted},
 * {@code User.ValidateCommentText}): trimmed, required, at most 2,000 characters, no control characters other than
 * CR, LF and TAB; plus the Oracle limit of 4,000 bytes per value ({@code MAX_STRING_SIZE = STANDARD}), which multi-byte
 * text can reach before 2,000 characters. Violations throw {@link IllegalArgumentException} with a user-facing message.
 */
public final class TextRules {

    /** Characters (code points, as Oracle's CHAR length semantics count them) of a {@code VARCHAR2(2000 CHAR)}. */
    public static final int MAX_CHARACTERS = 2000;
    /** Bytes of one VARCHAR2 value in this database. */
    public static final int MAX_UTF8_BYTES = 4000;

    private TextRules() {
    }

    /**
     * Returns the text trimmed, or throws {@link IllegalArgumentException} naming {@code label} (e.g. "Description").
     */
    public static String validText(String raw, String label) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (text.codePointCount(0, text.length()) > MAX_CHARACTERS) {
            throw new IllegalArgumentException(label + " must be at most 2,000 characters.");
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isISOControl(cp) && cp != '\r' && cp != '\n' && cp != '\t') {
                throw new IllegalArgumentException(
                        label + " contains a control character; only line breaks and tabs are allowed.");
            }
            i += Character.charCount(cp);
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException(label + " is too long: at most 4,000 bytes in UTF-8.");
        }
        return text;
    }
}
