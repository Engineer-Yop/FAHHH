package com.example.sanivend;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Account activation with a 6-digit email code.
 *
 * Firebase Realtime Database:
 *   /users/{uid}/verified          true once the code was entered (false right after sign-up)
 *   /verification/{uid}/hash       SHA-256 of "uid:code" (the code itself is never stored)
 *   /verification/{uid}/expires_at the code stops working after this (10 minutes)
 *   /verification/{uid}/sent_at    used for the 1-minute resend wait
 *   /verification/{uid}/tries      wrong attempts (5 allowed)
 *
 * Accounts verified earlier with Firebase's email link still count as verified.
 */
public final class AccountVerification {

    private static final String PREFS = "account_verification";

    public interface Done {
        /** @param error null on success, otherwise a message for the user */
        void done(String error);
    }

    public interface Checked {
        void result(boolean verified);
    }

    private AccountVerification() {}

    private static DatabaseReference root() {
        return FirebaseDatabase.getInstance().getReference();
    }

    /** Quick check without the network: verified by link, or by code on this phone before. */
    public static boolean isVerifiedHere(Context context, FirebaseUser user) {
        if (user == null) return false;
        if (user.isEmailVerified()) return true;
        return prefs(context).getBoolean(user.getUid(), false);
    }

    /** Full check (also asks Firebase, e.g. after signing in on a new phone). */
    public static void check(final Context context, final FirebaseUser user, final Checked callback) {
        if (isVerifiedHere(context, user)) {
            callback.result(true);
            return;
        }
        root().child("users").child(user.getUid()).child("verified").get()
                .addOnSuccessListener(s -> {
                    boolean ok = Boolean.TRUE.equals(s.getValue(Boolean.class));
                    if (ok) remember(context, user.getUid());
                    callback.result(ok);
                })
                .addOnFailureListener(e -> callback.result(false));
    }

    /**
     * Makes a new code, saves its hash and emails it.
     * Refused (with the time to wait) if the last code was sent less than a minute ago.
     */
    public static void sendCode(final FirebaseUser user, final Done done) {
        final String uid = user.getUid();
        final String email = user.getEmail();
        if (email == null) {
            done.done("This account has no email address.");
            return;
        }
        final DatabaseReference ref = root().child("verification").child(uid);
        ref.get().addOnSuccessListener(old -> {
            long sentAt = longOf(old.child("sent_at"));
            long wait = VerificationCodes.waitBeforeResend(sentAt, System.currentTimeMillis());
            if (wait > 0) {
                done.done("Please wait " + ((wait + 999) / 1000) + " s before asking for a new code.");
                return;
            }
            final String code = VerificationCodes.newCode();
            long now = System.currentTimeMillis();
            Map<String, Object> v = new HashMap<>();
            v.put("hash", VerificationCodes.hash(uid, code));
            v.put("expires_at", now + VerificationCodes.VALID_MS);
            v.put("sent_at", now);
            v.put("tries", 0);
            v.put("email", email);
            ref.setValue(v)
                    .addOnSuccessListener(x -> EmailCodeSender.send(email, code, done::done))
                    .addOnFailureListener(e -> done.done("Couldn't save the code: " + e.getMessage()));
        }).addOnFailureListener(e -> done.done("Couldn't reach the database: " + e.getMessage()));
    }

    /** Checks the typed code; on success activates the account. */
    public static void verify(final Context context, final FirebaseUser user, final String typed, final Done done) {
        final String uid = user.getUid();
        final DatabaseReference ref = root().child("verification").child(uid);
        ref.get().addOnSuccessListener(s -> {
            String hash = s.child("hash").getValue(String.class);
            long expiresAt = longOf(s.child("expires_at"));
            int tries = (int) longOf(s.child("tries"));
            VerificationCodes.Result r = VerificationCodes.check(uid, typed.trim(), hash, expiresAt,
                    tries, System.currentTimeMillis());
            switch (r) {
                case OK:
                    Map<String, Object> update = new HashMap<>();
                    update.put("users/" + uid + "/verified", true);
                    update.put("users/" + uid + "/verified_at", ServerValue.TIMESTAMP);
                    update.put("verification/" + uid, null);
                    root().updateChildren(update)
                            .addOnSuccessListener(x -> {
                                remember(context, uid);
                                done.done(null);
                            })
                            .addOnFailureListener(e -> done.done("Couldn't activate the account: " + e.getMessage()));
                    break;
                case WRONG:
                    ref.child("tries").setValue(ServerValue.increment(1));
                    int left = VerificationCodes.MAX_TRIES - tries - 1;
                    done.done(left > 0 ? "Wrong code. " + left + (left == 1 ? " try" : " tries") + " left."
                            : "Wrong code. Tap Resend to get a new one.");
                    break;
                case EXPIRED:
                    done.done("This code has expired. Tap Resend to get a new one.");
                    break;
                case TOO_MANY_TRIES:
                    done.done("Too many wrong tries. Tap Resend to get a new code.");
                    break;
                default:
                    done.done("No code was sent yet. Tap Resend.");
            }
        }).addOnFailureListener(e -> done.done("Couldn't reach the database: " + e.getMessage()));
    }

    /** Right after sign-up: the account starts as not verified. */
    public static void markNew(FirebaseUser user) {
        Map<String, Object> v = new HashMap<>();
        v.put("verified", false);
        v.put("email", user.getEmail());
        root().child("users").child(user.getUid()).updateChildren(v);
    }

    private static void remember(Context context, String uid) {
        prefs(context).edit().putBoolean(uid, true).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long longOf(DataSnapshot s) {
        Object v = s.getValue();
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }
}
