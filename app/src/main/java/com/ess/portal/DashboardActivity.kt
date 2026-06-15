package com.ess.portal

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        findViewById<android.view.View>(R.id.btn_attendance).setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btn_profile).setOnClickListener {
            Toast.makeText(this, "Profile coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_time_off).setOnClickListener {
            Toast.makeText(this, "Time Off coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_overtime).setOnClickListener {
            Toast.makeText(this, "Overtime coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_hr_requests).setOnClickListener {
            Toast.makeText(this, "HR Requests coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_expenses).setOnClickListener {
            Toast.makeText(this, "Expenses coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_contract).setOnClickListener {
            Toast.makeText(this, "Contract & Salary coming soon", Toast.LENGTH_SHORT).show()
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
                        put("fields", JSONArray(listOf("id", "name", "job_title", "department_id")))
                    }
                )
                val employees = empResult as? JSONArray

                withContext(Dispatchers.Main) {
                    if (employees != null && employees.length() > 0) {
                        val emp = employees.getJSONObject(0)
                        OdooRpcClient.setEmployeeId(emp.optInt("id", 0))
                        val name = emp.optString("name", "Employee")
                        findViewById<TextView>(R.id.tv_employee_name).text = name
                        findViewById<TextView>(R.id.tv_employee_name_detail).text = name
                        findViewById<TextView>(R.id.tv_greeting).text = "Welcome back, $name!"
                        val job = emp.optString("job_title", "")
                        findViewById<TextView>(R.id.tv_employee_job).text = if (job.isNotEmpty()) job else "Employee"
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
                        put("fields", JSONArray(listOf("id", "check_in", "check_out", "worked_hours")))
                    }
                )
                val attendances = attResult as? JSONArray

                withContext(Dispatchers.Main) {
                    if (attendances != null && attendances.length() > 0) {
                        val last = attendances.getJSONObject(attendances.length() - 1)
                        val checkOut = last.opt("check_out")
                        val badge = findViewById<TextView>(R.id.tv_attendance_status_badge)

                        if (checkOut == JSONObject.NULL) {
                            badge.visibility = android.view.View.VISIBLE
                            badge.text = "IN"
                            badge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.success))
                            badge.setBackgroundResource(R.drawable.bg_icon_green)
                            findViewById<TextView>(R.id.tv_today_status).text = "Clocked In"
                            findViewById<TextView>(R.id.tv_today_status).setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.success))
                        } else {
                            badge.visibility = android.view.View.VISIBLE
                            badge.text = "OUT"
                            badge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.gray))
                            badge.setBackgroundResource(R.drawable.bg_icon_red_light)
                            findViewById<TextView>(R.id.tv_today_status).text = "Completed"
                            findViewById<TextView>(R.id.tv_today_status).setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.gray))
                        }

                        val totalSeconds = calculateWorkedSeconds(attendances)
                        val hours = totalSeconds / 3600
                        val mins = (totalSeconds % 3600) / 60
                        findViewById<TextView>(R.id.tv_worked_hours).text = "${hours}h ${mins}m"

                        findViewById<TextView>(R.id.tv_attendance_count).text = "${if (checkOut == JSONObject.NULL) 1 else 0}"
                    } else {
                        findViewById<TextView>(R.id.tv_today_status).text = "Not clocked in"
                        findViewById<TextView>(R.id.tv_today_status).setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.error))
                        findViewById<TextView>(R.id.tv_worked_hours).text = "0h 0m"
                        findViewById<TextView>(R.id.tv_attendance_count).text = "0"
                    }
                }

                val leaveResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid)),
                            JSONArray(listOf("state", "in", listOf("confirm", "validate", "validate1")))
                        )))
                        put("fields", JSONArray(listOf("id")))
                    }
                )
                val leaves = leaveResult as? JSONArray
                val pendingCount = leaves?.length() ?: 0

                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tv_pending).text = "$pendingCount"
                    findViewById<TextView>(R.id.tv_hr_requests_count).text = "$pendingCount"
                }

                val typesResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave.type", "search_read",
                    kwargs = JSONObject().apply {
                        put("fields", JSONArray(listOf("id", "name", "virtual_remaining_leaves")))
                    }
                )
                val types = typesResult as? JSONArray
                var totalBalance = 0.0
                if (types != null) {
                    for (i in 0 until types.length()) {
                        totalBalance += types.getJSONObject(i).optDouble("virtual_remaining_leaves", 0.0)
                    }
                }

                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tv_leave_days).text = String.format("%.1f", totalBalance)
                    findViewById<TextView>(R.id.tv_time_off_count).text = "$pendingCount"
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
