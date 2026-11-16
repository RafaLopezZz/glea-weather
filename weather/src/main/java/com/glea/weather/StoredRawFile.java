package com.glea.weather;

/**
 * What was written to disk: path relative to the RAW root (always with '/'), size and SHA-256.
 * These are the values recorded in raw_files and in the run manifest.
 */
public record StoredRawFile(String relativePath, long byteSize, String sha256) {
}
