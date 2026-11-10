package com.example.sanivend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Checks the stock and expiry rules used by the notifications.
 * Run in Android Studio: right-click this file → Run 'AlertRulesTest'.
 */
public class AlertRulesTest {

    private static final String TODAY = "2026-11-06";

    // ── Stock ──────────────────────────────────

    @Test
    public void fullTrayIsOk() {
        AlertRules.Alert a = AlertRules.stock(8, "Alcosafe", 7, 7);
        assertEquals(AlertRules.Level.OK, a.level);
        assertFalse(a.needsNotification());
    }

    @Test
    public void threeLeftIsStillOk() {
        assertEquals(AlertRules.Level.OK, AlertRules.stock(1, "Sisters", 3, 7).level);
    }

    @Test
    public void twoLeftIsLow() {
        AlertRules.Alert a = AlertRules.stock(8, "Alcosafe", 2, 7);
        assertEquals(AlertRules.Level.LOW, a.level);
        assertTrue(a.needsNotification());
        assertEquals("Low stock — Tray 8 needs a refill", a.title);
        assertEquals("Alcosafe is running low: only 2 of 7 left. Refill tray 8 soon.", a.message);
    }

    @Test
    public void oneLeftIsLowAndNotifiesAgain() {
        AlertRules.Alert two = AlertRules.stock(8, "Alcosafe", 2, 7);
        AlertRules.Alert one = AlertRules.stock(8, "Alcosafe", 1, 7);
        assertEquals(AlertRules.Level.LOW, one.level);
        assertNotEquals(two.state, one.state);   // a new notification for each item sold while low
    }

    @Test
    public void zeroIsOutOfStock() {
        AlertRules.Alert a = AlertRules.stock(6, "Blest", 0, 7);
        assertEquals(AlertRules.Level.EMPTY, a.level);
        assertEquals("Out of stock — Tray 6 needs a refill", a.title);
        assertEquals("Blest is empty (0 of 7 left). Refill tray 6 now.", a.message);
    }

    @Test
    public void negativeStockCountsAsEmpty() {
        assertEquals(AlertRules.Level.EMPTY, AlertRules.stock(6, "Blest", -1, 7).level);
    }

    @Test
    public void missingStockGivesNoAlert() {
        assertNull(AlertRules.stock(3, "Kleenfant", null, 7));
    }

    // ── Expiry ─────────────────────────────────

    @Test
    public void farExpiryIsOk() {
        assertEquals(AlertRules.Level.OK, AlertRules.expiry(1, "Sisters", "2026-12-26", TODAY).level);
    }

    @Test
    public void eightDaysIsStillOk() {
        assertEquals(AlertRules.Level.OK, AlertRules.expiry(1, "Sisters", "2026-11-14", TODAY).level);
    }

    @Test
    public void sevenDaysIsSoon() {
        AlertRules.Alert a = AlertRules.expiry(1, "Sisters", "2026-11-13", TODAY);
        assertEquals(AlertRules.Level.SOON, a.level);
        assertEquals("Expiring soon — Tray 1", a.title);
        assertEquals("Sisters expires in 7 days (13/11/2026).", a.message);
    }

    @Test
    public void oneDayIsSingular() {
        AlertRules.Alert a = AlertRules.expiry(2, "Charmee", "2026-11-07", TODAY);
        assertEquals("Charmee expires in 1 day (07/11/2026).", a.message);
    }

    @Test
    public void soonNotifiesOncePerDayLeft() {
        AlertRules.Alert d3 = AlertRules.expiry(1, "Sisters", "2026-11-09", "2026-11-06");
        AlertRules.Alert d3again = AlertRules.expiry(1, "Sisters", "2026-11-09", "2026-11-06");
        AlertRules.Alert d2 = AlertRules.expiry(1, "Sisters", "2026-11-09", "2026-11-07");
        assertEquals(d3.state, d3again.state);   // same day → no repeat
        assertNotEquals(d3.state, d2.state);     // next day → one new reminder
    }

    @Test
    public void todayIsToday() {
        AlertRules.Alert a = AlertRules.expiry(5, "Femme", TODAY, TODAY);
        assertEquals(AlertRules.Level.TODAY, a.level);
        assertEquals("Femme expires today (06/11/2026).", a.message);
    }

    @Test
    public void pastDateIsExpired() {
        AlertRules.Alert a = AlertRules.expiry(7, "Hygienix", "2026-11-03", TODAY);
        assertEquals(AlertRules.Level.EXPIRED, a.level);
        assertEquals("EXPIRED — Tray 7", a.title);
        assertEquals("Hygienix expired on 03/11/2026 (3 days ago). Remove it from the machine.", a.message);
    }

    @Test
    public void expiredRemindsOncePerDay() {
        String s1 = AlertRules.expiry(7, "Hygienix", "2026-11-03", "2026-11-06").state;
        String s2 = AlertRules.expiry(7, "Hygienix", "2026-11-03", "2026-11-07").state;
        assertNotEquals(s1, s2);
    }

    @Test
    public void newExpiryDateNotifiesAgain() {
        String before = AlertRules.expiry(4, "Giggles", "2026-11-10", TODAY).state;
        String after = AlertRules.expiry(4, "Giggles", "2026-11-11", TODAY).state;
        assertNotEquals(before, after);
    }

    @Test
    public void emptyOrBadExpiryGivesNoAlert() {
        assertNull(AlertRules.expiry(1, "Sisters", "", TODAY));
        assertNull(AlertRules.expiry(1, "Sisters", null, TODAY));
        assertNull(AlertRules.expiry(1, "Sisters", "26/12/2026", TODAY));
        assertNull(AlertRules.expiry(1, "Sisters", "2026-02-30", TODAY));
    }

    // ── Date maths ─────────────────────────────

    @Test
    public void daysAcrossMonthYearAndLeapDay() {
        assertEquals(1, AlertRules.daysBetween("2026-11-30", "2026-12-01"));
        assertEquals(1, AlertRules.daysBetween("2026-12-31", "2027-01-01"));
        assertEquals(2, AlertRules.daysBetween("2028-02-28", "2028-03-01"));   // 2028 is a leap year
        assertEquals(1, AlertRules.daysBetween("2027-02-28", "2027-03-01"));
        assertEquals(-3, AlertRules.daysBetween("2026-11-06", "2026-11-03"));
        assertEquals(365, AlertRules.daysBetween("2026-01-01", "2027-01-01"));
    }

    @Test
    public void showDateFormat() {
        assertEquals("26/12/2026", AlertRules.showDate("2026-12-26"));
    }

    // ── Refill confirmation ──

    @Test
    public void refillWhenStockGoesBackToCapacity() {
        AlertRules.Alert a = AlertRules.refill(3, "Kleenfant", 1, 7, 7);
        assertEquals(AlertRules.Level.REFILLED, a.level);
        assertEquals("Refilled — Tray 3", a.title);
        assertEquals("Kleenfant was refilled: 7 of 7 in the tray (was 1).", a.message);
        assertNotNull(AlertRules.refill(3, "Kleenfant", 0, 7, 7));
    }

    @Test
    public void noRefillAlertOtherwise() {
        assertNull(AlertRules.refill(3, "Kleenfant", null, 7, 7));   // first look: no history yet
        assertNull(AlertRules.refill(3, "Kleenfant", 7, 7, 7));      // was already full
        assertNull(AlertRules.refill(3, "Kleenfant", 2, 5, 7));      // topped up but not full
        assertNull(AlertRules.refill(3, "Kleenfant", 7, 6, 7));      // a sale, not a refill
        assertNull(AlertRules.refill(3, "Kleenfant", 2, null, 7));
    }
}
