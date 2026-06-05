package com.dailyroutine.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateGreeting()

        findViewById<MaterialCardView>(R.id.cardReminders).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardDiet).setOnClickListener {
            startActivity(Intent(this, DietPlanActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardWorkout).setOnClickListener {
            startActivity(Intent(this, WorkoutPlanActivity::class.java))
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning!"
            in 12..16 -> "Good Afternoon!"
            in 17..20 -> "Good Evening!"
            else -> "Good Night!"
        }
        
        val subGreeting = when (hour) {
            in 0..11 -> "Ready to take on the day?"
            in 12..16 -> "Keep up the great momentum!"
            in 17..20 -> "Time to wind down soon."
            else -> "Rest well for tomorrow."
        }

        findViewById<TextView>(R.id.tvGreeting).text = greeting
        findViewById<TextView>(R.id.tvSubGreeting).text = subGreeting
    }
}
