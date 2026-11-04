package com.example.sanivend;

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
        signupButton = findViewById(R.id.signup_button);

        signupButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(SignUpActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            } else {
                performSignUp(email, password);
            }
        });
    }

    private void performSignUp(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification()
                                    .addOnCompleteListener(verifyTask -> {
                                        if (verifyTask.isSuccessful()) {
                                            Toast.makeText(SignUpActivity.this,
                                                    "Verification email sent. Please verify your email link before logging in.",
                                                    Toast.LENGTH_LONG).show();
                                            mAuth.signOut();
                                            finish();
                                        } else {
                                            Toast.makeText(SignUpActivity.this,
                                                    "Error: " + verifyTask.getException().getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(SignUpActivity.this,
                                "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
