package com.example.sanivend;

/**
 * Decides whether a tray needs a stock or expiry alert.
 * Plain Java (no Android code), so it is covered by AlertRulesTest.
 *
 *   Stock:  0 left            → OUT OF STOCK
 *           1–2 left          → LOW STOCK
 *   Expiry: date has passed   → EXPIRED       (reminds once a day)
 *           date is today     → EXPIRES TODAY
 *           within 7 days     → EXPIRING SOON (the same 7-day window the ESP32 uses)
 */
public final class AlertRules {

    /** This many items or fewer counts as low stock. */
    public static final int LOW_STOCK_AT = 2;
    /** Warn this many days before the expiry date. */
    public static final int EXPIRY_WARN_DAYS = 7;

    public enum Kind { STOCK, EXPIRY }

    public enum Level { OK, LOW, EMPTY, SOON, TODAY, EXPIRED }

    /** One tray's result for one kind of alert. */
    public static final class Alert {
        public final Kind kind;
        public final Level level;
        public final int tray;
        public final String title;
        public final String message;
        /**
         * Changes whenever the user should be told again — one fewer item, one fewer day,
         * a new expiry date. The app only notifies when this differs from last time.
         */
        public final String state;

        Alert(Kind kind, Level level, int tray, String title, String message, String state) {
            this.kind = kind;
            this.level = level;
            this.tray = tray;
            this.title = title;
            this.message = message;
            this.state = state;
        }

        public boolean needsNotification() {
            return level != Level.OK;
        }
    }

    private AlertRules() {}

    /** Stock alert for one tray. Returns null when the tray has no stock value yet. */
    public static Alert stock(int tray, String product, Integer stock, int capacity) {
        if (stock == null) return null;
        String of = capacity > 0 ? " of " + capacity : "";

        if (stock <= 0) {
            return new Alert(Kind.STOCK, Level.EMPTY, tray,
                    "Out of stock — Tray " + tray,
                    product + " is empty (0" + of + " left). Refill tray " + tray + ".",
                    "EMPTY");
        }
        if (stock <= LOW_STOCK_AT) {
            return new Alert(Kind.STOCK, Level.LOW, tray,
                    "Low stock — Tray " + tray,
                    product + " is running low: only " + stock + of + " left.",
                    "LOW:" + stock);
        }
        return new Alert(Kind.STOCK, Level.OK, tray, "", "", "OK");
    }

    /**
     * Expiry alert for one tray.
     * @param expiry "yyyy-MM-dd" as saved by the app; null or "" means not set
     * @param today  "yyyy-MM-dd" on the phone
     * @return null when no valid expiry date is set
     */
    public static Alert expiry(int tray, String product, String expiry, String today) {
        if (expiry == null || expiry.trim().isEmpty()) return null;
        int days;
        try {
            days = daysBetween(today, expiry.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String shown = showDate(expiry.trim());

        if (days < 0) {
            int ago = -days;
            return new Alert(Kind.EXPIRY, Level.EXPIRED, tray,
                    "EXPIRED — Tray " + tray,
                    product + " expired on " + shown + " (" + ago + (ago == 1 ? " day" : " days")
                            + " ago). Remove it from the machine.",
                    "EXPIRED:" + expiry.trim() + ":" + today);      // once a day
        }
        if (days == 0) {
            return new Alert(Kind.EXPIRY, Level.TODAY, tray,
                    "Expires today — Tray " + tray,
                    product + " expires today (" + shown + ").",
                    "TODAY:" + expiry.trim());
        }
        if (days <= EXPIRY_WARN_DAYS) {
            return new Alert(Kind.EXPIRY, Level.SOON, tray,
                    "Expiring soon — Tray " + tray,
                    product + " expires in " + days + (days == 1 ? " day" : " days") + " (" + shown + ").",
                    "SOON:" + expiry.trim() + ":" + days);           // once per day left
        }
        return new Alert(Kind.EXPIRY, Level.OK, tray, "", "", "OK");
    }

    /** Days from 'fromIso' to 'toIso' (both "yyyy-MM-dd"); negative if 'toIso' is earlier. */
    public static int daysBetween(String fromIso, String toIso) {
        return (int) (epochDay(toIso) - epochDay(fromIso));
    }

    /** "2026-12-26" → "26/12/2026" (the format shown in the app). */
    public static String showDate(String iso) {
        int[] p = parse(iso);
        return String.format(java.util.Locale.US, "%02d/%02d/%04d", p[2], p[1], p[0]);
    }

    // ── Calendar maths without time zones (works on every Android version) ──

    static long epochDay(String iso) {
        int[] p = parse(iso);
        return epochDay(p[0], p[1], p[2]);
    }

    /** Days since 1970-01-01 (Howard Hinnant's days_from_civil). */
    static long epochDay(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = Math.floorDiv(y, 400);
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** Strict "yyyy-MM-dd" check, including real month lengths and leap years. */
    static int[] parse(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Not a yyyy-MM-dd date: " + iso);
        }
        int y = Integer.parseInt(iso.substring(0, 4));
        int m = Integer.parseInt(iso.substring(5, 7));
        int d = Integer.parseInt(iso.substring(8, 10));
        if (m < 1 || m > 12 || d < 1 || d > daysInMonth(y, m)) {
            throw new IllegalArgumentException("Not a real date: " + iso);
        }
        return new int[]{y, m, d};
    }

    static int daysInMonth(int y, int m) {
        switch (m) {
            case 2:  return (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) ? 29 : 28;
            case 4: case 6: case 9: case 11: return 30;
            default: return 31;
        }
    }
}
