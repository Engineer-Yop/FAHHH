package com.example.sanivend;

/**
 * Text and bookkeeping for the "new order" notification.
 * Plain Java (no Android code), so it is covered by OrderAlertRulesTest.
 *
 * Each sale is saved by ESP32 #1 as /devices/{id}/orders/order_N with N counting up
 * (order_1, order_2 …). The app remembers the highest N it has already told the owner about
 * and notifies only for higher numbers, so old orders never pop up again.
 */
public final class OrderAlertRules {

    /** At most this many single-order notifications from one background check; the rest are summed up. */
    public static final int MAX_AT_ONCE = 5;

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private OrderAlertRules() {}

    /** "order_12" → 12; anything else (e.g. "summary") → -1. */
    public static int number(String key) {
        if (key == null || !key.startsWith("order_")) return -1;
        try {
            int n = Integer.parseInt(key.substring(6));
            return n > 0 ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String title(int tray, int price) {
        return "New order — Tray " + tray + " · ₱" + price;
    }

    /**
     * "Kleenfant Wet Wipes · ₱15 · Nov 10, 2026 3:45 PM · paid ₱20, change ₱5"
     * @param datetime "yyyy-MM-dd HH:mm:ss" from the machine; shown as-is if it can't be read
     */
    public static String message(String product, int tray, int price, String datetime,
                                 int amountPaid, int change) {
        StringBuilder b = new StringBuilder();
        b.append(product).append(" (tray ").append(tray).append(") · ₱").append(price);
        String when = showTime(datetime);
        if (!when.isEmpty()) b.append(" · ").append(when);
        if (amountPaid > 0) {
            b.append(" · paid ₱").append(amountPaid);
            if (change > 0) b.append(", change ₱").append(change);
        }
        return b.toString();
    }

    /** "2026-11-10 15:45:09" → "Nov 10, 2026 3:45 PM" */
    public static String showTime(String datetime) {
        if (datetime == null || datetime.length() < 16) return datetime == null ? "" : datetime;
        try {
            int[] d = AlertRules.parse(datetime.substring(0, 10));
            int h = Integer.parseInt(datetime.substring(11, 13));
            int m = Integer.parseInt(datetime.substring(14, 16));
            if (h < 0 || h > 23 || m < 0 || m > 59) return datetime;
            int h12 = h % 12 == 0 ? 12 : h % 12;
            return MONTHS[d[1] - 1] + " " + d[2] + ", " + d[0] + " " + h12 + ":"
                    + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
        } catch (RuntimeException e) {
            return datetime;
        }
    }

    /** "and 3 more orders" line for a background check that found many new sales. */
    public static String moreText(int more) {
        return more == 1 ? "1 more new order — open Sales Monitoring to see it."
                : more + " more new orders — open Sales Monitoring to see them.";
    }
}
