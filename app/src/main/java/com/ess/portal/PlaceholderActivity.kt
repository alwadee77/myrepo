package com.ess.portal

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PlaceholderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)

        val title = intent.getStringExtra("title") ?: "Coming Soon"
        val message = intent.getStringExtra("message") ?: "This feature is under development"

        setSupportActionBar(findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar))
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tv_placeholder_title).text = title
        findViewById<TextView>(R.id.tv_placeholder_message).text = message
    }
}
