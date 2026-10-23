package com.ngcode.voxphone

import android.content.Context
import com.voximplant.android.sdk.core.Node

/** Saved login so the app can reconnect by itself after restart or network loss. */
data class Credentials(val user: String, val password: String, val node: Node)

object Prefs {
    private const val FILE = "vox"

    fun load(context: Context): Credentials? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val user = p.getString("user", null) ?: return null
        val password = p.getString("password", null) ?: return null
        val node = runCatching { Node.valueOf(p.getString("node", null)!!) }.getOrNull() ?: return null
        return Credentials(user, password, node)
    }

    fun save(context: Context, c: Credentials) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("user", c.user)
            .putString("password", c.password)
            .putString("node", c.node.name)
            .apply()
    }

    /** Forgets the password but keeps user and node to prefill the login form. */
    fun clearPassword(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove("password").apply()
    }

    fun lastUser(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("user", "") ?: ""

    fun lastNode(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("node", null)
}
