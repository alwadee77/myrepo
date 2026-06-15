package com.ess.portal

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        prefs = AppPreferences(this)

        setSupportActionBar(findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        setupClickListeners()
        loadDashboard()
    }

    private fun setupClickListeners() {
        findViewById<android.view.View>(R.id.btn_check_in).setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btn_request_leave).setOnClickListener {
            Toast.makeText(this, "Time Off coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_profile).setOnClickListener {
            Toast.makeText(this, "Profile coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDashboard() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val uid = OdooRpcClient.getSession()?.uid ?: return@launch

                val empResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.employee", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(JSONArray(listOf("user_id", "=", uid)))))
                        put("fields", JSONArray(listOf("id", "name", "job_title")))
                    }
                )
                val employees = (empResult as? JSONObject)?.optJSONArray("records")

                withContext(Dispatchers.Main) {
                    if (employees != null && employees.length() > 0) {
                        val emp = employees.getJSONObject(0)
                        OdooRpcClient.setEmployeeId(emp.optInt("id", 0))
                        findViewById<TextView>(R.id.tv_employee_name).text = emp.optString("name", "Employee")
                    }
                }

                val today = getTodayDate()
                val attResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.attendance", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid)),
                            JSONArray(listOf("check_in", ">=", "$today 00:00:00"))
                        )))
                        put("fields", JSONArray(listOf("id", "check_in", "check_out")))
                    }
                )
                val attendances = (attResult as? JSONObject)?.optJSONArray("records")

                withContext(Dispatchers.Main) {
                    if (attendances != null && attendances.length() > 0) {
                        val last = attendances.getJSONObject(attendances.length() - 1)
                        val checkIn = last.optString("check_in", "")
                        val checkOut = last.opt("check_out")
                        val time = if (checkIn.length >= 16) checkIn.substring(11, 16) else "--:--"

                        if (checkOut == JSONObject.NULL) {
                            findViewById<TextView>(R.id.tv_check_label).text = "Check Out"
                            findViewById<TextView>(R.id.tv_check_status).text = time
                            findViewById<TextView>(R.id.tv_today_status).text = "Clocked In"
                            findViewById<TextView>(R.id.tv_today_status).setTextColor(0xFF16A34A.toInt())
                        } else {
                            findViewById<TextView>(R.id.tv_check_label).text = "Check In"
                            findViewById<TextView>(R.id.tv_check_status).text = "Done"
                            findViewById<TextView>(R.id.tv_today_status).text = "Completed"
                            findViewById<TextView>(R.id.tv_today_status).setTextColor(0xFF666666.toInt())
                        }

                        val totalSeconds = calculateWorkedSeconds(attendances)
                        val hours = totalSeconds / 3600
                        val mins = (totalSeconds % 3600) / 60
                        findViewById<TextView>(R.id.tv_worked_hours).text = "${hours}h ${mins}m"
                    } else {
                        findViewById<TextView>(R.id.tv_check_status).text = "Not yet"
                        findViewById<TextView>(R.id.tv_today_status).text = "Not clocked in"
                        findViewById<TextView>(R.id.tv_today_status).setTextColor(0xFFDC2626.toInt())
                        findViewById<TextView>(R.id.tv_worked_hours).text = "0h 0m"
                    }
                }

                val leaveResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid)),
                            JSONArray(listOf("state", "=", "draft"))
                        )))
                        put("fields", JSONArray(listOf("id")))
                    }
                )
                val leaves = (leaveResult as? JSONObject)?.optJSONArray("records")

                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tv_pending).text = "${leaves?.length() ?: 0}"
                }

                val typesResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave.type", "search_read",
                    kwargs = JSONObject().apply {
                        put("fields", JSONArray(listOf("id", "name", "virtual_remaining_leaves")))
                    }
                )
                val types = (typesResult as? JSONObject)?.optJSONArray("records")
                var totalBalance = 0.0
                if (types != null) {
                    for (i in 0 until types.length()) {
                        totalBalance += types.getJSONObject(i).optDouble("virtual_remaining_leaves", 0.0)
                    }
                }
                val displayBalance = totalBalance
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tv_leave_days).text = String.format("%.1f", displayBalance)
                    findViewById<TextView>(R.id.tv_leave_balance).text = "${String.format("%.1f", displayBalance)} days"
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getTodayDate(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}-${String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))}"
    }

    private fun calculateWorkedSeconds(attendances: JSONArray): Long {
        var total = 0L
        for (i in 0 until attendances.length()) {
            val a = attendances.getJSONObject(i)
            val checkIn = a.optString("check_in", "")
            val checkOut = a.opt("check_out")
            if (checkOut != JSONObject.NULL && checkIn.isNotEmpty()) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    val inTime = sdf.parse(checkIn)?.time ?: 0L
                    val outTime = sdf.parse(checkOut.toString())?.time ?: 0L
                    total += (outTime - inTime) / 1000
                } catch (_: Exception) {}
            }
        }
        return total
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadDashboard()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_logout -> {
                showLogoutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                OdooRpcClient.setSession(null)
                prefs.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }
}
