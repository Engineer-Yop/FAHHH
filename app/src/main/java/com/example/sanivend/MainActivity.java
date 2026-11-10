package com.example.sanivend;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Login (the app's start screen).
 *  - Email + password; only activated accounts get in.
 *  - Not activated yet (no 6-digit code entered) → the code screen, with a fresh code.
 *  - "Forgot password?" sends a reset link.
 *  - Stays signed in: next time the app opens straight to Sanivend Machines.
 *  Opened by the Welcome page, which the app shows first every time it starts.
 */
public class MainActivity extends BaseActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private TextView loginButton;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Already signed in → straight on (or to the code screen if not activated yet)
        final FirebaseUser current = mAuth.getCurrentUser();
        if (current != null) {
            AccountVerification.check(this, current, verified -> {
                if (isFinishing()) return;
                if (verified) checkUserProfile(current.getUid());
                else openVerification(false);
            });
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);

        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            if (!AuthErrors.looksLikeEmail(email)) {
                emailInput.setError("Enter a valid email address");
                return;
            }
            if (password.isEmpty()) {
                passwordInput.setError("Enter your password");
                return;
            }
            performLogin(email, password);
        });

        findViewById(R.id.signup_text).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SignUpActivity.class)));
        findViewById(R.id.forgot_password).setOnClickListener(v -> showForgotPassword());
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        loginButton.setText(busy ? "Logging in..." : getString(R.string.login_button_text));
    }

    private void performLogin(String email, String password) {
        setBusy(true);
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                setBusy(false);
                Toast.makeText(this, AuthErrors.message(task.getException()), Toast.LENGTH_LONG).show();
                return;
            }
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                setBusy(false);
                return;
            }
            AccountVerification.check(this, user, verified -> {
                if (isFinishing()) return;
                if (verified) {
                    checkUserProfile(user.getUid());
                } else {
                    setBusy(false);
                    openVerification(true);              // email a fresh 6-digit code
                }
            });
        });
    }

    private void openVerification(boolean sendNow) {
        startActivity(new Intent(this, VerificationActivity.class)
                .putExtra(VerificationActivity.EXTRA_SEND_NOW, sendNow));
        finish();
    }

    private void showForgotPassword() {
        final EditText input = new EditText(this);
        input.setHint("Your account email");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setText(emailInput.getText().toString().trim());
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, input.getPaddingTop(), pad, input.getPaddingBottom());

        new AlertDialog.Builder(this)
                .setTitle("Reset password")
                .setMessage("We'll email you a link to set a new password.")
                .setView(input)
                .setPositiveButton("Send link", (d, w) -> {
                    String email = input.getText().toString().trim();
                    if (!AuthErrors.looksLikeEmail(email)) {
                        Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_LONG).show();
                        return;
                    }
                    mAuth.sendPasswordResetEmail(email).addOnCompleteListener(t ->
                            Toast.makeText(this, t.isSuccessful()
                                            ? "Password reset link sent to " + email
                                            : AuthErrors.message(t.getException()),
                                    Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** First login → Create Profile (username). Otherwise → Sanivend Machines. */
    private void checkUserProfile(String uid) {
        final boolean[] done = {false};
        // If the database doesn't answer in 4 s (offline), go on to Sanivend Machines anyway
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> go(done, true), 4000);
        mDatabase.child("users").child(uid).child("username")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        go(done, snapshot.exists());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        go(done, true);
                    }
                });
    }

    private void go(boolean[] done, boolean hasProfile) {
        if (done[0] || isFinishing()) return;
        done[0] = true;
        startActivity(new Intent(this, hasProfile ? DeviceListActivity.class : CreateProfileActivity.class));
        finish();
    }
}
