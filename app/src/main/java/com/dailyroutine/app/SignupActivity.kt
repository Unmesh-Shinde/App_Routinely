package com.dailyroutine.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SignupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val etName = findViewById<EditText>(R.id.etSignupName)
        val btnStart = findViewById<Button>(R.id.btnStartJourney)

        btnStart.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Please enter your name"
                return@setOnClickListener
            }

            UserPreferencesStore.setUserName(this, name)
            UserPreferencesStore.setSignedUp(this, true)
            
            // Mark as needing permission check on next Main launch
            val healthPrefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
            healthPrefs.edit().putBoolean("needs_initial_permission_request", true).apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
