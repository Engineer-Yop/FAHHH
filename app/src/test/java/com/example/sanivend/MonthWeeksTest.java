package com.example.sanivend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Sales Monitoring: only this month's orders, grouped by week. */
public class MonthWeeksTest {

    private static final String TODAY = "2026-11-10";

    @Test
    public void onlyThisMonth() {
        assertTrue(MonthWeeks.inThisMonth("2026-11-01", TODAY));
        assertTrue(MonthWeeks.inThisMonth("2026-11-30", TODAY));
        assertFalse(MonthWeeks.inThisMonth("2026-10-31", TODAY));    // cleared when November began
        assertFalse(MonthWeeks.inThisMonth("2025-11-10", TODAY));    // same month, last year
        assertFalse(MonthWeeks.inThisMonth("unknown", TODAY));
        assertFalse(MonthWeeks.inThisMonth(null, TODAY));
    }

    @Test
    public void weekOfMonth() {
        assertEquals(1, MonthWeeks.week("2026-11-01"));
        assertEquals(1, MonthWeeks.week("2026-11-07"));
        assertEquals(2, MonthWeeks.week("2026-11-08"));
        assertEquals(4, MonthWeeks.week("2026-11-28"));
        assertEquals(5, MonthWeeks.week("2026-11-29"));
        assertEquals(5, MonthWeeks.week("2026-12-31"));
    }

    @Test
    public void labels() {
        assertEquals("Week 1 · Nov 1–7", MonthWeeks.label(1, TODAY));
        assertEquals("Week 2 · Nov 8–14", MonthWeeks.label(2, TODAY));
        assertEquals("Week 5 · Nov 29–30", MonthWeeks.label(5, TODAY));            // 30-day month
        assertEquals("Week 5 · Feb 29", MonthWeeks.label(5, "2028-02-10"));       // leap February
        assertEquals("November 2026", MonthWeeks.monthTitle(TODAY));
    }
}
