package com.example.sanivend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * The 6-digit email code that activates a new administrator account (Appendix XIII).
 * Plain Java, covered by VerificationCodesTest.
 *
 *   - 6 random digits from SecureRandom (000000–999999)
 *   - only a SHA-256 hash of (account ID + code) is saved in Firebase, never the code itself
 *   - valid for 10 minutes, at most 5 wrong tries, a new code at most once a minute
 */
public final class VerificationCodes {

    public static final int LENGTH = 6;
    public static final long VALID_MS = 10 * 60 * 1000L;
    public static final long RESEND_AFTER_MS = 60 * 1000L;
    public static final int MAX_TRIES = 5;

    public enum Result { OK, WRONG, EXPIRED, TOO_MANY_TRIES, NO_CODE }

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodes() {}

    /** A new random code, always 6 digits ("004213" keeps its zeros). */
    public static String newCode() {
        return String.format(java.util.Locale.US, "%06d", RANDOM.nextInt(1_000_000));
    }

    /** What is stored instead of the code: SHA-256 of "uid:code", in hex. */
    public static String hash(String uid, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((uid + ":" + code).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) hex.append(String.format(java.util.Locale.US, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** True for exactly 6 digits. */
    public static boolean looksValid(String code) {
        return code != null && code.matches("\\d{" + LENGTH + "}");
    }

    /**
     * Checks a typed code against what was saved.
     * @param savedHash  hash saved when the code was sent (null if none)
     * @param expiresAt  when the code stops working (ms)
     * @param tries      wrong tries so far
     */
    public static Result check(String uid, String typed, String savedHash, long expiresAt,
                               int tries, long now) {
        if (savedHash == null || savedHash.isEmpty()) return Result.NO_CODE;
        if (tries >= MAX_TRIES) return Result.TOO_MANY_TRIES;
        if (now > expiresAt) return Result.EXPIRED;
        if (!looksValid(typed)) return Result.WRONG;
        return MessageDigest.isEqual(hash(uid, typed).getBytes(StandardCharsets.US_ASCII),
                savedHash.getBytes(StandardCharsets.US_ASCII)) ? Result.OK : Result.WRONG;
    }

    /** Milliseconds until a new code may be sent (0 = now). */
    public static long waitBeforeResend(long sentAt, long now) {
        return Math.max(0, sentAt + RESEND_AFTER_MS - now);
    }
}
