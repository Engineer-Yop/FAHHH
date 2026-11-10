package com.example.sanivend;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Enter the 6-digit code sent to the administrator's email (Appendix XIII).
 * Opened after sign-up, or after logging in to an account that isn't activated yet.
 * The right code activates the account and opens the app; "Resend code" sends a new one
 * (once a minute); "Use a different account" signs out.
 */
public class VerificationActivity extends AppCompatActivity {

    /** true → send a fresh code as soon as the screen opens. */
    public static final String EXTRA_SEND_NOW = "send_now";

    private FirebaseUser user;
    private EditText codeInput;
    private TextView verifyButton, resendButton, status;
    private CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            openApp();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_verification);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ((TextView) findViewById(R.id.verification_message)).setText(
                "We sent a 6-digit code to\n" + user.getEmail() + "\nEnter it below to activate your account."
                        + " It works for " + (VerificationCodes.VALID_MS / 60000) + " minutes.");
        codeInput = findViewById(R.id.code_input);
        verifyButton = findViewById(R.id.btnVerify);
        resendButton = findViewById(R.id.btnResend);
        status = findViewById(R.id.verification_status);

        verifyButton.setOnClickListener(v -> verify());
        resendButton.setOnClickListener(v -> sendCode());
        findViewById(R.id.btnOtherAccount).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            openApp();
        });
        codeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (VerificationCodes.looksValid(s.toString())) verify();   // 6 digits typed → check at once
            }
        });

        if (getIntent().getBooleanExtra(EXTRA_SEND_NOW, false) && savedInstanceState == null) {
            sendCode();
        }
    }

    private void sendCode() {
        resendButton.setEnabled(false);
        show("Sending a code to " + user.getEmail() + "…", false);
        AccountVerification.sendCode(user, error -> {
            if (isFinishing()) return;
            if (error == null) {
                show("Code sent. Check your inbox (and Spam).", false);
                startResendWait();
            } else {
                show(error, true);
                resendButton.setEnabled(true);
            }
        });
    }

    private void verify() {
        String typed = codeInput.getText().toString().trim();
        if (!VerificationCodes.looksValid(typed)) {
            codeInput.setError("Enter the 6 digits from the email");
            return;
        }
        verifyButton.setEnabled(false);
        show("Checking…", false);
        AccountVerification.verify(this, user, typed, error -> {
            if (isFinishing()) return;
            verifyButton.setEnabled(true);
            if (error == null) {
                show("Account activated!", false);
                openApp();
            } else {
                show(error, true);
                codeInput.setText("");
            }
        });
    }

    /** "Resend code (45 s)" until another code may be sent. */
    private void startResendWait() {
        if (resendTimer != null) resendTimer.cancel();
        resendTimer = new CountDownTimer(VerificationCodes.RESEND_AFTER_MS, 1000) {
            @Override
            public void onTick(long left) {
                resendButton.setText("Resend code (" + ((left + 999) / 1000) + " s)");
            }

            @Override
            public void onFinish() {
                resendButton.setText("Resend code");
                resendButton.setEnabled(true);
            }
        }.start();
    }

    private void show(String text, boolean isError) {
        status.setText(text);
        status.setTextColor(isError ? 0xFFC62828 : ContextCompat.getColor(this, R.color.text_primary));
    }

    /** Back to the start: Login routes a verified account on to Create Profile or Sanivend Machines. */
    private void openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) resendTimer.cancel();
    }
}
