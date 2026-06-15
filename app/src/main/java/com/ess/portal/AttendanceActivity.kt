package com.ess.portal

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AttendanceActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        prefs = AppPreferences(this)

        setSupportActionBar(findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_attendance_action).setOnClickListener {
            toggleAttendance()
        }

        loadTodayAttendance()
    }

    private fun loadTodayAttendance() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val empId = OdooRpcClient.getEmployeeId()
                if (empId == 0) return@launch

                val today = getTodayDate()
                val result = OdooRpcClient.callKw(
                    baseUrl, db, "hr.attendance", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id", "=", empId)),
                            JSONArray(listOf("check_in", ">=", "$today 00:00:00"))
                        )))
                        put("fields", JSONArray(listOf("id", "check_in", "check_out")))
                    }
                )
                val records = (result as? JSONObject)?.optJSONArray("records")

                withContext(Dispatchers.Main) {
                    updateAttendanceUI(records)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateAttendanceUI(records: JSONArray?) {
        val btn = findViewById<Button>(R.id.btn_attendance_action)
        val statusText = findViewById<TextView>(R.id.tv_attendance_status)
        val statusIcon = findViewById<TextView>(R.id.tv_status_icon)
        val lastAction = findViewById<TextView>(R.id.tv_last_action)
        val emptyView = findViewById<android.view.View>(R.id.cv_empty_records)
        val recordsView = findViewById<android.view.View>(R.id.layout_records)

        if (records == null || records.length() == 0) {
            btn.text = "Check In"
            statusText.text = "Not Clocked In"
            lastAction.text = "No records today"
            statusIcon.text = "\u23F1"
            emptyView.visibility = android.view.View.VISIBLE
            recordsView.visibility = android.view.View.GONE
            return
        }

        emptyView.visibility = android.view.View.GONE
        recordsView.visibility = android.view.View.VISIBLE
        recordsView.removeAllViews()

        val last = records.getJSONObject(records.length() - 1)
        val checkOut = last.opt("check_out")

        if (checkOut == JSONObject.NULL) {
            btn.text = "Check Out"
            statusText.text = "Clocked In"
            statusIcon.text = "\u23F9"
            lastAction.text = "Since ${formatTime(last.optString("check_in", ""))}"
        } else {
            btn.text = "Check In"
            statusText.text = "Completed"
            statusIcon.text = "\u2705"
            lastAction.text = "Last: ${formatTime(last.optString("check_out", ""))}"
        }

        for (i in 0 until records.length()) {
            val rec = records.getJSONObject(i)
            val ci = rec.optString("check_in", "")
            val co = rec.opt("check_out")
            val row = androidx.appcompat.widget.AppCompatTextView(this)
            row.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            row.setPadding(20, 16, 20, 16)
            row.text = "${formatTime(ci)} - ${if (co == JSONObject.NULL) "Now" else formatTime(co.toString())}"
            row.textSize = 14f
            row.setTextColor(0xFF1a1a1a.toInt())
            if (i < records.length() - 1) {
                row.setBackgroundResource(android.R.drawable.divider_horizontal_bright)
            }
            recordsView.addView(row)
        }
    }

    private fun toggleAttendance() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val empId = OdooRpcClient.getEmployeeId()
                if (empId == 0) return@launch

                val today = getTodayDate()
                val result = OdooRpcClient.callKw(
                    baseUrl, db, "hr.attendance", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id", "=", empId)),
                            JSONArray(listOf("check_in", ">=", "$today 00:00:00"))
                        )))
                        put("fields", JSONArray(listOf("id", "check_in", "check_out")))
                    }
                )
                val records = (result as? JSONObject)?.optJSONArray("records")

                var checkedIn = false
                var openId = 0
                if (records != null && records.length() > 0) {
                    val last = records.getJSONObject(records.length() - 1)
                    if (last.opt("check_out") == JSONObject.NULL) {
                        checkedIn = true
                        openId = last.optInt("id", 0)
                    }
                }

                if (checkedIn && openId > 0) {
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    OdooRpcClient.write(baseUrl, db, "hr.attendance", openId,
                        JSONObject().apply { put("check_out", now) })
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AttendanceActivity, "Checked out!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    OdooRpcClient.create(baseUrl, db, "hr.attendance",
                        JSONObject().apply {
                            put("employee_id", empId)
                            put("check_in", now)
                        })
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AttendanceActivity, "Checked in!", Toast.LENGTH_SHORT).show()
                    }
                }

                loadTodayAttendance()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getTodayDate(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}-${String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))}"
    }

    private fun formatTime(dt: String): String {
        return if (dt.length >= 16) dt.substring(11, 16) else dt
    }
}
