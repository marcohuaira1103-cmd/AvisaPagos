package com.marco.avisapagos

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val hora = SimpleDateFormat("HH:mm", Locale("es", "PE"))
    private val fechaHora = SimpleDateFormat("dd/MM HH:mm", Locale("es", "PE"))
    private val refresh: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Announcer.get(this)

        findViewById<Button>(R.id.btnPermiso).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnProbar).setOnClickListener {
            val demo = PaymentParser.parse(
                "Confirmación de Pago. Yape! Juan Pérez te envió un pago por S/ 12.50", "Yape"
            )
            if (demo != null) Announcer.get(this).announce(demo)
            if (!prefs.voiceOn) toast("La voz está desactivada en Ajustes")
        }
        findViewById<Button>(R.id.btnRepetir).setOnClickListener {
            val last = Store.all(this).firstOrNull()
            if (last == null) toast("Todavía no hay pagos") else
                Announcer.get(this).say(PaymentParser.speech(last, prefs.sayName))
        }
        findViewById<Button>(R.id.btnBateria).setOnClickListener { askBattery() }
        findViewById<Button>(R.id.btnBorrar).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("¿Borrar todo el historial?")
                .setMessage("Se borrarán todos los pagos guardados en este celular.")
                .setPositiveButton("Borrar") { _, _ -> Store.clear(this) }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        bindSwitch(R.id.swVoz, { prefs.voiceOn }) { prefs.voiceOn = it }
        bindSwitch(R.id.swDing, { prefs.beep }) { prefs.beep = it }
        bindSwitch(R.id.swVol, { prefs.maxVolume }) { prefs.maxVolume = it }
        bindSwitch(R.id.swNombre, { prefs.sayName }) { prefs.sayName = it }
        bindSwitch(R.id.swRepetir, { prefs.repeatTwice }) { prefs.repeatTwice = it }
        bindSwitch(R.id.swCualquier, { prefs.anyApp }) { on ->
            prefs.anyApp = on
            if (on) AlertDialog.Builder(this)
                .setMessage("Se revisarán las notificaciones de todas las apps (excepto chats). " +
                    "Úsalo si recibes pagos por otra app, como una caja municipal.")
                .setPositiveButton("Entendido", null).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Store.addListener(refresh)
        if (hasNotificationAccess() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Si Android "durmió" el servicio, lo volvemos a enganchar
            NotificationListenerService.requestRebind(
                ComponentName(this, PaymentListenerService::class.java)
            )
        }
        render()
    }

    override fun onPause() {
        super.onPause()
        Store.removeListener(refresh)
    }

    private fun bindSwitch(id: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = get()
        sw.setOnCheckedChangeListener { _, v -> set(v) }
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.split(":")?.any {
            ComponentName.unflattenFromString(it)?.packageName == packageName
        } ?: false
    }

    private fun askBattery() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("Listo: Android no la va a apagar por batería")
            return
        }
        try {
            @SuppressLint("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun render() {
        // Permiso
        val ok = hasNotificationAccess()
        val card = findViewById<LinearLayout>(R.id.cardPermiso)
        card.setBackgroundResource(if (ok) R.drawable.card_ok else R.drawable.card_warn)
        findViewById<TextView>(R.id.txtPermiso).text =
            if (ok) "✅ Escuchando pagos de Yape, Plin y bancos."
            else "⚠️ Falta un permiso: activa \"AvisaPagos\" en Acceso a notificaciones. " +
                "Si Android no te deja, ve a Ajustes › Apps › AvisaPagos › ⋮ › " +
                "\"Permitir configuración restringida\" y vuelve a intentar."
        findViewById<Button>(R.id.btnPermiso).visibility = if (ok) View.GONE else View.VISIBLE

        // Totales del día
        val hoy = Store.today(this)
        val total = hoy.sumOf { it.amount }
        findViewById<TextView>(R.id.txtTotal).text = soles(total)
        val porApp = hoy.groupBy { it.app }
            .map { (app, l) -> "$app ${soles(l.sumOf { it.amount })}" }
        findViewById<TextView>(R.id.txtResumen).text =
            if (hoy.isEmpty()) "Sin pagos todavía"
            else "${hoy.size} pago${if (hoy.size == 1) "" else "s"} · " + porApp.joinToString(" · ")

        // Lista de pagos
        val lista = findViewById<LinearLayout>(R.id.listaPagos)
        lista.removeAllViews()
        if (hoy.isEmpty()) lista.addView(row("Aquí aparecerán los pagos que recibas hoy.", null, null))
        hoy.forEach { p ->
            lista.addView(row(
                "${hora.format(Date(p.time))}  ·  ${p.app}",
                p.sender ?: "—",
                soles(p.amount),
            ))
        }

        // No reconocidas
        val nr = findViewById<LinearLayout>(R.id.listaNoReconocidas)
        nr.removeAllViews()
        val items = Store.unmatched(this)
        if (items.isEmpty()) nr.addView(row("Nada por aquí 👍", null, null))
        items.forEach { (t, app, txt) ->
            val v = row("${fechaHora.format(Date(t))}  ·  $app", txt, null)
            v.setOnLongClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("notificación", "$app: $txt"))
                toast("Texto copiado")
                true
            }
            nr.addView(v)
        }
    }

    private fun row(top: String, bottom: String?, amount: String?): View {
        val d = resources.displayMetrics.density
        val pad = (14 * d).toInt()
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, (10 * d).toInt(), pad, (10 * d).toInt())
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = top; textSize = 13f; setTextColor(getColor(R.color.muted))
        })
        if (bottom != null) col.addView(TextView(this).apply {
            text = bottom; textSize = 15f; setTextColor(getColor(R.color.ink))
        })
        h.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (amount != null) h.addView(TextView(this).apply {
            text = amount; textSize = 18f; setTextColor(getColor(R.color.primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        return h
    }

    private fun soles(v: Double) = "S/ " + String.format(Locale.US, "%,.2f", v)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
