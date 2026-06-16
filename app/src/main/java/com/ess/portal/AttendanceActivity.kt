package com.ess.portal

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AttendanceActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    private lateinit var prefs: AppPreferences
    private var isCheckedIn = false

    override fun attachBaseContext(newBase: Context) {
        val p = AppPreferences(newBase)
        super.attachBaseContext(LocaleUtil.applyLocale(newBase, p.getLang()))
    }

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
            requestLocationAndToggle()
        }

        loadMonthAttendance()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocationAndToggle()
            } else {
                toggleAttendance(null, null)
            }
        }
    }

    private fun loadMonthAttendance() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                var empId = OdooRpcClient.getEmployeeId()

                if (empId == 0) {
                    val uid = OdooRpcClient.getSession()?.uid ?: return@launch
                    val empResult = OdooRpcClient.searchRead(
                        baseUrl, db, "hr.employee",
                        domain = JSONArray(listOf(JSONArray(listOf("user_id", "=", uid)))),
                        fields = JSONArray(listOf("id"))
                    )
                    if (empResult != null && empResult.length() > 0) {
                        empId = empResult.getJSONObject(0).optInt("id", 0)
                        OdooRpcClient.setEmployeeId(empId)
                    }
                    if (empId == 0) return@launch
                }

                val today = getTodayDate()
                val monthStart = "${today.substring(0, 7)}-01"

                val result = OdooRpcClient.callKw(
                    baseUrl, db, "hr.attendance", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id", "=", empId)),
                            JSONArray(listOf("check_in", ">=", "$monthStart 00:00:00"))
                        )))
                        put("fields", JSONArray(listOf("id", "check_in", "check_out", "worked_hours")))
                        put("order", "check_in desc")
                    }
                )
                val records = result as? JSONArray

                withContext(Dispatchers.Main) {
                    updateAttendanceUI(records)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateAttendanceUI(records: JSONArray?) {
        val btn = findViewById<Button>(R.id.btn_attendance_action)
        val statusText = findViewById<TextView>(R.id.tv_attendance_status)
        val lastAction = findViewById<TextView>(R.id.tv_last_action)
        val emptyView = findViewById<CardView>(R.id.cv_empty_records)
        val recordsView = findViewById<LinearLayout>(R.id.layout_records)

        recordsView.removeAllViews()

        if (records == null || records.length() == 0) {
            isCheckedIn = false
            btn.text = getString(R.string.attendance_check_in)
            btn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            statusText.text = getString(R.string.status_not_clocked_in)
            lastAction.text = getString(R.string.attendance_no_record)
            emptyView.visibility = View.VISIBLE
            recordsView.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        recordsView.visibility = View.VISIBLE

        val last = records.getJSONObject(0)
        val checkOut = last.opt("check_out")

        if (checkOut == JSONObject.NULL) {
            isCheckedIn = true
            btn.text = getString(R.string.attendance_register_check_out)
            btn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.error)
            statusText.text = getString(R.string.attendance_checked_in)
            lastAction.text = getString(R.string.attendance_since, formatTime(last.optString("check_in", "")))
        } else {
            isCheckedIn = false
            btn.text = getString(R.string.attendance_register_check_in)
            btn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            statusText.text = getString(R.string.status_completed)
            val ci = formatDate(last.optString("check_in", ""))
            val co = formatTime(last.optString("check_out", ""))
            lastAction.text = "$ci $co"
        }

        // Month records list
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        for (i in 0 until records.length()) {
            val rec = records.getJSONObject(i)
            val ci = rec.optString("check_in", "")
            val co = rec.opt("check_out")
            val hours = rec.optDouble("worked_hours", 0.0)
            val comment = rec.optString("portal_comment", "")
            val attId = rec.optInt("id", 0)

            val card = CardView(this)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 8
            card.layoutParams = lp
            card.radius = 12f
            card.setCardBackgroundColor(0xFFFFFFFF.toInt())
            card.cardElevation = 2f
            card.setPadding(0, 0, 0, 0)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(16, 14, 16, 14)

            val dateTv = TextView(this)
            dateTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            dateTv.text = formatDate(ci)
            dateTv.textSize = 14f
            dateTv.setTextColor(0xFF1E293B.toInt())
            dateTv.setTypeface(null, android.graphics.Typeface.BOLD)

            val timeTv = TextView(this)
            timeTv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            timeTv.text = "${formatTime(ci)} - ${if (co == JSONObject.NULL) "\u2014" else formatTime(co.toString())}"
            timeTv.textSize = 12f
            timeTv.setTextColor(0xFF64748B.toInt())

            val hoursTv = TextView(this)
            hoursTv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hoursTv.text = if (hours > 0) "%.2f".format(hours) else "\u2014"
            hoursTv.textSize = 12f
            hoursTv.setTextColor(0xFF64748B.toInt())

            val commentBtn = Button(this)
            val btnLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            btnLp.marginStart = 8
            btnLp.marginEnd = 4
            commentBtn.layoutParams = btnLp
            commentBtn.text = if (comment.isNotEmpty()) "\u2705 ${getString(R.string.comment_view)}" else "\uD83D\uDCDD ${getString(R.string.comment_add)}"
            commentBtn.textSize = 13f
            commentBtn.setBackgroundResource(android.R.color.transparent)
            commentBtn.setOnClickListener {
                showCommentDialog(attId, comment)
            }

            if (isRtl) {
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL)
            }
            row.addView(dateTv)
            row.addView(timeTv)
            val spacer = View(this)
            spacer.layoutParams = LinearLayout.LayoutParams(8, 1)
            row.addView(spacer)
            row.addView(hoursTv)
            row.addView(commentBtn)

            card.addView(row)
            recordsView.addView(card)
        }
    }

    private fun requestLocationAndToggle() {
        if (isCheckedIn) {
            toggleAttendance(null, null)
            return
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
            return
        }
        getLocationAndToggle()
    }

    private fun getLocationAndToggle() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                toggleAttendance(location.latitude, location.longitude)
            } else {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        locationManager.removeUpdates(this)
                        toggleAttendance(loc.latitude, loc.longitude)
                    }
                    override fun onProviderDisabled(provider: String) {
                        toggleAttendance(null, null)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                }, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            toggleAttendance(null, null)
        }
    }

    private fun toggleAttendance(latitude: Double?, longitude: Double?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                var empId = OdooRpcClient.getEmployeeId()
                if (empId == 0) {
                    val uid = OdooRpcClient.getSession()?.uid ?: return@launch
                    val empResult = OdooRpcClient.searchRead(
                        baseUrl, db, "hr.employee",
                        domain = JSONArray(listOf(JSONArray(listOf("user_id", "=", uid)))),
                        fields = JSONArray(listOf("id"))
                    )
                    if (empResult != null && empResult.length() > 0) {
                        empId = empResult.getJSONObject(0).optInt("id", 0)
                        OdooRpcClient.setEmployeeId(empId)
                    }
                    if (empId == 0) return@launch
                }

                // Geofence check using GPS coordinates
                if (latitude != null && longitude != null) {
                    val empResult = OdooRpcClient.callKw(
                        baseUrl, db, "hr.employee", "search_read",
                        kwargs = JSONObject().apply {
                            put("domain", JSONArray(listOf(JSONArray(listOf("id", "=", empId)))))
                            put("fields", JSONArray(listOf("office_latitude", "office_longitude", "office_geofence_radius")))
                            put("limit", 1)
                        }
                    )
                    val employees = empResult as? JSONArray
                    if (employees != null && employees.length() > 0) {
                        val emp = employees.getJSONObject(0)
                        val officeLat = emp.optDouble("office_latitude", 0.0)
                        val officeLng = emp.optDouble("office_longitude", 0.0)
                        val radius = emp.optDouble("office_geofence_radius", 100.0)
                        if (officeLat != 0.0 && officeLng != 0.0) {
                            val distance = calculateDistance(latitude, longitude, officeLat, officeLng)
                            if (distance > radius) {
                                val msg = getString(R.string.geofence_outside, Math.round(distance))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@AttendanceActivity, msg, Toast.LENGTH_LONG).show()
                                }
                                return@launch
                            }
                        }
                    }
                }

                val result = OdooRpcClient.callKw(
                    baseUrl, db, "hr.attendance", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id", "=", empId)),
                            JSONArray(listOf("check_out", "=", false))
                        )))
                        put("fields", JSONArray(listOf("id", "check_in")))
                        put("limit", 1)
                    }
                )
                val openRecords = result as? JSONArray

                var checkedIn = false
                var openId = 0
                if (openRecords != null && openRecords.length() > 0) {
                    checkedIn = true
                    openId = openRecords.getJSONObject(0).optInt("id", 0)
                }

                if (checkedIn && openId > 0) {
                    val utcFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    utcFmt.timeZone = TimeZone.getTimeZone("UTC")
                    val now = utcFmt.format(Date())
                    val wrote = OdooRpcClient.write(baseUrl, db, "hr.attendance", openId,
                        JSONObject().apply { put("check_out", now) })
                    withContext(Dispatchers.Main) {
                        if (wrote) {
                            Toast.makeText(this@AttendanceActivity, getString(R.string.attendance_checked_out_msg), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@AttendanceActivity, getString(R.string.error_loading, "Check-out failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val utcFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    utcFmt.timeZone = TimeZone.getTimeZone("UTC")
                    val now = utcFmt.format(Date())
                    val newId = OdooRpcClient.create(baseUrl, db, "hr.attendance",
                        JSONObject().apply {
                            put("employee_id", empId)
                            put("check_in", now)
                        })
                    withContext(Dispatchers.Main) {
                        if (newId != null && newId > 0) {
                            Toast.makeText(this@AttendanceActivity, getString(R.string.attendance_checked_in_msg), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@AttendanceActivity, getString(R.string.error_loading, "Check-in failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                loadMonthAttendance()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, getString(R.string.error_loading, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun showCommentDialog(attendanceId: Int, currentComment: String) {
        val input = EditText(this)
        input.setText(currentComment)
        input.hint = getString(R.string.comment_hint)
        input.setPadding(32, 16, 32, 16)
        input.textSize = 14f
        input.setLines(4)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.comment_title))
            .setView(input)
            .setPositiveButton(getString(R.string.comment_send)) { _, _ ->
                val newComment = input.text.toString().trim()
                if (newComment.isNotEmpty()) {
                    saveComment(attendanceId, newComment)
                } else {
                    Toast.makeText(this, getString(R.string.comment_empty_warn), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveComment(attendanceId: Int, comment: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val ctx = this@AttendanceActivity

                // 1. Write portal_comment to attendance record
                OdooRpcClient.write(baseUrl, db, "hr.attendance", attendanceId,
                    JSONObject().apply { put("portal_comment", comment) })

                // 2. Fetch manager's partner_id for notification
                var managerPartnerId = 0
                try {
                    val attResult = OdooRpcClient.callKw(baseUrl, db, "hr.attendance", "read",
                        args = JSONArray(listOf(
                            JSONArray(listOf(attendanceId)),
                            JSONArray(listOf("employee_id"))
                        ))
                    ) as? JSONArray
                    val empId = attResult?.optJSONObject(0)?.optJSONArray("employee_id")?.optInt(0) ?: 0
                    if (empId > 0) {
                        val empResult = OdooRpcClient.callKw(baseUrl, db, "hr.employee", "read",
                            args = JSONArray(listOf(
                                JSONArray(listOf(empId)),
                                JSONArray(listOf("parent_id"))
                            ))
                        ) as? JSONArray
                        val mgrEmpId = empResult?.optJSONObject(0)?.optJSONArray("parent_id")?.optInt(0) ?: 0
                        if (mgrEmpId > 0) {
                            val mgrResult = OdooRpcClient.callKw(baseUrl, db, "hr.employee", "read",
                                args = JSONArray(listOf(
                                    JSONArray(listOf(mgrEmpId)),
                                    JSONArray(listOf("user_id"))
                                ))
                            ) as? JSONArray
                            val userId = mgrResult?.optJSONObject(0)?.optJSONArray("user_id")?.optInt(0) ?: 0
                            if (userId > 0) {
                                val userResult = OdooRpcClient.callKw(baseUrl, db, "res.users", "read",
                                    args = JSONArray(listOf(
                                        JSONArray(listOf(userId)),
                                        JSONArray(listOf("partner_id"))
                                    ))
                                ) as? JSONArray
                                managerPartnerId = userResult?.optJSONObject(0)?.optJSONArray("partner_id")?.optInt(0) ?: 0
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 3. Post to chatter with partner notification (sends email via Odoo mail server)
                if (managerPartnerId > 0) {
                    try {
                        OdooRpcClient.callKw(baseUrl, db, "hr.attendance", "message_post",
                            args = JSONArray(listOf(
                                JSONArray(listOf(attendanceId)),
                                JSONObject().apply {
                                    put("body", "\u270D\ufe0f Portal Comment: $comment")
                                    put("message_type", "comment")
                                    put("subtype_xmlid", "mail.mt_comment")
                                    put("partner_ids", JSONArray(listOf(managerPartnerId)))
                                }
                            ))
                        )
                    } catch (_: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, getString(R.string.comment_sent), Toast.LENGTH_SHORT).show()
                    loadMonthAttendance()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, getString(R.string.comment_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getTodayDate(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}-${String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))}"
    }

    private fun utcToSaudi(dt: String): Date? {
        return try {
            val utcFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            utcFmt.timeZone = TimeZone.getTimeZone("UTC")
            utcFmt.parse(dt)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTime(dt: String): String {
        val date = utcToSaudi(dt)
        if (date != null) {
            val saudiFmt = SimpleDateFormat("HH:mm", Locale.US)
            saudiFmt.timeZone = TimeZone.getTimeZone("Asia/Riyadh")
            return saudiFmt.format(date)
        }
        return if (dt.length >= 16) dt.substring(11, 16) else dt
    }

    private fun formatDate(dt: String): String {
        val date = utcToSaudi(dt)
        if (date != null) {
            val saudiFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            saudiFmt.timeZone = TimeZone.getTimeZone("Asia/Riyadh")
            return saudiFmt.format(date)
        }
        return if (dt.length >= 10) {
            val parts = dt.substring(0, 10).split("-")
            if (parts.size == 3) {
                val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val m = parts[1].toIntOrNull()?.let { if (it in 1..12) months[it - 1] else parts[1] } ?: parts[1]
                "$m ${parts[2]}, ${parts[0]}"
            } else dt.substring(0, 10)
        } else dt
    }
}
