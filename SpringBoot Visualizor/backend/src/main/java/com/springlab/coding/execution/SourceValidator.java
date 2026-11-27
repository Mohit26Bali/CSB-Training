package com.springlab.coding.execution;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Checks a source BEFORE it is queued, so a refused source costs no queue slot, no worker and no sandbox. */
public final class SourceValidator {

    public static final int MAX_SOURCE_BYTES = 64 * 1024;

    private SourceValidator() {
    }

    public static void validate(byte[] source) {
        if (source == null || source.length == 0) {
            throw new InvalidSourceException("The source is empty");
        }
        if (source.length > MAX_SOURCE_BYTES) {
            throw new InvalidSourceException("The source is larger than " + MAX_SOURCE_BYTES + " bytes");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source)).toString();
        } catch (CharacterCodingException e) {
            throw new InvalidSourceException("The source is not valid UTF-8");
        }
        if (text.indexOf('\0') >= 0) {
            throw new InvalidSourceException("The source contains a NUL character");
        }
    }
}
