package com.example.sanivend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The 6-digit account activation code. */
public class VerificationCodesTest {

    private static final String UID = "abc123";
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void codesAreSixDigits() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String c = VerificationCodes.newCode();
            assertTrue(c, VerificationCodes.looksValid(c));
            seen.add(c);
        }
        assertTrue(seen.size() > 490);                 // random, not repeating
    }

    @Test
    public void onlyTheHashIsStored() {
        String h = VerificationCodes.hash(UID, "123456");
        assertEquals(64, h.length());
        assertFalse(h.contains("123456"));
        assertNotEquals(h, VerificationCodes.hash("other", "123456"));   // tied to the account
    }

    @Test
    public void checking() {
        String h = VerificationCodes.hash(UID, "042137");
        long exp = NOW + VerificationCodes.VALID_MS;
        assertEquals(VerificationCodes.Result.OK, VerificationCodes.check(UID, "042137", h, exp, 0, NOW));
        assertEquals(VerificationCodes.Result.WRONG, VerificationCodes.check(UID, "042138", h, exp, 0, NOW));
        assertEquals(VerificationCodes.Result.WRONG, VerificationCodes.check(UID, "42137", h, exp, 0, NOW));
        assertEquals(VerificationCodes.Result.EXPIRED, VerificationCodes.check(UID, "042137", h, exp, 0, exp + 1));
        assertEquals(VerificationCodes.Result.TOO_MANY_TRIES, VerificationCodes.check(UID, "042137", h, exp, 5, NOW));
        assertEquals(VerificationCodes.Result.NO_CODE, VerificationCodes.check(UID, "042137", null, exp, 0, NOW));
    }

    @Test
    public void resendWait() {
        assertEquals(60_000, VerificationCodes.waitBeforeResend(NOW, NOW));
        assertEquals(0, VerificationCodes.waitBeforeResend(NOW - 61_000, NOW));
        assertEquals(0, VerificationCodes.waitBeforeResend(0, NOW));    // never sent
    }
}
