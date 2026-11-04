package com.example.sanivend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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

public class MainActivity extends BaseActivity {

    private EditText usernameInput; // This is actually email input now
    private EditText passwordInput;
    private TextView loginButton;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        usernameInput = findViewById(R.id.username_input); // Assuming this is used for email
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);

        // Set click listener for the login button
        loginButton.setOnClickListener(v -> {
            String email = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
            } else {
                performLogin(email, password);
            }
        });

        // Sign up text click listener - Navigates to SignUpActivity
        findViewById(R.id.signup_text).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SignUpActivity.class);
            startActivity(intent);
        });
    }

    private void performLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            if (user.isEmailVerified()) {
                                checkUserProfile(user.getUid());
                            } else {
                                Toast.makeText(MainActivity.this, "Please verify your email first", Toast.LENGTH_LONG).show();
                                mAuth.signOut();
                            }
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkUserProfile(String uid) {
        mDatabase.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Profile exists, go to Dashboard
                    Toast.makeText(MainActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    // No profile, redirect to Create Profile screen
                    Intent intent = new Intent(MainActivity.this, CreateProfileActivity.class);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
