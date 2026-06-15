package com.ess.portal

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class TimeOffActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_off)

        prefs = AppPreferences(this)

        setSupportActionBar(findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_new_request).setOnClickListener {
            showNewRequestDialog()
        }

        loadData()
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val uid = OdooRpcClient.getSession()?.uid ?: return@launch

                val balanceResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave.allocation", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid)),
                            JSONArray(listOf("state", "=", "validate"))
                        )))
                        put("fields", JSONArray(listOf("id", "holiday_status_id", "number_of_days", "leaves_taken")))
                    }
                )
                val balances = balanceResult as? JSONArray

                val requestsResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid))
                        )))
                        put("fields", JSONArray(listOf("id", "name", "holiday_status_id", "date_from", "date_to", "number_of_days", "state")))
                        put("order", "date_from desc")
                        put("limit", 20)
                    }
                )
                val requests = requestsResult as? JSONArray

                val typesResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave.type", "search_read",
                    kwargs = JSONObject().apply {
                        put("fields", JSONArray(listOf("id", "name")))
                    }
                )
                val leaveTypes = typesResult as? JSONArray

                withContext(Dispatchers.Main) {
                    renderBalance(balances)
                    renderRequests(requests)
                    leaveTypes?.let { types ->
                        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_new_request).setOnClickListener {
                            showNewRequestDialogWithTypes(types)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TimeOffActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderBalance(balances: JSONArray?) {
        val layout = findViewById<LinearLayout>(R.id.layout_balance)
        layout.removeAllViews()

        if (balances == null || balances.length() == 0) {
            val tv = TextView(this)
            tv.text = "No leave allocations found"
            tv.setTextColor(ContextCompat.getColor(this, R.color.gray))
            tv.setPadding(16, 16, 16, 16)
            layout.addView(tv)
            return
        }

        val seen = mutableMapOf<String, Double>()
        for (i in 0 until balances.length()) {
            val b = balances.getJSONObject(i)
            val type = formatM2o(b.opt("holiday_status_id"), "name")
            val total = b.optDouble("number_of_days", 0.0)
            val taken = b.optDouble("leaves_taken", 0.0)
            seen[type] = (seen[type] ?: 0.0) + (total - taken)
        }

        for ((name, remaining) in seen) {
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
            card.setPadding(20, 16, 20, 16)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL

            val nameTv = TextView(this)
            nameTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            nameTv.text = name
            nameTv.textSize = 15f
            nameTv.setTextColor(0xFF1E293B.toInt())
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD)

            val daysTv = TextView(this)
            daysTv.text = "%.1f days".format(remaining)
            daysTv.textSize = 14f
            daysTv.setTextColor(0xFF10B981.toInt())
            daysTv.setTypeface(null, android.graphics.Typeface.BOLD)

            row.addView(nameTv)
            row.addView(daysTv)
            card.addView(row)
            layout.addView(card)
        }
    }

    private fun renderRequests(requests: JSONArray?) {
        val layout = findViewById<LinearLayout>(R.id.layout_requests)
        val empty = findViewById<CardView>(R.id.cv_empty)
        layout.removeAllViews()

        if (requests == null || requests.length() == 0) {
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE

        for (i in 0 until requests.length()) {
            val r = requests.getJSONObject(i)
            val name = r.optString("name", "")
            val type = formatM2o(r.opt("holiday_status_id"), "name")
            val dateFrom = formatDate(r.optString("date_from", ""))
            val dateTo = formatDate(r.optString("date_to", ""))
            val days = r.optDouble("number_of_days", 0.0)
            val state = r.optString("state", "")
            val stateLabel = formatState(state)

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
            card.setPadding(16, 12, 16, 12)

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL

            val header = LinearLayout(this)
            header.orientation = LinearLayout.HORIZONTAL
            header.gravity = android.view.Gravity.CENTER_VERTICAL

            val nameTv = TextView(this)
            nameTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            nameTv.text = if (name.isNotEmpty()) name else type
            nameTv.textSize = 14f
            nameTv.setTextColor(0xFF1E293B.toInt())
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD)

            val badge = TextView(this)
            badge.text = stateLabel
            badge.textSize = 11f
            badge.setTextColor(android.graphics.Color.WHITE)
            badge.setPadding(10, 4, 10, 4)
            badge.setBackgroundColor(getStateColor(state))

            header.addView(nameTv)
            header.addView(badge)
            col.addView(header)

            val detail = TextView(this)
            detail.text = "$dateFrom - $dateTo  ($days days)"
            detail.textSize = 13f
            detail.setTextColor(0xFF64748B.toInt())
            detail.setPadding(0, 4, 0, 0)
            col.addView(detail)

            card.addView(col)
            layout.addView(card)
        }
    }

    private var leaveTypesCache: JSONArray? = null

    private fun showNewRequestDialogWithTypes(types: JSONArray) {
        leaveTypesCache = types
        showNewRequestDialog()
    }

    private fun showNewRequestDialog() {
        val types = leaveTypesCache
        if (types == null || types.length() == 0) {
            Toast.makeText(this, "No leave types available", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Time Off Request")

        val view = layoutInflater.inflate(R.layout.dialog_time_off_request, null)
        val spinner = view.findViewById<Spinner>(R.id.spinner_leave_type)
        val etDateFrom = view.findViewById<TextView>(R.id.et_date_from)
        val etDateTo = view.findViewById<TextView>(R.id.et_date_to)

        val typeNames = Array(types.length()) { i -> types.getJSONObject(i).optString("name", "?") }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeNames)

        etDateFrom.setOnClickListener { showDatePicker(etDateFrom) }
        etDateTo.setOnClickListener { showDatePicker(etDateTo) }

        builder.setView(view)
        builder.setPositiveButton("Submit") { _, _ ->
            val idx = spinner.selectedItemPosition
            if (idx < 0) return@setPositiveButton
            val typeId = types.getJSONObject(idx).optInt("id", 0)
            val dateFrom = etDateFrom.text.toString()
            val dateTo = etDateTo.text.toString()
            if (dateFrom.isEmpty() || dateTo.isEmpty()) {
                Toast.makeText(this, "Please select dates", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            submitRequest(typeId, dateFrom, dateTo)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showDatePicker(textView: TextView) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            textView.text = "%04d-%02d-%02d".format(y, m + 1, d)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun submitRequest(typeId: Int, dateFrom: String, dateTo: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val empId = OdooRpcClient.getEmployeeId()
                if (empId == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TimeOffActivity, "Employee not loaded", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val result = OdooRpcClient.callKw(
                    baseUrl, db, "hr.leave", "create",
                    args = JSONArray(listOf(JSONObject().apply {
                        put("employee_id", empId)
                        put("holiday_status_id", typeId)
                        put("date_from", "$dateFrom 08:00:00")
                        put("date_to", "$dateTo 17:00:00")
                        put("request_date_from", dateFrom)
                        put("request_date_to", dateTo)
                    }))
                )

                withContext(Dispatchers.Main) {
                    if (result != null) {
                        Toast.makeText(this@TimeOffActivity, "Time off request submitted!", Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        Toast.makeText(this@TimeOffActivity, "Failed to submit request", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TimeOffActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatM2o(value: Any?, fallback: String): String {
        if (value is JSONArray && value.length() >= 2) {
            return value.optString(1, fallback)
        }
        return value?.toString() ?: fallback
    }

    private fun formatDate(dt: String): String {
        return if (dt.length >= 10) {
            val p = dt.substring(0, 10).split("-")
            if (p.size == 3) "${p[1]}/${p[2]}/${p[0]}" else dt.substring(0, 10)
        } else dt
    }

    private fun formatState(state: String): String {
        return when (state) {
            "draft" -> "Draft"
            "confirm" -> "Pending"
            "validate1" -> "Approved"
            "validate" -> "Approved"
            "refuse" -> "Refused"
            "cancel" -> "Cancelled"
            else -> state
        }
    }

    private fun getStateColor(state: String): Int {
        return when (state) {
            "draft" -> 0xFF94A3B8.toInt()
            "confirm" -> 0xFFF59E0B.toInt()
            "validate1", "validate" -> 0xFF10B981.toInt()
            "refuse" -> 0xFFEF4444.toInt()
            "cancel" -> 0xFF94A3B8.toInt()
            else -> 0xFF94A3B8.toInt()
        }
    }
}
