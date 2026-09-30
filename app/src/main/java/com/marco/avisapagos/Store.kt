package com.marco.avisapagos

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Ajustes del usuario. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    var voiceOn: Boolean
        get() = sp.getBoolean("voz", true); set(v) = sp.edit().putBoolean("voz", v).apply()
    var beep: Boolean
        get() = sp.getBoolean("ding", true); set(v) = sp.edit().putBoolean("ding", v).apply()
    var maxVolume: Boolean
        get() = sp.getBoolean("volmax", true); set(v) = sp.edit().putBoolean("volmax", v).apply()
    var sayName: Boolean
        get() = sp.getBoolean("nombre", true); set(v) = sp.edit().putBoolean("nombre", v).apply()
    var repeatTwice: Boolean
        get() = sp.getBoolean("repetir", false); set(v) = sp.edit().putBoolean("repetir", v).apply()
    var anyApp: Boolean
        get() = sp.getBoolean("cualquier", false); set(v) = sp.edit().putBoolean("cualquier", v).apply()
}

/** Historial de pagos guardado en el teléfono (sin internet). */
object Store {
    private const val MAX = 1000
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private val recentKeys = LinkedHashMap<String, Long>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() = main.post { listeners.toList().forEach { it() } }

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("pagos", Context.MODE_PRIVATE)

    @Synchronized
    fun all(ctx: Context): List<Payment> {
        val arr = JSONArray(sp(ctx).getString("lista", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Payment(
                o.getLong("t"), o.getString("app"), o.getDouble("monto"),
                o.optString("de").ifBlank { null }, o.optString("raw"),
            )
        }
    }

    fun today(ctx: Context): List<Payment> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return all(ctx).filter { it.time >= start }
    }

    @Synchronized
    fun add(ctx: Context, p: Payment) {
        val list = all(ctx).toMutableList()
        list.add(0, p)
        val arr = JSONArray()
        list.take(MAX).forEach {
            arr.put(JSONObject().apply {
                put("t", it.time); put("app", it.app); put("monto", it.amount)
                put("de", it.sender ?: ""); put("raw", it.raw)
            })
        }
        sp(ctx).edit().putString("lista", arr.toString()).apply()
        notifyChanged()
    }

    @Synchronized
    fun clear(ctx: Context) {
        sp(ctx).edit().putString("lista", "[]").apply()
        notifyChanged()
    }

    /**
     * Evita anunciar dos veces el mismo pago (las apps a veces actualizan
     * o repiten la misma notificación).
     */
    @Synchronized
    fun isDuplicate(notifKey: String, p: Payment): Boolean {
        val now = p.time
        recentKeys.entries.removeAll { now - it.value > 10 * 60_000 }
        val k1 = "N|$notifKey|${p.raw}"
        val k2 = "P|${p.app}|${p.amount}|${p.sender}"
        val dup = recentKeys.containsKey(k1) ||
            (recentKeys[k2]?.let { now - it < 20_000 } ?: false)
        recentKeys[k1] = now
        recentKeys[k2] = now
        return dup
    }

    // ---- Notificaciones de apps de pago que no se reconocieron (para ajustar reglas) ----
    @Synchronized
    fun addUnmatched(ctx: Context, app: String, text: String) {
        val arr = JSONArray(sp(ctx).getString("noreconocidas", "[]"))
        val out = JSONArray()
        out.put(JSONObject().apply { put("t", System.currentTimeMillis()); put("app", app); put("txt", text) })
        for (i in 0 until minOf(arr.length(), 14)) out.put(arr.getJSONObject(i))
        sp(ctx).edit().putString("noreconocidas", out.toString()).apply()
        notifyChanged()
    }

    fun unmatched(ctx: Context): List<Triple<Long, String, String>> {
        val arr = JSONArray(sp(ctx).getString("noreconocidas", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Triple(o.getLong("t"), o.getString("app"), o.getString("txt"))
        }
    }
}
