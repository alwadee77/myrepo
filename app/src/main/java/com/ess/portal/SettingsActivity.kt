package com.ess.portal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = AppPreferences(this)

        val etUrl = findViewById<EditText>(R.id.et_url)
        val etDb = findViewById<EditText>(R.id.et_db)
        val btnSave = findViewById<Button>(R.id.btn_save)

        etUrl.setText(prefs.getUrl())
        etDb.setText(prefs.getDb())

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val db = etDb.text.toString().trim()

            if (url.isEmpty() || db.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.setUrl(url.trimEnd('/'))
            prefs.setDb(db)
            prefs.setLoggedIn(false)

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
