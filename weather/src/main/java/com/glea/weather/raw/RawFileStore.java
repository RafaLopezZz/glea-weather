package com.glea.weather.raw;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import com.glea.weather.acquisition.DateChunk;
import com.glea.weather.aemet.AemetPayload;

/**
 * Immutable store for original AEMET responses.
 * Layout: {@code <root>/<station>/<run_id>/<start>_<end>.json}. A file is
 * written once:
 * bytes go to a temporary file in the same folder, are forced to disk and then
 * renamed in
 * one atomic step, so an interrupted write never leaves a truncated file with a
 * final name.
 */
public class RawFileStore {

    private static final Pattern STATION_ID = Pattern.compile("[A-Za-z0-9]{1,20}");

    private final Path root;

    public RawFileStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("RAW root is required.");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Relative path inside the RAW root, always with '/' so it is portable and fits
     * raw_files.
     */
    static String relativePath(String stationId, UUID runId, DateChunk chunk) {
        if (stationId == null || !STATION_ID.matcher(stationId).matches()) {
            throw new IllegalArgumentException("Invalid station identifier.");
        }
        if (runId == null || chunk == null) {
            throw new IllegalArgumentException("Run and chunk are required.");
        }
        return stationId + "/" + runId + "/" + chunk.start() + "_" + chunk.end() + ".json";
    }

    /**
     * Writes the payload once and returns its relative path, size and SHA-256.
     *
     * @throws IllegalStateException if the file already exists (originals are never
     *                               overwritten)
     * @throws UncheckedIOException  if the disk write fails; no final file is left
     *                               behind
     */
    public StoredRawFile store(String stationId, UUID runId, DateChunk chunk, AemetPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is required.");
        }
        String relative = relativePath(stationId, runId, chunk);
        Path target = resolve(relative);
        if (Files.exists(target)) {
            throw new IllegalStateException("RAW file already exists; originals are never overwritten: " + relative);
        }

        byte[] bytes = payload.body();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".tmp-", ".part");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store RAW file " + relative, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The original error is more useful than a failed cleanup.
                }
            }
        }
        return new StoredRawFile(relative, bytes.length, sha256(bytes));
    }

    /**
     * Recomputes the SHA-256 of a stored file and compares it with the expected
     * value.
     */
    public boolean verify(StoredRawFile expected) {
        Path file = resolve(expected.relativePath());
        try {
            byte[] bytes = Files.readAllBytes(file);
            return bytes.length == expected.byteSize() && sha256(bytes).equals(expected.sha256());
        } catch (IOException exception) {
            return false;
        }
    }

    /** Lowercase hexadecimal SHA-256, the canonical form required by raw_files. */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    /**
     * Resolves a relative path and refuses anything that would land outside the
     * root.
     */
    private Path resolve(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the RAW root.");
        }
        return resolved;
    }
}
