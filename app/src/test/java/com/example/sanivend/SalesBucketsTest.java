package com.example.sanivend;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which point of the Sales Performance graph each order lands on. */
public class SalesBucketsTest {

    // Friday, 6 Nov 2026 → this week is Sun 1 Nov … Sat 7 Nov
    private static final String TODAY = "2026-11-06";

    @Test
    public void sizes() {
        assertEquals(7, SalesBuckets.size(SalesBuckets.WEEK, TODAY));
        assertEquals(30, SalesBuckets.size(SalesBuckets.MONTH, TODAY));          // November
        assertEquals(31, SalesBuckets.size(SalesBuckets.MONTH, "2026-12-15"));
        assertEquals(28, SalesBuckets.size(SalesBuckets.MONTH, "2027-02-01"));
        assertEquals(29, SalesBuckets.size(SalesBuckets.MONTH, "2028-02-01"));   // leap year
        assertEquals(12, SalesBuckets.size(SalesBuckets.YEAR, TODAY));
    }

    @Test
    public void labels() {
        assertArrayEquals(new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"},
                SalesBuckets.labels(SalesBuckets.WEEK, TODAY));
        String[] month = SalesBuckets.labels(SalesBuckets.MONTH, TODAY);
        assertEquals(30, month.length);
        assertEquals("1", month[0]);
        assertEquals("30", month[29]);
        assertEquals("Dec", SalesBuckets.labels(SalesBuckets.YEAR, TODAY)[11]);
    }

    @Test
    public void weekRunsSundayToSaturday() {
        assertEquals(0, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-01", TODAY));   // Sun
        assertEquals(5, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-06", TODAY));   // Fri (today)
        assertEquals(6, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-07", TODAY));   // Sat
        assertEquals(-1, SalesBuckets.index(SalesBuckets.WEEK, "2026-10-31", TODAY));  // last Sat
        assertEquals(-1, SalesBuckets.index(SalesBuckets.WEEK, "2026-11-08", TODAY));  // next Sun
    }

    @Test
    public void weekAcrossNewYear() {
        // Thu 31 Dec 2026 → week is Sun 27 Dec … Sat 2 Jan
        assertEquals(6, SalesBuckets.index(SalesBuckets.WEEK, "2027-01-02", "2026-12-31"));
        assertEquals(0, SalesBuckets.index(SalesBuckets.WEEK, "2026-12-27", "2026-12-31"));
    }

    @Test
    public void monthByDay() {
        assertEquals(0, SalesBuckets.index(SalesBuckets.MONTH, "2026-11-01", TODAY));
        assertEquals(5, SalesBuckets.index(SalesBuckets.MONTH, "2026-11-06", TODAY));
        assertEquals(29, SalesBuckets.index(SalesBuckets.MONTH, "2026-11-30", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.MONTH, "2026-10-31", TODAY));  // last month
        assertEquals(-1, SalesBuckets.index(SalesBuckets.MONTH, "2025-11-06", TODAY));  // last year
    }

    @Test
    public void yearByMonth() {
        assertEquals(0, SalesBuckets.index(SalesBuckets.YEAR, "2026-01-15", TODAY));
        assertEquals(10, SalesBuckets.index(SalesBuckets.YEAR, "2026-11-06", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.YEAR, "2025-11-06", TODAY));
    }

    @Test
    public void periodNames() {
        assertEquals("This week", SalesBuckets.periodName(SalesBuckets.WEEK, TODAY));
        assertEquals("November 2026", SalesBuckets.periodName(SalesBuckets.MONTH, TODAY));
        assertEquals("2026", SalesBuckets.periodName(SalesBuckets.YEAR, TODAY));
    }

    @Test
    public void unknownOrBrokenDatesAreSkipped() {
        assertEquals(-1, SalesBuckets.index(SalesBuckets.WEEK, "unknown", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.MONTH, "unknown", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.YEAR, "", TODAY));
        assertEquals(-1, SalesBuckets.index(SalesBuckets.YEAR, null, TODAY));
    }

    @Test
    public void dayOfWeek() {
        assertEquals(4, SalesBuckets.dayOfWeek(0));                          // 1970-01-01 Thursday
        assertEquals(5, SalesBuckets.dayOfWeek(AlertRules.epochDay("2026-11-06")));   // Friday
        assertEquals(0, SalesBuckets.dayOfWeek(AlertRules.epochDay("2026-11-08")));   // Sunday
    }
}
