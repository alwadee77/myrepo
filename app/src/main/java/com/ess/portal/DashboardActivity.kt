package com.ess.portal

import android.content.Context
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

    override fun attachBaseContext(newBase: Context) {
        val p = AppPreferences(newBase)
        super.attachBaseContext(LocaleUtil.applyLocale(newBase, p.getLang()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = AppPreferences(this)

        if (OdooRpcClient.getSession() == null && prefs.isLoggedIn()) {
            OdooRpcClient.setSession(OdooRpcClient.Session(prefs.getUid(), prefs.getSessionId(), prefs.getLang()))
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

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
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btn_time_off).setOnClickListener {
            startActivity(Intent(this, TimeOffActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btn_overtime).setOnClickListener {
            startActivity(Intent(this, PlaceholderActivity::class.java).apply {
                putExtra("title", "Overtime")
                putExtra("message", "Overtime requests will be displayed here.")
            })
        }
        findViewById<android.view.View>(R.id.btn_hr_requests).setOnClickListener {
            startActivity(Intent(this, PlaceholderActivity::class.java).apply {
                putExtra("title", "HR Requests")
                putExtra("message", "HR requests for letters, assets, visa, etc. will appear here.")
            })
        }
        findViewById<android.view.View>(R.id.btn_expenses).setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btn_contract).setOnClickListener {
            startActivity(Intent(this, PlaceholderActivity::class.java).apply {
                putExtra("title", "Contract & Salary")
                putExtra("message", "Your contract details and salary information will be displayed here.")
            })
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
                        val name = emp.optString("name", getString(R.string.employee_default))
                        findViewById<TextView>(R.id.tv_employee_name_detail).text = name
                        findViewById<TextView>(R.id.tv_greeting).text = getString(R.string.greeting_format, name)
                        val job = emp.optString("job_title", "")
                        findViewById<TextView>(R.id.tv_employee_job).text = if (job.isNotEmpty()) job else getString(R.string.employee_default)
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
                            badge.text = getString(R.string.badge_in)
                            badge.setTextColor(android.graphics.Color.WHITE)
                            badge.setBackgroundResource(R.drawable.bg_badge_green)
                            findViewById<TextView>(R.id.tv_today_status).text = getString(R.string.status_clocked_in)
                            findViewById<TextView>(R.id.tv_today_status).setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.success))
                        } else {
                            badge.visibility = android.view.View.VISIBLE
                            badge.text = getString(R.string.badge_out)
                            badge.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.error))
                            badge.setBackgroundResource(R.drawable.bg_icon_red_light)
                            findViewById<TextView>(R.id.tv_today_status).text = getString(R.string.status_completed)
                            findViewById<TextView>(R.id.tv_today_status).setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.on_surface))
                        }

                        val totalSeconds = calculateWorkedSeconds(attendances)
                        val hours = totalSeconds / 3600
                        val mins = (totalSeconds % 3600) / 60
                        findViewById<TextView>(R.id.tv_worked_hours).text = getString(R.string.hours_format, hours, mins)

                        findViewById<TextView>(R.id.tv_attendance_count).text = "${attendances.length()}"
                    } else {
                        findViewById<TextView>(R.id.tv_today_status).text = getString(R.string.status_not_clocked_in)
                        findViewById<TextView>(R.id.tv_today_status).setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.error))
                        findViewById<TextView>(R.id.tv_worked_hours).text = getString(R.string.hours_zero)
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

                val overtimeResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.attendance.overtime", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid)),
                            JSONArray(listOf("date", "=", getTodayDate()))
                        )))
                        put("fields", JSONArray(listOf("id")))
                    }
                )
                val overtimeCount = (overtimeResult as? JSONArray)?.length() ?: 0

                val expenseResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.expense", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid)),
                            JSONArray(listOf("state", "in", listOf("draft", "submit", "approve")))
                        )))
                        put("fields", JSONArray(listOf("id")))
                    }
                )
                val expenseCount = (expenseResult as? JSONArray)?.length() ?: 0

                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tv_overtime_count).text = "$overtimeCount"
                    findViewById<TextView>(R.id.tv_expense_count).text = "$expenseCount"
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardActivity, getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_LONG).show()
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
            R.id.action_logout -> {
                showLogoutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton(getString(R.string.logout_yes)) { _, _ ->
                OdooRpcClient.setSession(null)
                prefs.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(getString(R.string.logout_no), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.primary_dark))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.gray))
        }
        dialog.show()
    }
}
