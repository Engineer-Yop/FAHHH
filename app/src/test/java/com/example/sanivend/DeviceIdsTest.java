package com.example.sanivend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Machine ID, pairing code, ownership and online-status rules used by My Machines. */
public class DeviceIdsTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long MIN = 60_000L;

    @Test
    public void acceptsTheWaysPeopleTypeAnId() {
        assertEquals("SV-A1B2C3", DeviceIds.normalize("SV-A1B2C3"));
        assertEquals("SV-A1B2C3", DeviceIds.normalize("sv-a1b2c3"));
        assertEquals("SV-A1B2C3", DeviceIds.normalize("  A1B2C3 "));
        assertEquals("SV-A1B2C3", DeviceIds.normalize("SV A1B2C3"));
        assertEquals("SV-A1B2C3", DeviceIds.normalize("sva1b2c3"));
        assertEquals("SV-00FF09", DeviceIds.normalize("00ff09"));
    }

    @Test
    public void rejectsBadIds() {
        assertNull(DeviceIds.normalize(null));
        assertNull(DeviceIds.normalize(""));
        assertNull(DeviceIds.normalize("SV-A1B2C"));     // too short
        assertNull(DeviceIds.normalize("SV-A1B2C3D"));   // too long
        assertNull(DeviceIds.normalize("SV-G1B2C3"));    // G is not hex
        assertNull(DeviceIds.normalize("XX-A1B2C3"));
    }

    @Test
    public void pairingCodeIsSixDigits() {
        assertTrue(DeviceIds.isValidCode("123456"));
        assertTrue(DeviceIds.isValidCode("012345"));
        assertTrue(DeviceIds.isValidCode(" 012345 "));
        assertFalse(DeviceIds.isValidCode("12345"));
        assertFalse(DeviceIds.isValidCode("1234567"));
        assertFalse(DeviceIds.isValidCode("12a456"));
        assertFalse(DeviceIds.isValidCode(null));
    }

    @Test
    public void codeMustMatchWhatTheMachineSaved() {
        assertTrue(DeviceIds.codeMatches("012345", "012345"));
        assertTrue(DeviceIds.codeMatches(" 012345", "012345 "));
        assertFalse(DeviceIds.codeMatches("012345", "012346"));
        assertFalse(DeviceIds.codeMatches("012345", null));
        assertFalse(DeviceIds.codeMatches("12345", "12345"));   // stored value itself invalid length
    }

    @Test
    public void onlyAnotherOwnerBlocksPairing() {
        assertTrue(DeviceIds.canClaim(null, "me"));
        assertTrue(DeviceIds.canClaim("", "me"));
        assertTrue(DeviceIds.canClaim("me", "me"));      // adding my own machine again
        assertFalse(DeviceIds.canClaim("someoneElse", "me"));
    }

    @Test
    public void onlineWithinThreeMinutes() {
        assertTrue(DeviceIds.isOnline(NOW - 30_000, NOW));
        assertTrue(DeviceIds.isOnline(NOW - 3 * MIN, NOW));
        assertFalse(DeviceIds.isOnline(NOW - 3 * MIN - 1, NOW));
        assertFalse(DeviceIds.isOnline(0, NOW));
    }

    @Test
    public void statusLabels() {
        assertEquals("Online", DeviceIds.statusLabel(NOW - MIN, NOW));
        assertEquals("Offline · last seen 12 min ago", DeviceIds.statusLabel(NOW - 12 * MIN, NOW));
        assertEquals("Offline · last seen 3 h ago", DeviceIds.statusLabel(NOW - 185 * MIN, NOW));
        assertEquals("Offline · last seen 1 day ago", DeviceIds.statusLabel(NOW - 25 * 60 * MIN, NOW));
        assertEquals("Offline · last seen 4 days ago", DeviceIds.statusLabel(NOW - 4 * 24 * 60 * MIN, NOW));
        assertEquals("Offline · not connected yet", DeviceIds.statusLabel(0, NOW));
    }

    @Test
    public void emailCheck() {
        assertTrue(AuthErrors.looksLikeEmail("geof@gmail.com"));
        assertTrue(AuthErrors.looksLikeEmail(" geof.f@school.edu.ph "));
        assertFalse(AuthErrors.looksLikeEmail("geof@gmail"));
        assertFalse(AuthErrors.looksLikeEmail("geof gmail.com"));
        assertFalse(AuthErrors.looksLikeEmail(""));
        assertFalse(AuthErrors.looksLikeEmail(null));
    }
}
