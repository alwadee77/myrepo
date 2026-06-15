package com.ess.portal

import android.webkit.CookieManager
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OdooApi {

    data class AuthResult(val uid: Int, val sessionId: String)

    fun authenticate(
        baseUrl: String,
        db: String,
        login: String,
        password: String
    ): AuthResult? {
        val url = URL("$baseUrl/web/session/authenticate")
        val conn = url.openConnection() as HttpURLConnection

        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val requestBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("id", 1)
                put("params", JSONObject().apply {
                    put("db", db)
                    put("login", login)
                    put("password", password)
                })
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) return null

            val reader = InputStreamReader(conn.inputStream)
            val responseBody = reader.readText()
            val responseJson = JSONObject(responseBody)

            if (!responseJson.has("result")) return null

            val result = responseJson.getJSONObject("result")
            val uid = result.optInt("uid", 0)
            if (uid == 0) return null

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            var index = 0
            while (true) {
                val key = conn.getHeaderFieldKey(index) ?: break
                if (key.equals("Set-Cookie", ignoreCase = true)) {
                    val cookie = conn.getHeaderField(index)
                    cookieManager.setCookie(baseUrl, cookie)
                }
                index++
            }

            val sessionId = conn.getHeaderField("Set-Cookie") ?: ""

            AuthResult(uid = uid, sessionId = sessionId)
        } finally {
            conn.disconnect()
        }
    }
}
