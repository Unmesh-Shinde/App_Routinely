package com.dailyroutine.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val etName = findViewById<EditText>(R.id.etProfileName)
        val etAge = findViewById<EditText>(R.id.etProfileAge)
        val etHeight = findViewById<EditText>(R.id.etProfileHeight)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)

        val genders = arrayOf("Male", "Female", "Other")
        spinnerGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        // Load existing data
        etName.setText(UserPreferencesStore.getUserName(this))
        etAge.setText(UserPreferencesStore.getUserAge(this).toString())
        etHeight.setText(UserPreferencesStore.getUserHeight(this).toString())
        spinnerGender.setSelection(genders.indexOf(UserPreferencesStore.getUserGender(this)).coerceAtLeast(0))

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val age = etAge.text.toString().toIntOrNull() ?: 25
            val height = etHeight.text.toString().toIntOrNull() ?: 170
            val gender = spinnerGender.selectedItem.toString()

            if (name.isEmpty()) {
                etName.error = "Name is required"
                return@setOnClickListener
            }

            UserPreferencesStore.setUserName(this, name)
            UserPreferencesStore.setUserAge(this, age)
            UserPreferencesStore.setUserHeight(this, height)
            UserPreferencesStore.setUserGender(this, gender)

            Toast.makeText(this, "Profile updated successfully! ✅", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
