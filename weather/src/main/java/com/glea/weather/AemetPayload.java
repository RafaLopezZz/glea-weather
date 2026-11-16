package com.glea.weather;

import java.util.Arrays;

/**
 * Exact bytes of an AEMET "datos" response plus the Content-Type it was served
 * with.
 * The bytes are never decoded or rewritten; the charset is kept so Python can
 * read them later.
 */
public record AemetPayload(byte[] body, String contentType) {

    public AemetPayload {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("AEMET payload must not be empty.");
        }
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AemetPayload that
                && Arrays.equals(body, that.body)
                && java.util.Objects.equals(contentType, that.contentType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(body) + java.util.Objects.hashCode(contentType);
    }

    @Override
    public String toString() {
        return "AemetPayload[" + body.length + " bytes, contentType=" + contentType + "]";
    }
}
