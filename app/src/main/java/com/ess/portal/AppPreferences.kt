package com.ess.portal

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ess_portal", Context.MODE_PRIVATE)

    fun getUrl(): String = prefs.getString("url", "") ?: ""
    fun setUrl(url: String) = prefs.edit().putString("url", url).apply()

    fun getDb(): String = prefs.getString("db", "") ?: ""
    fun setDb(db: String) = prefs.edit().putString("db", db).apply()

    fun isLoggedIn(): Boolean = prefs.getBoolean("logged_in", false)
    fun setLoggedIn(value: Boolean) = prefs.edit().putBoolean("logged_in", value).apply()

    fun getUid(): Int = prefs.getInt("uid", 0)
    fun setUid(uid: Int) = prefs.edit().putInt("uid", uid).apply()

    fun getSessionId(): String = prefs.getString("session_id", "") ?: ""
    fun setSessionId(sid: String) = prefs.edit().putString("session_id", sid).apply()

    fun isConfigured(): Boolean = getUrl().isNotEmpty() && getDb().isNotEmpty()

    fun logout() {
        prefs.edit().apply {
            putBoolean("logged_in", false)
            putInt("uid", 0)
            putString("session_id", "")
            apply()
        }
    }
}
