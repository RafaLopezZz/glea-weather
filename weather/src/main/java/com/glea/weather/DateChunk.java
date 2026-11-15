package com.glea.weather;

import java.time.LocalDate;

/**
 * One request window using the project convention [start, end): start included, end excluded.
 * AEMET expects an inclusive end, so the conversion to its format happens only here.
 */
public record DateChunk(LocalDate start, LocalDate end) {

    public DateChunk {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Chunk bounds are required.");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Chunk start must be before the exclusive end.");
        }
    }

    /** First instant requested from AEMET, e.g. 2010-01-01T00:00:00UTC. */
    public String aemetStart() {
        return start + "T00:00:00UTC";
    }

    /** Last instant requested from AEMET (inclusive): the day before {@code end}, at 23:59:59. */
    public String aemetEnd() {
        return end.minusDays(1) + "T23:59:59UTC";
    }
}
