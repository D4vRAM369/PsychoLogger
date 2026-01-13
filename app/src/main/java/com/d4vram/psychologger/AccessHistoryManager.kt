package com.d4vram.psychologger

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * AccessHistoryManager - Gestiona el historial de accesos a la app
 *
 * Almacena los últimos MAX_EVENTS eventos de desbloqueo con:
 * - Timestamp del acceso
 * - Método utilizado (biometric, pin, init)
 */
class AccessHistoryManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "access_history_prefs"
        private const val KEY_HISTORY = "access_history"
        private const val MAX_EVENTS = 50
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Representa un evento de acceso
     */
    data class AccessEvent(
        val timestamp: Long,
        val method: String  // "biometric" | "pin" | "init" | "device_credential"
    ) {
        val formattedDate: String
            get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))

        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        val methodLabel: String
            get() = when (method) {
                "biometric" -> "🔐 Biometría"
                "pin" -> "🔢 PIN"
                "device_credential" -> "🔑 Credencial del dispositivo"
                "init" -> "🚀 Inicio"
                else -> "❓ Desconocido"
            }

        fun toJson(): JSONObject = JSONObject().apply {
            put("timestamp", timestamp)
            put("method", method)
        }

        companion object {
            fun fromJson(json: JSONObject): AccessEvent {
                return AccessEvent(
                    timestamp = json.getLong("timestamp"),
                    method = json.getString("method")
                )
            }
        }
    }

    /**
     * Registra un nuevo acceso
     *
     * @param method Método de autenticación utilizado
     */
    fun logAccess(method: String) {
        val history = getHistoryMutable()
        
        // Añadir nuevo evento al principio
        history.add(0, AccessEvent(
            timestamp = System.currentTimeMillis(),
            method = method
        ))

        // Limitar a MAX_EVENTS
        while (history.size > MAX_EVENTS) {
            history.removeAt(history.size - 1)
        }

        // Persistir
        saveHistory(history)
    }

    /**
     * Obtiene el historial de accesos (más recientes primero)
     */
    fun getHistory(): List<AccessEvent> {
        return getHistoryMutable().toList()
    }

    /**
     * Limpia todo el historial
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /**
     * Obtiene el número de accesos registrados
     */
    fun getAccessCount(): Int {
        return getHistory().size
    }

    private fun getHistoryMutable(): MutableList<AccessEvent> {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<AccessEvent>()
            for (i in 0 until jsonArray.length()) {
                list.add(AccessEvent.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveHistory(history: List<AccessEvent>) {
        val jsonArray = JSONArray()
        history.forEach { event ->
            jsonArray.put(event.toJson())
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }
}
