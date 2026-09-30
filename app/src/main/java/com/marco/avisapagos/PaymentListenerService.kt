package com.marco.avisapagos

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android llama a este servicio cada vez que llega una notificación
 * (requiere el permiso "Acceso a notificaciones").
 */
class PaymentListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        Announcer.get(this) // prepara la voz con anticipación
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (_: Exception) {
            // Nunca dejar que un formato raro tumbe el servicio
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val prefs = Prefs(this)
        val known = PaymentParser.KNOWN_APPS[sbn.packageName]
        if (known == null) {
            if (!prefs.anyApp) return
            // En modo "cualquier app" ignoramos chats (WhatsApp, SMS…) para evitar falsos avisos
            if (n.category == Notification.CATEGORY_MESSAGE) return
        }
        val appName = known ?: appLabel(sbn.packageName)

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = big ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() }.orEmpty()
        val full = listOf(title, text, lines).filter { it.isNotBlank() }.joinToString(". ")
        if (full.isBlank()) return

        val payment = PaymentParser.parse(full, appName)
        if (payment == null) {
            if (known != null) Store.addUnmatched(this, appName, full)
            return
        }
        if (Store.isDuplicate(sbn.key, payment)) return
        Store.add(this, payment)
        Announcer.get(this).announce(payment)
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }
}
