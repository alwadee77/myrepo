package com.ess.portal

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
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

class ExpensesActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expenses)

        prefs = AppPreferences(this)

        setSupportActionBar(findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_new_expense).setOnClickListener {
            showNewExpenseDialog()
        }

        loadExpenses()
    }

    private fun loadExpenses() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val uid = OdooRpcClient.getSession()?.uid ?: return@launch

                val expensesResult = OdooRpcClient.callKw(
                    baseUrl, db, "hr.expense", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("employee_id.user_id", "=", uid))
                        )))
                        put("fields", JSONArray(listOf("id", "name", "product_id", "date", "total_amount_currency", "state")))
                        put("order", "date desc, id desc")
                        put("limit", 50)
                    }
                )
                val expenses = expensesResult as? JSONArray

                val productsResult = OdooRpcClient.callKw(
                    baseUrl, db, "product.product", "search_read",
                    kwargs = JSONObject().apply {
                        put("domain", JSONArray(listOf(
                            JSONArray(listOf("can_be_expensed", "=", true))
                        )))
                        put("fields", JSONArray(listOf("id", "display_name")))
                        put("order", "name")
                    }
                )
                val products = productsResult as? JSONArray

                withContext(Dispatchers.Main) {
                    renderExpenses(expenses)
                    productsCache = products
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpensesActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var productsCache: JSONArray? = null

    private fun renderExpenses(expenses: JSONArray?) {
        val layout = findViewById<LinearLayout>(R.id.layout_expenses)
        val empty = findViewById<CardView>(R.id.cv_empty)
        layout.removeAllViews()

        if (expenses == null || expenses.length() == 0) {
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE

        for (i in 0 until expenses.length()) {
            val e = expenses.getJSONObject(i)
            val name = e.optString("name", "-")
            val product = formatM2o(e.opt("product_id"), "display_name")
            val date = formatDate(e.optString("date", ""))
            val amount = e.optDouble("total_amount_currency", 0.0)
            val state = e.optString("state", "")
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

            val productTv = TextView(this)
            productTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            productTv.text = product
            productTv.textSize = 14f
            productTv.setTextColor(0xFF1E293B.toInt())
            productTv.setTypeface(null, android.graphics.Typeface.BOLD)

            val amountTv = TextView(this)
            amountTv.text = "%.2f".format(amount)
            amountTv.textSize = 14f
            amountTv.setTextColor(0xFF1E293B.toInt())
            amountTv.setTypeface(null, android.graphics.Typeface.BOLD)

            header.addView(productTv)
            header.addView(amountTv)
            col.addView(header)

            val detail = LinearLayout(this)
            detail.orientation = LinearLayout.HORIZONTAL
            detail.gravity = android.view.Gravity.CENTER_VERTICAL

            val descTv = TextView(this)
            descTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            descTv.text = name
            descTv.textSize = 12f
            descTv.setTextColor(0xFF64748B.toInt())

            val dateTv = TextView(this)
            dateTv.text = date
            dateTv.textSize = 12f
            dateTv.setTextColor(0xFF64748B.toInt())
            dateTv.setPadding(8, 0, 8, 0)

            val badge = TextView(this)
            badge.text = stateLabel
            badge.textSize = 10f
            badge.setTextColor(android.graphics.Color.WHITE)
            badge.setPadding(8, 3, 8, 3)
            badge.setBackgroundColor(getStateColor(state))

            detail.addView(descTv)
            detail.addView(dateTv)
            detail.addView(badge)
            col.addView(detail)

            card.addView(col)
            layout.addView(card)
        }
    }

    private fun showNewExpenseDialog() {
        val products = productsCache ?: return
        if (products.length() == 0) {
            Toast.makeText(this, "No expense categories available", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Expense")

        val view = layoutInflater.inflate(R.layout.dialog_expense, null)
        val spinner = view.findViewById<Spinner>(R.id.spinner_category)
        val etDesc = view.findViewById<EditText>(R.id.et_description)
        val etAmount = view.findViewById<EditText>(R.id.et_amount)
        val etDate = view.findViewById<TextView>(R.id.et_date)

        val names = Array(products.length()) { i -> formatM2o(products.getJSONObject(i).opt("display_name"), "") }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)

        etDate.setOnClickListener { showDatePicker(etDate) }

        builder.setView(view)
        builder.setPositiveButton("Submit") { _, _ ->
            val idx = spinner.selectedItemPosition
            if (idx < 0) return@setPositiveButton
            val productId = products.getJSONObject(idx).optInt("id", 0)
            val desc = etDesc.text.toString()
            val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val date = etDate.text.toString()
            if (desc.isEmpty() || amount <= 0 || date.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            submitExpense(productId, desc, amount, date)
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

    private fun submitExpense(productId: Int, desc: String, amount: Double, date: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = prefs.getUrl()
                val db = prefs.getDb()
                val empId = OdooRpcClient.getEmployeeId()
                if (empId == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExpensesActivity, "Employee not loaded", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val result = OdooRpcClient.callKw(
                    baseUrl, db, "hr.expense", "create",
                    args = JSONArray(listOf(JSONObject().apply {
                        put("employee_id", empId)
                        put("product_id", productId)
                        put("name", desc)
                        put("unit_amount", amount)
                        put("quantity", 1)
                        put("date", date)
                    }))
                )

                withContext(Dispatchers.Main) {
                    if (result != null) {
                        Toast.makeText(this@ExpensesActivity, "Expense submitted!", Toast.LENGTH_SHORT).show()
                        loadExpenses()
                    } else {
                        Toast.makeText(this@ExpensesActivity, "Failed to submit expense", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpensesActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatM2o(value: Any?, field: String): String {
        if (value is JSONArray && value.length() >= 2) {
            return value.optString(1, "-")
        }
        return value?.toString() ?: "-"
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
            "submit" -> "Submitted"
            "approve" -> "Approved"
            "done" -> "Paid"
            "refuse" -> "Refused"
            "cancel" -> "Cancelled"
            else -> state
        }
    }

    private fun getStateColor(state: String): Int {
        return when (state) {
            "draft" -> 0xFF94A3B8.toInt()
            "submit" -> 0xFFF59E0B.toInt()
            "approve" -> 0xFF3B82F6.toInt()
            "done" -> 0xFF10B981.toInt()
            "refuse" -> 0xFFEF4444.toInt()
            "cancel" -> 0xFF94A3B8.toInt()
            else -> 0xFF94A3B8.toInt()
        }
    }
}
