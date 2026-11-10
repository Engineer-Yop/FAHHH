package com.example.sanivend;

/**
 * Sales Monitoring shows only this month's orders, grouped by week:
 *   Week 1 = days 1–7, Week 2 = 8–14, Week 3 = 15–21, Week 4 = 22–28, Week 5 = 29–31.
 * When a new month starts the list is empty again (nothing is deleted: the orders
 * stay in Firebase and in the Sales Performance graph).
 *
 * Plain Java, covered by MonthWeeksTest.
 */
public final class MonthWeeks {

    static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    private static final String[] SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
            "Aug", "Sep", "Oct", "Nov", "Dec"};

    private MonthWeeks() {}

    /** True if the order date "yyyy-MM-dd" is in the same month as today. */
    public static boolean inThisMonth(String date, String today) {
        try {
            AlertRules.parse(date);
            return date.substring(0, 7).equals(today.substring(0, 7));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Week of the month, 1–5, for a valid "yyyy-MM-dd" date. */
    public static int week(String date) {
        int day = AlertRules.parse(date)[2];
        return (day - 1) / 7 + 1;
    }

    /** "Week 2 · Nov 8–14" (the last week stops at the month's last day). */
    public static String label(int week, String today) {
        int[] t = AlertRules.parse(today);
        int first = (week - 1) * 7 + 1;
        int last = Math.min(week * 7, AlertRules.daysInMonth(t[0], t[1]));
        String m = SHORT[t[1] - 1];
        return "Week " + week + " · " + m + " " + first + (last > first ? "–" + last : "");
    }

    /** "November 2026" */
    public static String monthTitle(String today) {
        int[] t = AlertRules.parse(today);
        return MONTHS[t[1] - 1] + " " + t[0];
    }
}
