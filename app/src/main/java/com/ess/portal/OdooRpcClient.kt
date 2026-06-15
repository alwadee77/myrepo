package com.ess.portal

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OdooRpcClient {

    private const val TAG = "OdooRpc"

    data class Session(val uid: Int, val sessionId: String)

    private var currentSession: Session? = null
    private var empId: Int = 0

    fun setSession(session: Session?) {
        currentSession = session
    }

    fun getSession(): Session? = currentSession

    fun getEmployeeId(): Int = empId
    fun setEmployeeId(id: Int) { empId = id }

    fun authenticate(baseUrl: String, db: String, login: String, password: String): Session? {
        val url = URL("$baseUrl/web/session/authenticate")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("id", 1)
                put("params", JSONObject().apply {
                    put("db", db)
                    put("login", login)
                    put("password", password)
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null

            val resp = JSONObject(conn.inputStream.bufferedReader().readText())
            val result = resp.optJSONObject("result") ?: return null
            val uid = result.optInt("uid", 0)
            if (uid == 0) return null

            val sessionId = extractSessionId(conn)
            Session(uid, sessionId).also { currentSession = it }
        } finally {
            conn.disconnect()
        }
    }

    private fun rpcCall(
        baseUrl: String, db: String, endpoint: String,
        body: JSONObject
    ): Any? {
        var sessionId = currentSession?.sessionId ?: ""
        if (sessionId.isNotEmpty() && sessionId.contains(";")) {
            sessionId = sessionId.split(";")[0]
        }

        val url = URL("$baseUrl$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (sessionId.isNotEmpty()) conn.setRequestProperty("Cookie", sessionId)
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP ${conn.responseCode} for $endpoint")
                return null
            }

            val resp = JSONObject(conn.inputStream.bufferedReader().readText())
            val error = resp.optJSONObject("error")
            if (error != null) {
                Log.e(TAG, "RPC error: ${error.optString("message")}")
                return null
            }
            resp.opt("result")
        } finally {
            conn.disconnect()
        }
    }

    fun callKw(
        baseUrl: String, db: String, model: String, method: String,
        args: JSONArray = JSONArray(), kwargs: JSONObject = JSONObject()
    ): Any? {
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "call")
            put("id", 1)
            put("params", JSONObject().apply {
                put("model", model)
                put("method", method)
                put("args", args)
                put("kwargs", kwargs)
            })
        }
        return rpcCall(baseUrl, db, "/web/dataset/call_kw", body)
    }

    fun searchRead(
        baseUrl: String, db: String, model: String,
        domain: JSONArray = JSONArray(), fields: JSONArray = JSONArray(listOf("id", "display_name"))
    ): JSONArray? {
        val result = callKw(baseUrl, db, model, "search_read", kwargs = JSONObject().apply {
            put("domain", domain)
            put("fields", fields)
        })
        return (result as? JSONObject)?.optJSONArray("records")
    }

    fun create(
        baseUrl: String, db: String, model: String, values: JSONObject
    ): Int? {
        val result = callKw(baseUrl, db, model, "create", args = JSONArray(listOf(values)))
        return (result as? Int)?.takeIf { it >= 0 }
    }

    fun write(
        baseUrl: String, db: String, model: String, id: Int, values: JSONObject
    ): Boolean {
        val result = callKw(baseUrl, db, model, "write", args = JSONArray(listOf(JSONArray(listOf(id)), values)))
        return result == true
    }

    private fun extractSessionId(conn: HttpURLConnection): String {
        var index = 0
        while (true) {
            val key = conn.getHeaderFieldKey(index) ?: break
            if (key.equals("Set-Cookie", ignoreCase = true)) {
                return conn.getHeaderField(index)
            }
            index++
        }
        return ""
    }
}
