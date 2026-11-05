package com.example.sanivend;

import androidx.appcompat.app.AlertDialog;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignUpActivity extends BaseActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmInput;
    private TextView signupButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        confirmInput = findViewById(R.id.confirm_password_input);
        signupButton = findViewById(R.id.signup_button);

        signupButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            String confirm = confirmInput.getText().toString();

            if (!AuthErrors.looksLikeEmail(email)) {
                emailInput.setError("Enter a valid email address");
            } else if (password.length() < 6) {
                passwordInput.setError("Use at least 6 characters");
            } else if (!password.equals(confirm)) {
                confirmInput.setError("Passwords don't match");
            } else {
                performSignUp(email, password);
            }
        });
    }

    private void setBusy(boolean busy) {
        signupButton.setEnabled(!busy);
        signupButton.setText(busy ? "Creating account..." : "Sign up");
    }

    /** Creates the account, sends the verification email, then returns to Login. */
    private void performSignUp(String email, String password) {
        setBusy(true);
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
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
            user.sendEmailVerification().addOnCompleteListener(verifyTask -> {
                mAuth.signOut();
                setBusy(false);
                new AlertDialog.Builder(this)
                        .setTitle(verifyTask.isSuccessful() ? "Check your email" : "Account created")
                        .setMessage(verifyTask.isSuccessful()
                                ? "We sent a verification link to " + email + ". Open it (check Spam too), "
                                  + "then come back and log in."
                                : "Your account was created, but the verification email couldn't be sent ("
                                  + AuthErrors.message(verifyTask.getException()) + "). Log in to get a new link.")
                        .setPositiveButton("Go to login", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            });
        });
    }
}
