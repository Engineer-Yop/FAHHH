package com.example.sanivend;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Turns Firebase sign-in errors into short messages a user understands. */
public final class AuthErrors {

    private AuthErrors() {}

    public static String message(Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Password is too weak. Use at least 6 characters.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists. Log in instead.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No account found for this email, or it was disabled.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Wrong email or password.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection. Check your connection and try again.";
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        return e != null && e.getMessage() != null ? e.getMessage() : "Something went wrong. Please try again.";
    }

    /** Basic email check (the app's own, so it also works in unit tests). */
    public static boolean looksLikeEmail(String email) {
        return email != null && email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }
}
