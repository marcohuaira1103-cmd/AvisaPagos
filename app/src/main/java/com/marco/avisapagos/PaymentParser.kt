package com.marco.avisapagos

import java.text.Normalizer
import java.util.Locale

/** Un pago detectado. */
data class Payment(
    val time: Long,
    val app: String,
    val amount: Double,
    val sender: String?,
    val raw: String,
)

/**
 * Lee el texto de una notificación y decide si es un pago RECIBIDO.
 * Si no reconoce un formato, la notificación aparece en "No reconocidas"
 * dentro de la app para poder ajustar estas reglas.
 */
object PaymentParser {

    /** Apps que se escuchan por defecto (nombre de paquete -> nombre para la voz). */
    val KNOWN_APPS: Map<String, String> = linkedMapOf(
        "com.bcp.innovacxion.yapeapp" to "Yape",
        "com.bcp.bank.bcp" to "BCP",
        "pe.com.interbank.mobilebanking" to "Interbank",
        "com.bbva.nxt_peru" to "BBVA",
        "pe.com.scotiabank.blpm.android.client" to "Scotiabank",
    )

    // Frases de dinero QUE ENTRA (se comparan sin tildes y en minúsculas)
    private val INCOMING = listOf(
        "te yape", "te envio", "te plin", "te pline", "te transfiri", "te deposit",
        "te pago", "te abon", "recibiste", "has recibido", "recibio un pago",
        "transferencia recibida", "pago recibido", "abono recibido", "abono de",
        "te han enviado", "te ha enviado", "te han transferido",
    )

    // Frases de dinero QUE SALE, cobros o publicidad -> se ignoran
    private val OUTGOING = listOf(
        "yapeaste", "enviaste", "has enviado", "pagaste", "transferiste", "realizaste",
        "solicit", "te pide", "te pidio", "te esta pidiendo", "cobrar", "consumo",
        "compra ", "cargo ", "retiro", "debito", "promo", "sorteo", "gana ", "cashback",
        "prestamo", "credito", "vence", "cuota",
    )

    private val AMOUNT = Regex("""(?i)(?:S\s?/\.?|PEN)\s*([0-9][0-9.,]*)""")

    private val SENDER_BEFORE_VERB = Regex(
        """([A-Za-zÁÉÍÓÚÑÜáéíóúñü*.' ]{2,60}?)\s+te\s+(?:ha\s+|han\s+)?(?:envi[oó]|enviado|yape[oó]|pline[oó]|transfiri[oó]|transferido|pag[oó]|deposit[oó]|abon[oó])""",
        RegexOption.IGNORE_CASE,
    )
    private val SENDER_AFTER_DE = Regex(
        """\b(?:de|desde)\s+([A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑÜáéíóúñü*.']*(?:\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑÜáéíóúñü*.']*){0,3})"""
    )
    private val NOT_NAMES = setOf(
        "yape", "plin", "bcp", "interbank", "bbva", "scotiabank", "confirmacion", "pago",
        "s", "pen", "tu", "su", "la", "el", "cuenta", "ahorros",
    )

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    fun parse(text: String, appName: String, now: Long = System.currentTimeMillis()): Payment? {
        val n = normalize(text)
        if (OUTGOING.any { n.contains(it) }) return null
        if (INCOMING.none { n.contains(it) }) return null
        val m = AMOUNT.find(text) ?: return null
        val amount = parseAmount(m.groupValues[1]) ?: return null
        if (amount <= 0.0) return null
        return Payment(now, appName, amount, findSender(text), text)
    }

    /** "1,250.50" -> 1250.5 ; "25,50" -> 25.5 ; "25." -> 25.0 */
    fun parseAmount(rawIn: String): Double? {
        var s = rawIn.trimEnd('.', ',')
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")                 // 1,250.50
                else s.replace(".", "").replace(',', '.')                  // 1.250,50
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                if (s.count { it == ',' } == 1 && decimals in 1..2) s.replace(',', '.') // 25,50
                else s.replace(",", "")                                    // 1,250
            }
            lastDot >= 0 -> {
                val decimals = s.length - lastDot - 1
                if (s.count { it == '.' } > 1 || decimals == 3) s.replace(".", "") // 1.250.000
                else s
            }
            else -> s
        }
        return s.toDoubleOrNull()
    }

    fun findSender(text: String): String? {
        SENDER_BEFORE_VERB.find(text)?.let { m ->
            val cleaned = cleanName(m.groupValues[1])
            if (cleaned != null) return cleaned
        }
        SENDER_AFTER_DE.findAll(text).forEach { m ->
            val cleaned = cleanName(m.groupValues[1])
            if (cleaned != null) return cleaned
        }
        return null
    }

    private fun cleanName(rawIn: String): String? {
        // Quita prefijos típicos: "Yape!", "Confirmación de Pago:", etc.
        var s = rawIn.substringAfterLast('!').substringAfterLast(':').trim().trim('.', ' ')
        s = s.replace(Regex("\\s+"), " ")
        val words = s.split(' ').filter { it.isNotBlank() }
            .dropWhile { normalize(it).trim('.') in NOT_NAMES }
        if (words.isEmpty()) return null
        if (normalize(words.first()).trim('.', '*') in NOT_NAMES) return null
        return words.take(3).joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    /** Texto que dirá la voz. */
    fun speech(p: Payment, sayName: Boolean): String {
        val cents = Math.round(p.amount * 100)
        val soles = cents / 100
        val cent = cents % 100
        val sb = StringBuilder()
        sb.append(p.app).append(". Recibiste ")
        sb.append(soles).append(if (soles == 1L) " sol" else " soles")
        if (cent > 0) sb.append(" con ").append(cent).append(if (cent == 1L) " céntimo" else " céntimos")
        if (sayName && !p.sender.isNullOrBlank()) sb.append(", de ").append(p.sender.replace("*", ""))
        return sb.toString()
    }
}
