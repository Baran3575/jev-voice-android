package com.baran.jevvoice

import java.util.regex.Pattern

/**
 * Bilinen kurallar kodda: aday çıkarma, zaman ayrıştırma.
 * Jev sadece anlam verir; değerleri kod bulur/kopyalar.
 */
object VoiceCommandRouter {
    const val CONFIRM_THRESHOLD = 0.60
    private val PHONE_RE = Pattern.compile("(\\+?\\d[\\d\\s()\\-.]{6,}\\d)")
    private val REL_MIN = Pattern.compile("(\\d+)\\s*dakika\\s*sonra")
    private val REL_HOUR = Pattern.compile("(\\d+)\\s*saat\\s*sonra")
    private val TOMORROW_HM = Pattern.compile("yarın\\s*(\\d{1,2})(?::|\\.)?(\\d{2})?")

    fun needsConfirmation(confidence: Double): Boolean = confidence < CONFIRM_THRESHOLD

    fun findPhoneCandidates(text: String, limit: Int = 10): List<String> {
        val m = PHONE_RE.matcher(text)
        val out = LinkedHashSet<String>()
        while (m.find() && out.size < limit) {
            val digits = m.group(1).filter { it.isDigit() || it == '+' }
            if (digits.filter { it.isDigit() }.length in 7..15) out.add(m.group(1).trim())
        }
        return out.toList()
    }

    fun stripNotePrefix(text: String): String =
        text.replaceFirst("(?i)^\\s*(not al|not et|kaydet|hatırla)\\s*[:\\-]?\\s*".toRegex(), "").trim()
            .ifBlank { text.trim() }

    /** "10 dakika sonra", "2 saat sonra", "yarın 09:00" → epoch millis. Null ise varsayılan +1 saat. */
    fun parseReminderTime(text: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val lower = text.lowercase()
        REL_MIN.matcher(lower).let { if (it.find()) return nowMillis + it.group(1).toLong() * 60_000L }
        REL_HOUR.matcher(lower).let { if (it.find()) return nowMillis + it.group(1).toLong() * 3_600_000L }
        TOMORROW_HM.matcher(lower).let {
            if (it.find()) {
                val h = it.group(1).toInt().coerceIn(0, 23)
                val min = it.group(2)?.toIntOrNull() ?: 0
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, h)
                    set(java.util.Calendar.MINUTE, min)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
        }
        return nowMillis + 3_600_000L
    }

    fun moodLabel(score: Double): String = when {
        score < 0.5 -> "çok olumsuz"
        score < 1.5 -> "biraz olumsuz"
        score < 2.5 -> "nötr"
        score < 3.5 -> "olumlu"
        else -> "çok olumlu"
    }
}
