package com.glea.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

class ChunkPlannerTest {

    private static final LocalDate START = LocalDate.of(2010, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 1);

    @Test
    void fullStudyPeriodProducesThirtyNineWindows() {
        List<DateChunk> chunks = ChunkPlanner.plan(START, END);

        assertEquals(39, chunks.size());
        assertEquals(new DateChunk(LocalDate.of(2010, 1, 1), LocalDate.of(2010, 6, 1)), chunks.get(0));
        assertEquals(new DateChunk(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 1, 1)), chunks.get(38));
    }

    @Test
    void windowsCoverThePeriodWithoutGapsOrOverlaps() {
        List<DateChunk> chunks = ChunkPlanner.plan(START, END);

        assertEquals(START, chunks.get(0).start());
        assertEquals(END, chunks.get(chunks.size() - 1).end());
        for (int i = 1; i < chunks.size(); i++) {
            assertEquals(chunks.get(i - 1).end(), chunks.get(i).start(), "gap or overlap at window " + i);
        }
        long days = chunks.stream().mapToLong(c -> ChronoUnit.DAYS.between(c.start(), c.end())).sum();
        assertEquals(ChronoUnit.DAYS.between(START, END), days);
    }

    @Test
    void noWindowIsLongerThanFiveMonths() {
        for (DateChunk chunk : ChunkPlanner.plan(START, END)) {
            assertTrue(!chunk.end().isAfter(chunk.start().plusMonths(5)), chunk.toString());
        }
    }

    @Test
    void singleDayIsOneWindow() {
        LocalDate day = LocalDate.of(2025, 1, 1);

        assertEquals(List.of(new DateChunk(day, day.plusDays(1))), ChunkPlanner.plan(day, day.plusDays(1)));
    }

    @Test
    void shortPeriodIsOneWindowEndingAtTheRequestedEnd() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 2, 15);

        assertEquals(List.of(new DateChunk(start, end)), ChunkPlanner.plan(start, end));
    }

    @Test
    void startingOnJanuaryThirtyFirstStillLeavesNoGaps() {
        LocalDate start = LocalDate.of(2024, 1, 31);
        LocalDate end = LocalDate.of(2025, 1, 31);
        List<DateChunk> chunks = ChunkPlanner.plan(start, end);

        // plusMonths(5) from 31 January clamps to 30 June; the next window starts there.
        assertEquals(new DateChunk(start, LocalDate.of(2024, 6, 30)), chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            assertEquals(chunks.get(i - 1).end(), chunks.get(i).start());
        }
        assertEquals(end, chunks.get(chunks.size() - 1).end());
    }

    @Test
    void emptyOrInvertedPeriodIsRejected() {
        LocalDate day = LocalDate.of(2025, 1, 1);

        assertThrows(IllegalArgumentException.class, () -> ChunkPlanner.plan(day, day));
        assertThrows(IllegalArgumentException.class, () -> ChunkPlanner.plan(day.plusDays(1), day));
        assertThrows(IllegalArgumentException.class, () -> ChunkPlanner.plan(null, day));
        assertThrows(IllegalArgumentException.class, () -> ChunkPlanner.plan(day, null));
    }

    @Test
    void aemetFormatUsesInclusiveEndOneDayBeforeExclusiveEnd() {
        DateChunk chunk = new DateChunk(LocalDate.of(2010, 1, 1), LocalDate.of(2010, 6, 1));

        assertEquals("2010-01-01T00:00:00UTC", chunk.aemetStart());
        assertEquals("2010-05-31T23:59:59UTC", chunk.aemetEnd());
    }

    @Test
    void aemetFormatHandlesLeapDayAndYearChange() {
        assertEquals("2024-02-29T23:59:59UTC",
                new DateChunk(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 1)).aemetEnd());
        assertEquals("2025-12-31T23:59:59UTC",
                new DateChunk(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 1, 1)).aemetEnd());
    }

    @Test
    void chunkRejectsEmptyWindow() {
        LocalDate day = LocalDate.of(2025, 1, 1);

        assertThrows(IllegalArgumentException.class, () -> new DateChunk(day, day));
    }
}
