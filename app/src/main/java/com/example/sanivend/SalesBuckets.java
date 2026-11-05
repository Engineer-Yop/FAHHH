package com.example.sanivend;

/**
 * Puts each order into a point on the Sales Graph. Plain Java, covered by SalesBucketsTest.
 *
 *   TODAY → 24 points, one per hour of today (12am … 11pm)
 *   WEEK  → 7 points, Sunday … Saturday of the current week
 *   YEAR  → 12 points, January … December of the current year
 */
public final class SalesBuckets {

    public static final String TODAY = "Today";
    public static final String WEEK = "Week";
    public static final String YEAR = "Year";

    private SalesBuckets() {}

    public static int size(String mode) {
        if (TODAY.equals(mode)) return 24;
        if (WEEK.equals(mode)) return 7;
        return 12;
    }

    /**
     * @param date     order date "yyyy-MM-dd"
     * @param datetime order time "yyyy-MM-dd HH:mm:ss"
     * @param today    today on the phone, "yyyy-MM-dd"
     * @return the graph point for this order, or -1 if it falls outside the chosen period
     *         (or its date is missing, e.g. an old "unknown" record)
     */
    public static int index(String mode, String date, String datetime, String today) {
        try {
            long d = AlertRules.epochDay(date);
            long t = AlertRules.epochDay(today);
            if (TODAY.equals(mode)) {
                if (d != t || datetime == null || datetime.length() < 13) return -1;
                int hour = Integer.parseInt(datetime.substring(11, 13));
                return (hour >= 0 && hour < 24) ? hour : -1;
            }
            if (WEEK.equals(mode)) {
                long weekStart = t - dayOfWeek(t);       // the Sunday of this week
                return (d >= weekStart && d <= weekStart + 6) ? (int) (d - weekStart) : -1;
            }
            if (!date.substring(0, 4).equals(today.substring(0, 4))) return -1;
            return Integer.parseInt(date.substring(5, 7)) - 1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** 0 = Sunday … 6 = Saturday. 1970-01-01 (day 0) was a Thursday. */
    static int dayOfWeek(long epochDay) {
        return (int) Math.floorMod(epochDay + 4, 7L);
    }
}
