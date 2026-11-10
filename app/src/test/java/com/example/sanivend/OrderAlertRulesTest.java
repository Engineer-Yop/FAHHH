package com.example.sanivend;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Text of the "new order" notification and reading order numbers. */
public class OrderAlertRulesTest {

    @Test
    public void orderNumbers() {
        assertEquals(1, OrderAlertRules.number("order_1"));
        assertEquals(126, OrderAlertRules.number("order_126"));
        assertEquals(-1, OrderAlertRules.number("summary"));
        assertEquals(-1, OrderAlertRules.number("order_"));
        assertEquals(-1, OrderAlertRules.number("order_x"));
        assertEquals(-1, OrderAlertRules.number(null));
    }

    @Test
    public void message() {
        assertEquals("New order — Tray 3 · ₱15", OrderAlertRules.title(3, 15));
        assertEquals("Kleenfant Wet Wipes (tray 3) · ₱15 · Nov 10, 2026 3:45 PM · paid ₱20, change ₱5",
                OrderAlertRules.message("Kleenfant Wet Wipes", 3, 15, "2026-11-10 15:45:09", 20, 5));
        assertEquals("Sisters Sanitary Napkin (tray 1) · ₱5 · Nov 10, 2026 12:05 AM · paid ₱5",
                OrderAlertRules.message("Sisters Sanitary Napkin", 1, 5, "2026-11-10 00:05:00", 5, 0));
    }

    @Test
    public void times() {
        assertEquals("Nov 10, 2026 12:00 PM", OrderAlertRules.showTime("2026-11-10 12:00:00"));
        assertEquals("Jan 1, 2027 9:07 AM", OrderAlertRules.showTime("2027-01-01 09:07:59"));
        assertEquals("unknown", OrderAlertRules.showTime("unknown"));     // no clock on the machine
        assertEquals("", OrderAlertRules.showTime(null));
    }
}
