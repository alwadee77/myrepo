package com.ess.portal

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ProfileActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefs = AppPreferences(this)

        setSupportActionBar(findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        loadProfile()
    }

    private fun loadProfile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val uid = OdooRpcClient.getSession()?.uid ?: return@launch

                val result = OdooRpcClient.callKw(
                    baseUrl, db, "hr.employee", "search_read",
                    kwargs = org.json.JSONObject().apply {
                        put("domain", JSONArray(listOf(JSONArray(listOf("user_id", "=", uid)))))
                        put("fields", JSONArray(listOf(
                            "id", "name", "work_email", "work_phone", "mobile_phone",
                            "emergency_contact", "emergency_phone",
                            "job_id", "department_id", "parent_id", "work_location_id",
                            "registration_number", "pin"
                        )))
                    }
                )
                val employees = result as? JSONArray

                withContext(Dispatchers.Main) {
                    if (employees != null && employees.length() > 0) {
                        val emp = employees.getJSONObject(0)

                        findViewById<TextView>(R.id.tv_name).text = emp.optString("name", "-")
                        findViewById<TextView>(R.id.tv_job).text = formatM2o(emp.opt("job_id"), "name")
                        findViewById<TextView>(R.id.tv_department).text = formatM2o(emp.opt("department_id"), "name")

                        findViewById<TextView>(R.id.tv_work_email).text = emp.optString("work_email", "-")
                        findViewById<TextView>(R.id.tv_work_phone).text = emp.optString("work_phone", "-")
                        findViewById<TextView>(R.id.tv_mobile_phone).text = emp.optString("mobile_phone", "-")
                        findViewById<TextView>(R.id.tv_emergency_contact).text = emp.optString("emergency_contact", "-")
                        findViewById<TextView>(R.id.tv_emergency_phone).text = emp.optString("emergency_phone", "-")

                        findViewById<TextView>(R.id.tv_manager).text = formatM2o(emp.opt("parent_id"), "name")
                        findViewById<TextView>(R.id.tv_work_location).text = formatM2o(emp.opt("work_location_id"), "name")
                        findViewById<TextView>(R.id.tv_registration_number).text = emp.optString("registration_number", "-")
                        findViewById<TextView>(R.id.tv_pin).text = emp.optString("pin", "-")

                    } else {
                        Toast.makeText(this@ProfileActivity, "Employee data not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatM2o(value: Any?, field: String): String {
        if (value is JSONArray && value.length() >= 2) {
            return value.optString(1, "-")
        }
        return "-"
    }
}
