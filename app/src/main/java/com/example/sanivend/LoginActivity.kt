package com.example.sanivend

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var btnSendLink: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var signupText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        emailInput = findViewById(R.id.emailInput)
        btnSendLink = findViewById(R.id.btnSendLink)
        progressBar = findViewById(R.id.progressBar)
        signupText = findViewById(R.id.signup_text)

        // Navigate to Sign Up page
        signupText.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        btnSendLink.setOnClickListener {
            val enteredEmail = emailInput.text.toString().trim()

            if (enteredEmail.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(enteredEmail).matches()) {
                progressBar.visibility = View.VISIBLE
                btnSendLink.isEnabled = false

                // Configure for passwordless email link sign-in
                val actionCodeSettings = ActionCodeSettings.newBuilder()
                    .setUrl("https://vending-machine-g7.firebaseapp.com/verify")
                    .setHandleCodeInApp(true)
                    .setAndroidPackageName(packageName, true, null)
                    .build()

                auth.sendSignInLinkToEmail(enteredEmail, actionCodeSettings)
                    .addOnCompleteListener { task ->
                        progressBar.visibility = View.GONE
                        btnSendLink.isEnabled = true

                        if (task.isSuccessful) {
                            Toast.makeText(this, "Verification link sent to $enteredEmail", Toast.LENGTH_LONG).show()
                            val sharedPref = getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)
                            sharedPref.edit().putString("user_email", enteredEmail).apply()
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                emailInput.error = "Please enter a valid email address"
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intentData = intent
        val deepLink = intentData?.data?.toString()

        if (deepLink != null && auth.isSignInWithEmailLink(deepLink)) {
            progressBar.visibility = View.VISIBLE
            val sharedPref = getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)
            val savedEmail = sharedPref.getString("user_email", "") ?: ""

            if (savedEmail.isNotEmpty()) {
                auth.signInWithEmailLink(savedEmail, deepLink)
                    .addOnCompleteListener { task ->
                        progressBar.visibility = View.GONE
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Authenticated successfully!", Toast.LENGTH_SHORT).show()
                            sharedPref.edit().remove("user_email").apply()
                            
                            val mainIntent = Intent(this, DashboardActivity::class.java)
                            startActivity(mainIntent)
                            finish()
                        } else {
                            Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: Stored email missing. Please click the link on the same device.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
