package com.ess.portal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = AppPreferences(this)
            val intent = if (!prefs.isConfigured()) {
                Intent(this, SettingsActivity::class.java)
            } else if (!prefs.isLoggedIn()) {
                Intent(this, LoginActivity::class.java)
            } else {
                Intent(this, DashboardActivity::class.java)
            }
            startActivity(intent)
            finish()
        }, 1500)
    }
}
