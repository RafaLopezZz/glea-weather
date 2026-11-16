package com.glea.weather.raw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.glea.weather.acquisition.DateChunk;
import com.glea.weather.aemet.AemetPayload;

class RawFileStoreTest {

    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final DateChunk JANUARY = new DateChunk(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1));
    /** Known SHA-256 of the ASCII text "abc" (FIPS 180-2 test vector). */
    private static final String SHA256_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path root;

    private AemetPayload payload(String text) {
        return new AemetPayload(text.getBytes(StandardCharsets.ISO_8859_1), "text/plain;charset=ISO-8859-15");
    }

    @Test
    void sha256MatchesTheStandardTestVector() {
        assertEquals(SHA256_ABC, RawFileStore.sha256("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void storesTheExactBytesAndReturnsPathSizeAndHash() throws IOException {
        RawFileStore store = new RawFileStore(root.resolve("raw"));

        StoredRawFile stored = store.store("7211B", RUN, JANUARY, payload("abc"));

        String expectedPath = "7211B/00000000-0000-0000-0000-000000000123/2025-01-01_2025-02-01.json";
        assertEquals(new StoredRawFile(expectedPath, 3, SHA256_ABC), stored);
        assertArrayEquals("abc".getBytes(StandardCharsets.US_ASCII),
                Files.readAllBytes(root.resolve("raw").resolve(expectedPath)));
    }

    @Test
    void neverOverwritesAnExistingOriginal() throws IOException {
        RawFileStore store = new RawFileStore(root);
        StoredRawFile first = store.store("7211B", RUN, JANUARY, payload("abc"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.store("7211B", RUN, JANUARY, payload("different")));

        assertTrue(error.getMessage().startsWith("RAW file already exists"));
        assertArrayEquals("abc".getBytes(StandardCharsets.US_ASCII),
                Files.readAllBytes(root.resolve(first.relativePath())));
    }

    @Test
    void leavesNoTemporaryFilesBehind() throws IOException {
        RawFileStore store = new RawFileStore(root);
        StoredRawFile stored = store.store("7211B", RUN, JANUARY, payload("abc"));

        try (Stream<Path> files = Files.list(root.resolve(stored.relativePath()).getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void verifyAcceptsTheUntouchedFileAndDetectsAnyChange() throws IOException {
        RawFileStore store = new RawFileStore(root);
        StoredRawFile stored = store.store("7211B", RUN, JANUARY, payload("abc"));
        assertTrue(store.verify(stored));

        Files.write(root.resolve(stored.relativePath()), "abd".getBytes(StandardCharsets.US_ASCII));

        assertFalse(store.verify(stored));
    }

    @Test
    void verifyReportsAMissingFileAsNotValid() {
        RawFileStore store = new RawFileStore(root);

        assertFalse(store.verify(new StoredRawFile("7211B/x/missing.json", 3, SHA256_ABC)));
    }

    @Test
    void verifyRefusesPathsOutsideTheRoot() {
        RawFileStore store = new RawFileStore(root);

        assertThrows(IllegalArgumentException.class,
                () -> store.verify(new StoredRawFile("../outside.json", 3, SHA256_ABC)));
    }

    @Test
    void relativePathFitsTheRawFilesConstraints() {
        String path = RawFileStore.relativePath("7211B", RUN, JANUARY);

        assertFalse(path.startsWith("/"));
        assertFalse(path.contains("\\"));
        assertFalse(path.contains(".."));
        assertFalse(path.matches("^[A-Za-z]:.*"));
    }

    @Test
    void rejectsStationIdentifiersThatCouldEscapeTheRoot() {
        assertThrows(IllegalArgumentException.class, () -> RawFileStore.relativePath("..", RUN, JANUARY));
        assertThrows(IllegalArgumentException.class, () -> RawFileStore.relativePath("a/b", RUN, JANUARY));
        assertThrows(IllegalArgumentException.class, () -> RawFileStore.relativePath("7211B", null, JANUARY));
    }
}
