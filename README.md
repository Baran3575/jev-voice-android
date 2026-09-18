# Jev Voice Android — sesli komut + TypeSafe Jev

Android uygulaması: kullanıcı **sesli mesajla komut verir**, uygulama gerekli izinleri ister,
komutu **TypeSafe Jev (`jev-latest`)** modeline gönderir, dönen **typed yanıtı** (Choice/Noul/Score +
olasılıklar + confidence) koda göre **cihaz aksiyonuna** dönüştürür.

## Özellikler

- 🎤 Türkçe sesli komut (`SpeechRecognizer`, `tr-TR`) + yazılı yedek giriş
- 🔑 API anahtarı **uygulama içinden girilir**, `EncryptedSharedPreferences` ile **sadece telefonda** saklanır.
  Repoda, kodda, Actions loglarında anahtar **yoktur**.
- 🧠 Tek Jev çağrısında paralel sorular (speculative fan-out):
  - `komut` (Choice): `not_al | hatirlatici_kur | arama_yap | mesaj_gonder | soru_sor | diger`
  - `acil_mi` (Noul): aciliyet / zaman hassasiyeti
  - `duygu` (Score): 5 seviyeli duygu
  - Telefon numarası varsa: adayları **kod bulur** (regex), Jev **Choice ile seçer**, kod birebir kopyalar
    (pre-parsed value extraction — model numara uyduramaz)
- 📲 Cihaz aksiyonları (kurallar kodda, Jev sadece anlam verir):
  - `not_al` → telefonda nota kaydet + bildirim
  - `hatirlatici_kur` → `AlarmManager` ile bildirim (örn. "10 dakika sonra", "yarın 09:00")
  - `arama_yap` → `ACTION_DIAL` (numara adayından seçilen)
  - `mesaj_gonder` → `ACTION_SENDTO` (sms:)
  - `soru_sor` / `diger` → Jev analizini göster + sesli oku (TTS)
- 🛡️ Confidence-gated routing: güven < 0.60 ise işlem yapmadan kullanıcıya sor.
- 🔔 İzinler: `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `CALL_PHONE` (isteğe bağlı),
  `SEND_SMS` (isteğe bağlı), `READ_CONTACTS` (isteğe bağlı). Tümü runtime'da açıklamasıyla istenir.

## Kurulum (telefon)

1. Actions'tan üretilen APK'yı kur (`app-debug.apk`).
2. Uygulamayı aç → ⚙️ Ayarlar alanına [console.typesafe.ai](https://console.typesafe.ai/settings/keys)
   adresinden aldığın anahtarı yapıştır → Kaydet. Anahtar `localstorage` (EncryptedSharedPreferences)
   içinde kalır, hiçbir yere gönderilmez (sadece `api.typesafe.ai` çağrılarında `Authorization` header'ı).
3. Mikrofon + Bildirim izinlerine izin ver.
4. 🎤 butonuna bas, Türkçe konuş, örn:
   - "Not al: yarın marketten süt al"
   - "10 dakika sonra su içmemi hatırlat"
   - "0532 123 45 67 numarasını ara"
   - "0532 123 45 67 numarasına mesaj gönder: toplantı ertelendi"
   - "Bu mesaj acil mi sence?"

## Mimari (TypeSafe pattern'ları)

```
Ses (tr-TR) → transcript (state) ─┐
                                   ├─ POST /v1/systemone (jev-latest)
                                   │   { komut: Choice, acil_mi: Noul, duygu: Score }
                                   │   + gerekirse 2. çağrı: telefon_sec (Choice)
                                   ▼
                        confidence >= 0.60 ? aksiyon : kullanıcı onayı
                                   ▼
              not / alarm / dial / sms / göster + TTS
```

- Bilinen her şey kodda: regex aday çıkarma, zaman ayrıştırma (`10 dakika sonra`, `yarın 09:00`),
  intent → `Intent()` eşleşmesi, eşiğe göre onay.
- Jev sadece semantik yargı verir, metin üretmez — `choice`/`noul`/`score` + `probabilities` + `confidence`.

## GitHub Actions

`.github/workflows/android.yml`: her push/PR'de `assembleDebug` yapar, `app-debug.apk` artifact yükler.
`main`'e tag (`v*`) pushlanırsa release APK da üretilir. **Secret gerekmez** çünkü anahtar uygulamada giriliyor.

## Güvenlik notu

- Bu repoda API anahtarı yoktur ve olmamalıdır. Sohbet geçmişinde paylaşılan anahtarları
  [dashboard](https://console.typesafe.ai/settings/keys) üzerinden **rotate** edin.
- `SecureKeyStore`, anahtarı `AES256_GCM` ile şifreler (`EncryptedSharedPreferences`),
  `backup_rules.xml` ile yedeklemeye dahil edilmez.

## Yerel derleme

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
