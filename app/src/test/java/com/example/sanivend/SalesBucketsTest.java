package com.example.sanivend;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which point of the Sales Graph each order lands on. */
public class SalesBucketsTest {

    // Friday, 6 Nov 2026 → this week is Sun 1 Nov … Sat 7 Nov
    private static final String TODAY = "2026-11-06";

    @Test
    public void sizes() {
        assertEquals(24, SalesBuckets.size(SalesBuckets.TODAY));
        assertEquals(7, SalesBuckets.size(SalesBuckets.WEEK));
        assertEquals(12, SalesBuckets.size(SalesBuckets.YEAR));
    }

    @Test
    public void todayByHour() {
        assertEquals(0, SalesBuckets.index(SalesBuckets.TODAY, TODAY, TODAY + " 00:15:00", TODAY));
        assertEquals(14, SalesBuckets.index(SalesBuckets.TODAY, TODAY, TODAY + " 14:59:59", TODAY));
        assertEquals(23, SalesBuckets.index(SalesBuckets.TODAY, TODAY, TODAY + " 23:00:00", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.TODAY, "2026-11-05", "2026-11-05 10:00:00", TODAY));
    }

    @Test
    public void weekRunsSundayToSaturday() {
        assertEquals(0, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-01", "", TODAY));   // Sun
        assertEquals(5, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-06", "", TODAY));   // Fri (today)
        assertEquals(6, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-07", "", TODAY));   // Sat
        assertEquals(-1, SalesBuckets.index(SalesBuckets.WEEK, "2026-10-31", "", TODAY));  // last Sat
        assertEquals(-1, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-08", "", TODAY));  // next Sun
    }

    @Test
    public void weekAcrossNewYear() {
        // Thu 31 Dec 2026 → week is Sun 27 Dec … Sat 2 Jan
        assertEquals(6, SalesBuckets.index(SalesBuckets.WEEK, "2027-01-02", "", "2026-12-31"));
        assertEquals(0, SalesBuckets.index(SalesBuckets.WEEK, "2026-12-27", "", "2026-12-31"));
    }

    @Test
    public void yearByMonth() {
        assertEquals(0, SalesBuckets.index(SalesBuckets.YEAR, "2026-01-15", "", TODAY));
        assertEquals(10, SalesBuckets.index(SalesBuckets.YEAR, "2026-11-06", "", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.YEAR, "2025-11-06", "", TODAY));
    }

    @Test
    public void unknownOrBrokenDatesAreSkipped() {
        assertEquals(-1, SalesBuckets.index(SalesBuckets.TODAY, "unknown", "unknown unknown", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.WEEK, "unknown", "", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.YEAR, "", "", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.YEAR, null, null, TODAY));
    }

    @Test
    public void dayOfWeek() {
        assertEquals(4, SalesBuckets.dayOfWeek(0));                          // 1970-01-01 Thursday
        assertEquals(5, SalesBuckets.dayOfWeek(AlertRules.epochDay("2026-11-06")));   // Friday
        assertEquals(0, SalesBuckets.dayOfWeek(AlertRules.epochDay("2026-11-08")));   // Sunday
    }
}
