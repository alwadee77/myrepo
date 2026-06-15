package com.ess.portal

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        prefs = AppPreferences(this)

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnSettings = findViewById<TextView>(R.id.btn_settings)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)

            progressBar.visibility = android.view.View.VISIBLE
            btnLogin.isEnabled = false
            btnLogin.text = "Signing in..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = OdooApi.authenticate(
                        baseUrl = prefs.getUrl(),
                        db = prefs.getDb(),
                        login = username,
                        password = password
                    )

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = android.view.View.GONE
                        btnLogin.isEnabled = true
                        btnLogin.text = "Sign In"

                        if (result != null) {
                            prefs.setLoggedIn(true)
                            startActivity(
                                Intent(this@LoginActivity, MainActivity::class.java)
                            )
                            finish()
                        } else {
                            Toast.makeText(
                                this@LoginActivity,
                                "Login failed. Check your credentials.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = android.view.View.GONE
                        btnLogin.isEnabled = true
                        btnLogin.text = "Sign In"
                        Toast.makeText(
                            this@LoginActivity,
                            "Connection error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
