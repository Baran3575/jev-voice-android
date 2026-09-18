package com.baran.jevvoice

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Minimal TypeSafe SystemOne istemcisi (HttpURLConnection — ek bağımlılık yok).
 * POST https://api.typesafe.ai/v1/systemone
 */
object JevClient {
    const val ENDPOINT = "https://api.typesafe.ai/v1/systemone"
    const val MODEL = "jev-latest"

    data class JevResult(
        val model: String,
        val command: String,
        val commandProbs: Map<String, Double>,
        val commandConfidence: Double,
        val isUrgent: Double,
        val moodScore: Double,
        val moodConfidence: Double,
        val raw: String
    )

    data class PhonePick(
        val phone: String?,
        val confidence: Double,
        val exists: Double,
        val raw: String
    )

    fun analyze(apiKey: String, message: String): JevResult {
        val state = JSONObject()
            .put("message", message)
            .put("locale", "tr-TR")
            .put("app", "jev-voice-android")

        val questions = JSONObject()
        questions.put("komut", JSONObject()
            .put("type", "choice")
            .put("instructions", "What does the user want the phone to do? Pick exactly one.")
            .put("criteria", JSONObject()
                .put("not_al", "User wants to save a note, reminder text, shopping list, memo. E.g. 'not al', 'kaydet', 'hatırla'.")
                .put("hatirlatici_kur", "User wants a timed reminder/alarm/notification. E.g. 'hatırlat', 'alarm kur', '10 dakika sonra'.")
                .put("arama_yap", "User wants to place a phone call. E.g. 'ara', 'telefon aç', contains a phone number to call.")
                .put("mesaj_gonder", "User wants to send an SMS/message. E.g. 'mesaj gönder', 'sms at', 'yaz'.")
                .put("soru_sor", "User asks a question or wants information/analysis. E.g. 'nedir', 'mi?', 'sence?'.")
                .put("diger", "Anything else that fits none of the above.")
            ))
        questions.put("acil_mi", JSONObject()
            .put("type", "noul")
            .put("instructions", "Does this message convey urgency or time-sensitivity?")
            .put("criteria", JSONObject()
                .put("true", "Explicitly time-sensitive, ASAP, emergency")
                .put("false", "No urgency expressed")))
        questions.put("duygu", JSONObject()
            .put("type", "score")
            .put("instructions", "How positive does the user message feel?")
            .put("criteria", org.json.JSONArray(listOf(
                "Very negative, angry or upset",
                "Slightly negative",
                "Neutral, just stating facts",
                "Slightly positive, friendly",
                "Very positive, enthusiastic or grateful"
            ))))

        val body = JSONObject()
            .put("state", state)
            .put("model", MODEL)
            .put("questions", questions)

        val resp = post(apiKey, body.toString())
        val json = JSONObject(resp)
        val answers = json.getJSONObject("answers")
        val cmd = answers.getJSONObject("komut")
        val probs = mutableMapOf<String, Double>()
        val pj = cmd.getJSONObject("probabilities")
        for (k in pj.keys()) probs[k] = pj.getDouble(k)
        return JevResult(
            model = json.optString("model", MODEL),
            command = cmd.getString("choice"),
            commandProbs = probs,
            commandConfidence = cmd.getDouble("confidence"),
            isUrgent = answers.getJSONObject("acil_mi").getDouble("noul"),
            moodScore = answers.getJSONObject("duygu").getDouble("score"),
            moodConfidence = answers.getJSONObject("duygu").getDouble("confidence"),
            raw = resp
        )
    }

    /** Aday telefonlar kod tarafından bulunur; Jev sadece Choice ile seçer (model numara uyduramaz). */
    fun pickPhone(apiKey: String, document: String, question: String, candidates: List<String>): PhonePick {
        val criteria = JSONObject()
        for (c in candidates) criteria.put(c, JSONObject.NULL)
        criteria.put("hicbiri", "None of the candidates answers the question.")
        val questions = JSONObject()
        questions.put("sec", JSONObject()
            .put("type", "choice")
            .put("instructions", "Hangi aday bu soruyu yanıtlıyor: \"$question\"? Yalnızca metne göre seç. / Which candidate answers?")
            .put("criteria", criteria))
        questions.put("varmi", JSONObject()
            .put("type", "noul")
            .put("instructions", "Does the document contain an answer to: \"$question\"?"))
        val body = JSONObject()
            .put("state", JSONObject().put("document", document))
            .put("model", MODEL)
            .put("questions", questions)
        val resp = post(apiKey, body.toString())
        val json = JSONObject(resp)
        val sec = json.getJSONObject("answers").getJSONObject("sec")
        val pick = sec.getString("choice")
        return PhonePick(
            phone = if (pick == "hicbiri") null else pick,
            confidence = sec.getDouble("confidence"),
            exists = json.getJSONObject("answers").getJSONObject("varmi").getDouble("noul"),
            raw = resp
        )
    }

    private fun post(apiKey: String, jsonBody: String): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(jsonBody.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
        conn.disconnect()
        if (code == 401) throw SecurityException("API anahtarı geçersiz (401).")
        if (code == 429) throw IllegalStateException("Hız limiti (429) — biraz bekleyip tekrar deneyin.")
        if (code !in 200..299) throw IllegalStateException("Jev hatası HTTP $code: ${text.take(300)}")
        return text
    }
}
