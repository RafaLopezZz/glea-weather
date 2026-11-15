package com.glea.weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a requested period [start, end) into consecutive windows that AEMET accepts.
 * Each window starts exactly where the previous one ended, so the windows never
 * overlap and never leave gaps. No network or database access.
 */
public final class ChunkPlanner {

    /** Window length used by the climaemet reference client for one station. */
    static final int MAX_MONTHS = 5;

    private ChunkPlanner() {
    }

    public static List<DateChunk> plan(LocalDate start, LocalDate end) {
        return plan(start, end, MAX_MONTHS);
    }

    static List<DateChunk> plan(LocalDate start, LocalDate end, int maxMonths) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Period bounds are required.");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Period start must be before the exclusive end.");
        }
        if (maxMonths < 1) {
            throw new IllegalArgumentException("Chunk length must be at least one month.");
        }

        List<DateChunk> chunks = new ArrayList<>();
        LocalDate chunkStart = start;
        while (chunkStart.isBefore(end)) {
            LocalDate candidateEnd = chunkStart.plusMonths(maxMonths);
            LocalDate chunkEnd = candidateEnd.isBefore(end) ? candidateEnd : end;
            chunks.add(new DateChunk(chunkStart, chunkEnd));
            chunkStart = chunkEnd;
        }
        return List.copyOf(chunks);
    }
}
