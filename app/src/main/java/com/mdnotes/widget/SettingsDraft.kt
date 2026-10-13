package com.mdnotes.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONObject

/** A settings transaction: no preference writes reach the application before Apply. */
class SettingsDraft(base: Context, saved: String? = null) : ContextWrapper(base) {
    private val values = if (saved == null) PreferencesManager.settings(base).toMutableMap() else {
        val json = JSONObject(saved)
        json.keys().asSequence().associateWith { json.get(it) }.toMutableMap()
    }
    private var baseline = PreferencesManager.settings(base)
    val dirty: Boolean get() = values != baseline
    fun serialize(): String = JSONObject(values).toString()
    fun applySettings() {
        PreferencesManager.writeSettings(baseContext, values)
        baseline = values.toMap()
    }

    val preferences: SharedPreferences = object : SharedPreferences {
        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getInt(key: String?, defValue: Int) = (values[key] as? Number)?.toInt() ?: defValue
        override fun getLong(key: String?, defValue: Long) = (values[key] as? Number)?.toLong() ?: defValue
        override fun getFloat(key: String?, defValue: Float) = (values[key] as? Number)?.toFloat() ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false
            private fun put(key: String?, value: Any?): SharedPreferences.Editor { if (key != null) changes[key] = value; return this }
            override fun putString(key: String?, value: String?) = put(key, value)
            override fun putInt(key: String?, value: Int) = put(key, value)
            override fun putLong(key: String?, value: Long) = put(key, value)
            override fun putFloat(key: String?, value: Float) = put(key, value)
            override fun putBoolean(key: String?, value: Boolean) = put(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
            override fun remove(key: String?) = put(key, null)
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun apply() {
                if (clear) values.clear()
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
            override fun commit(): Boolean { apply(); return true }
        }
    }
}
