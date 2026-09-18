package com.baran.jevvoice

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var commandInput: TextInputEditText
    private lateinit var resultView: TextView
    private lateinit var keyStatus: TextView
    private var tts: TextToSpeech? = null
    private val io = Executors.newSingleThreadExecutor()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val denied = grants.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            append("⚠️ Reddedilen izin: $denied\nİşlevler kısıtlı çalışır.\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        commandInput = findViewById(R.id.commandInput)
        resultView = findViewById(R.id.resultView)
        keyStatus = findViewById(R.id.keyStatus)
        tts = TextToSpeech(this, this)

        refreshKeyStatus()
        requestNeededPermissions()

        findViewById<Button>(R.id.saveKeyButton).setOnClickListener {
            val key = apiKeyInput.text?.toString()?.trim().orEmpty()
            if (key.length < 10) {
                append("❌ Anahtar çok kısa — console.typesafe.ai/settings/keys adresinden alın.\n")
                return@setOnClickListener
            }
            SecureKeyStore.saveApiKey(this, key)
            apiKeyInput.text?.clear()
            refreshKeyStatus()
            append("✅ ${getString(R.string.key_saved)}\n")
        }

        findViewById<Button>(R.id.micButton).setOnClickListener { startListening() }
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val text = commandInput.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                append("Önce 🎤 ile konuşun veya bir komut yazın.\n")
                return@setOnClickListener
            }
            handleCommand(text)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("tr", "TR")
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        io.shutdown()
        super.onDestroy()
    }

    private fun refreshKeyStatus() {
        keyStatus.text = if (SecureKeyStore.hasApiKey(this)) {
            "🔑 Anahtar kayıtlı (sadece bu telefonda, şifreli)."
        } else getString(R.string.no_key)
    }

    // ---- İzinler: uygulamada gerekli izinleri iste ----
    private fun requestNeededPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("İzinler")
                .setMessage("Sesli komut için Mikrofon, hatırlatıcı/not için Bildirim izni gerekir. " +
                    "Arama/SMS/Rehber izinleri sadece ilgili komutta istenir.")
                .setPositiveButton("Tamam") { _, _ -> permLauncher.launch(missing.toTypedArray()) }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                append("⏰ Tam zamanlı hatırlatıcı için: Ayarlar → Özel uygulama erişimi → Alarmlar ve hatırlatıcılar → Jev Voice → İzin ver.\n")
            }
        }
    }

    // ---- Sesli giriş (tr-TR) ----
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            append("❌ Bu cihazda ses tanıma yok — komutu yazın.\n")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.listening))
        }
        append("🎤 ${getString(R.string.listening)}\n")
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                recognizer.destroy()
                if (text.isBlank()) {
                    append("❌ Ses anlaşılmadı — tekrar deneyin veya yazın.\n")
                    return
                }
                commandInput.setText(text)
                handleCommand(text)
            }
            override fun onError(error: Int) {
                recognizer.destroy()
                append("❌ Dinleme hatası ($error) — tekrar deneyin veya yazın.\n")
            }
            override fun onReadyForSpeech(p: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(r: Bundle) {}
            override fun onEvent(t: Int, p: Bundle) {}
        })
        recognizer.startListening(intent)
    }

    // ---- Komut → Jev → cihaz aksiyonu ----
    private fun handleCommand(transcript: String) {
        val apiKey = SecureKeyStore.getApiKey(this)
        if (apiKey == null) {
            append("🔑 Önce API anahtarını girip Kaydet'e basın.\n")
            return
        }
        append("\n▶ Komut: \"$transcript\"\n🧠 Jev'e soruluyor…\n")
        io.execute {
            try {
                val r = JevClient.analyze(apiKey, transcript)
                val urgentTxt = if (r.isUrgent >= 0.7) "acil" else "normal"
                val header = "📊 Jev (${r.model}): komut=${r.command} " +
                    "(güven ${pct(r.commandConfidence)}), aciliyet=${pct(r.isUrgent)} ($urgentTxt), " +
                    "duygu=${"%.2f".format(r.moodScore)} (${VoiceCommandRouter.moodLabel(r.moodScore)})\n"
                if (VoiceCommandRouter.needsConfirmation(r.commandConfidence)) {
                    runOnUiThread {
                        append(header + "⚠️ Güven düşük (${pct(r.commandConfidence)} < %60) — " +
                            "işlem yapılmadı. Komutu netleştirip tekrar deneyin.\n")
                        speak("Emin olamadım. Komutu netleştirir misin?")
                    }
                    return@execute
                }
                when (r.command) {
                    "not_al" -> {
                        val note = VoiceCommandRouter.stripNotePrefix(transcript)
                        val id = DeviceActions.saveNote(this, note)
                        runOnUiThread {
                            append(header + "📝 Not #$id kaydedildi: $note\n")
                            speak("Not kaydedildi.")
                        }
                    }
                    "hatirlatici_kur" -> {
                        val at = VoiceCommandRouter.parseReminderTime(transcript)
                        DeviceActions.scheduleReminder(this, transcript, at)
                        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR")).format(Date(at))
                        runOnUiThread {
                            append(header + "⏰ Hatırlatıcı kuruldu: $fmt — \"$transcript\"\n")
                            speak("Hatırlatıcı kuruldu.")
                        }
                    }
                    "arama_yap", "mesaj_gonder" -> {
                        val cands = VoiceCommandRouter.findPhoneCandidates(transcript)
                        var phone: String? = cands.firstOrNull()
                        var conf = r.commandConfidence
                        if (cands.size > 1) {
                            // 2. Jev çağrısı: adaylar arasından seç (model numara uyduramaz)
                            val pick = JevClient.pickPhone(apiKey, transcript,
                                "Hangi telefon numarası aranmalı/mesaj atılmalı?", cands)
                            phone = pick.phone
                            conf = minOf(conf, pick.confidence)
                        }
                        if (phone == null) {
                            runOnUiThread {
                                append(header + "❌ Metinde aranacak/mesaj atılacak numara bulunamadı.\n")
                                speak("Numara bulamadım.")
                            }
                            return@execute
                        }
                        if (conf < VoiceCommandRouter.CONFIRM_THRESHOLD) {
                            val p = phone
                            runOnUiThread {
                                append(header + "⚠️ Numara güveni düşük — onaylayın: $p\n")
                                AlertDialog.Builder(this)
                                    .setTitle("Onay")
                                    .setMessage("$p ${if (r.command == "arama_yap") "aranacak" else "numarasına mesaj yazılacak"}. Onaylıyor musunuz?")
                                    .setPositiveButton("Evet") { _, _ -> fireCallOrSms(r.command, p, transcript) }
                                    .setNegativeButton("Hayır", null)
                                    .show()
                            }
                            return@execute
                        }
                        val p = phone
                        runOnUiThread { fireCallOrSms(r.command, p, transcript) }
                    }
                    else -> {
                        runOnUiThread {
                            append(header + "💬 \"$transcript\"\n")
                            speak("Anladım. Komut türü ${r.command}.")
                        }
                    }
                }
            } catch (e: SecurityException) {
                runOnUiThread { append("❌ ${e.message}\nAnahtarı kontrol edip tekrar kaydedin.\n") }
            } catch (e: Exception) {
                runOnUiThread { append("❌ Hata: ${e.message}\n") }
            }
        }
    }

    private fun fireCallOrSms(command: String, phone: String, transcript: String) {
        if (command == "arama_yap") {
            append("📞 Arama açılıyor: $phone\n")
            speak("Arama açılıyor.")
            startActivity(DeviceActions.dialIntent(phone))
        } else {
            val body = transcript.substringAfter(":", transcript).trim()
            append("✉️ SMS açılıyor: $phone\n")
            speak("Mesaj ekranı açılıyor.")
            startActivity(DeviceActions.smsIntent(phone, body))
        }
    }

    private fun pct(v: Double): String = "%${(v * 100).toInt()}"
    private fun append(s: String) {
        runOnUiThread { resultView.append(s) }
    }
    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jev")
    }
}
