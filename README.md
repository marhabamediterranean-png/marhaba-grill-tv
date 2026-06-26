# Marhaba Grill TV (native)

A fully **native** Android app (Kotlin + Jetpack Compose + Media3/ExoPlayer) for a
24/7 restaurant wall display. No WebView, **no server, no recurring charges**.

## What it shows

- **Prayer times** for Minneapolis (AlAdhan API, ISNA method) — refreshed daily.
- **Makkah live stream** — the official **Saudi Quran TV** HLS feed (Saudi
  Broadcasting Authority), which carries the Kaaba / Masjid al-Haram live 24/7.
  Auto-reconnects and falls back to a secondary feed if the primary drops.
- **Adhan** plays automatically at each prayer time (stream mutes during it).
- **Holiday banner** — auto-detects Ramadan / Eid, same rules as before.
- **Marhaba Grill logo** in the header (replaces the old app-name text).

## How to get the APK (free — no charges)

This repo includes a GitHub Actions workflow that compiles the installable APK for
you at no cost. You do **not** need Android Studio.

1. Create a new GitHub repo (or use your existing one) and upload this whole folder.
2. Make sure the default branch is `main`.
3. Go to the **Actions** tab → run **"Build APK"** (or just push to `main`).
4. When it finishes, open the run and download the **`marhaba-grill-tv-apk`**
   artifact. Inside is `app-debug.apk`.
5. Sideload that APK onto your TV / display (USB, "Send files to TV", Downloader app, etc.).

The build produces a **debug-signed** APK, which installs on any device with no
extra signing setup — ideal for a kiosk/sideload.

## Building locally (optional)

Open the folder in **Android Studio** (Giraffe or newer) and let it sync, then
**Build → Build APK(s)**. Android Studio will generate the Gradle wrapper on first sync.

## Customizing

- **Logo:** replace `app/src/main/res/drawable-nodpi/marhaba_logo.png`.
- **Stream:** edit `MECCA_HLS_PRIMARY` / `MECCA_HLS_FALLBACK` in
  `app/src/main/java/com/mpls/salattv/MainActivity.kt`.
- **Adhan sound:** edit `ADHAN_URL` in the same file.
- **City / method:** edit `URL_STR` in `PrayerRepository.kt`.

## Stability notes (24/7)

- Screen kept on (`FLAG_KEEP_SCREEN_ON`) and immersive fullscreen.
- Live player retries indefinitely on error with a secondary fallback URL.
- Prayer data fetch retries, and refreshes itself just after midnight.
- No obfuscation/minification, so behavior is predictable.
- App id `com.mpls.salattv`, label **Marhaba Grill**, landscape, TV-launcher enabled.
