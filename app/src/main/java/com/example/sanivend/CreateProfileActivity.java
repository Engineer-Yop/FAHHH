package com.example.sanivend;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;

public class CreateProfileActivity extends BaseActivity {

    private EditText usernameInput;
    private TextView saveProfileButton;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_profile);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        usernameInput = findViewById(R.id.username_input);
        saveProfileButton = findViewById(R.id.save_profile_button);

        saveProfileButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (username.length() < 3) {
                Toast.makeText(this, "Username too short", Toast.LENGTH_SHORT).show();
                return;
            }
            createNewProfile(username);
        });
    }

    private void createNewProfile(final String username) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        final String uid = user.getUid();
        final String email = user.getEmail();

        // Use a transaction on the "usernames" node to ensure uniqueness
        DatabaseReference usernameRef = mDatabase.child("usernames").child(username);

        usernameRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                if (mutableData.getValue() != null) {
                    // Username already exists
                    return Transaction.abort();
                }

                // Set the value of the username to the UID
                mutableData.setValue(uid);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (committed) {
                    // Username successfully reserved, now create the user profile
                    Map<String, Object> userProfile = new HashMap<>();
                    userProfile.put("username", username);
                    userProfile.put("email", email);

                    mDatabase.child("users").child(uid).setValue(userProfile)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(CreateProfileActivity.this, "Profile Created!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(CreateProfileActivity.this, DashboardActivity.class);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    // Cleanup reserved username if profile creation fails
                                    mDatabase.child("usernames").child(username).removeValue();
                                    Toast.makeText(CreateProfileActivity.this, "Failed to create profile", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    if (error != null) {
                        Toast.makeText(CreateProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CreateProfileActivity.this, "Username already taken, please choose another.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }
}
