package com.example.sanivend;

import java.util.Locale;

/**
 * Rules for machine IDs, pairing codes and the online label.
 * Plain Java (no Android code), covered by DeviceIdsTest.
 *
 * Every SaniVend machine (ESP32 #1) makes its ID from its chip's factory MAC address:
 * "SV-" + the last 6 hex digits, e.g. SV-A1B2C3. It shows the ID on the LCD, and
 * a 6-digit pairing code at start-up. Adding a machine in the app needs both.
 */
public final class DeviceIds {

    /** A machine counts as online if it checked in within this many minutes (it checks in every minute). */
    public static final int ONLINE_WINDOW_MIN = 3;

    private DeviceIds() {}

    /**
     * Cleans what the user typed: "sv-a1b2c3", " A1B2C3 ", "SV A1B2C3", "sva1b2c3" → "SV-A1B2C3".
     * @return the ID in standard form, or null if it isn't a valid machine ID
     */
    public static String normalize(String typed) {
        if (typed == null) return null;
        String s = typed.trim().toUpperCase(Locale.US).replace(" ", "").replace("-", "");
        if (s.startsWith("SV")) s = s.substring(2);
        if (!s.matches("[0-9A-F]{6}")) return null;
        return "SV-" + s;
    }

    /** Pairing codes are exactly 6 digits. */
    public static boolean isValidCode(String code) {
        return code != null && code.trim().matches("\\d{6}");
    }

    /** True when the code typed matches the one the machine saved (spaces ignored). */
    public static boolean codeMatches(String typed, Object stored) {
        if (!isValidCode(typed) || stored == null) return false;
        return typed.trim().equals(String.valueOf(stored).trim());
    }

    /** Pairing is refused only when someone else already owns the machine. */
    public static boolean canClaim(String currentOwnerUid, String myUid) {
        return currentOwnerUid == null || currentOwnerUid.isEmpty() || currentOwnerUid.equals(myUid);
    }

    public static boolean isOnline(long lastSeenMs, long nowMs) {
        return lastSeenMs > 0 && nowMs - lastSeenMs <= ONLINE_WINDOW_MIN * 60_000L;
    }

    /** "Online", "Offline · last seen 12 min ago", "Offline · last seen 3 h ago", "Offline · last seen 2 days ago". */
    public static String statusLabel(long lastSeenMs, long nowMs) {
        if (lastSeenMs <= 0) return "Offline · not connected yet";
        if (isOnline(lastSeenMs, nowMs)) return "Online";
        long min = Math.max(0, (nowMs - lastSeenMs) / 60_000L);
        if (min < 60) return "Offline · last seen " + min + " min ago";
        long h = min / 60;
        if (h < 24) return "Offline · last seen " + h + " h ago";
        long d = h / 24;
        return "Offline · last seen " + d + (d == 1 ? " day ago" : " days ago");
    }
}
