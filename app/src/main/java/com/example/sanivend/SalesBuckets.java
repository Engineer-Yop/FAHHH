package com.example.sanivend;

/**
 * Puts each order into a point on the Sales Performance graph.
 * Plain Java, covered by SalesBucketsTest.
 *
 *   WEEK  → 7 points, Sunday … Saturday of the current week
 *   MONTH → one point per day of the current month (28–31 points)
 *   YEAR  → 12 points, January … December of the current year
 */
public final class SalesBuckets {

    public static final String WEEK = "Week";
    public static final String MONTH = "Month";
    public static final String YEAR = "Year";

    private SalesBuckets() {}

    /** Number of graph points; MONTH depends on how many days this month has. */
    public static int size(String mode, String today) {
        if (WEEK.equals(mode)) return 7;
        if (MONTH.equals(mode)) {
            int[] t = AlertRules.parse(today);
            return AlertRules.daysInMonth(t[0], t[1]);
        }
        return 12;
    }

    /** Labels under the graph: Sun…Sat, 1…31, or Jan…Dec. */
    public static String[] labels(String mode, String today) {
        if (WEEK.equals(mode)) return new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        if (MONTH.equals(mode)) {
            String[] days = new String[size(MONTH, today)];
            for (int i = 0; i < days.length; i++) days[i] = String.valueOf(i + 1);
            return days;
        }
        return new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    }

    /**
     * @param date  order date "yyyy-MM-dd"
     * @param today today on the phone, "yyyy-MM-dd"
     * @return the graph point for this order, or -1 if it falls outside the chosen period
     *         (or its date is missing, e.g. an old "unknown" record)
     */
    public static int index(String mode, String date, String today) {
        try {
            long d = AlertRules.epochDay(date);
            long t = AlertRules.epochDay(today);
            if (WEEK.equals(mode)) {
                long weekStart = t - dayOfWeek(t);       // the Sunday of this week
                return (d >= weekStart && d <= weekStart + 6) ? (int) (d - weekStart) : -1;
            }
            if (MONTH.equals(mode)) {
                if (!date.substring(0, 7).equals(today.substring(0, 7))) return -1;
                return Integer.parseInt(date.substring(8, 10)) - 1;
            }
            if (!date.substring(0, 4).equals(today.substring(0, 4))) return -1;
            return Integer.parseInt(date.substring(5, 7)) - 1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Title over the graph, e.g. "This week", "November 2026", "2026". */
    public static String periodName(String mode, String today) {
        if (WEEK.equals(mode)) return "This week";
        int[] t = AlertRules.parse(today);
        if (MONTH.equals(mode)) return MonthWeeks.MONTHS[t[1] - 1] + " " + t[0];
        return String.valueOf(t[0]);
    }

    /** 0 = Sunday … 6 = Saturday. 1970-01-01 (day 0) was a Thursday. */
    static int dayOfWeek(long epochDay) {
        return (int) Math.floorMod(epochDay + 4, 7L);
    }
}
