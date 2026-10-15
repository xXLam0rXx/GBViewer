# GB Viewer

An unofficial Android client for [Gelbooru](https://gelbooru.com), built with Kotlin and Jetpack Compose.
Not affiliated with or endorsed by Gelbooru. The source site may contain adult content (18+).

## Features

- Tag search with autocomplete, separate field for excluded tags, infinite scroll, pull to refresh
- Post page with tags sorted and colored by type (artist / copyright / character / metadata), tap a tag to search, hold to copy
- Swipe between posts, fullscreen zoomable images, GIF and video playback
- Local favorites stored privately inside the app (not shown in the gallery): search by tags, multi-select delete, shuffle, backup / restore to a single file
- PIN and fingerprint lock, recent-apps preview hidden
- Light / dark / AMOLED themes with a custom accent color picker
- Languages: English, Русский, Deutsch, 한국어, 日本語, 中文
- Tag blacklist, configurable grid columns, hide Copyright / Metadata tags

## Build

1. Install [Android Studio](https://developer.android.com/studio).
2. Open this folder (**File → Open**) and wait for the Gradle sync to finish.
3. Run on a device or emulator (Android 8.0+), or build an APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Setup

Gelbooru may require authentication for API requests. In the app open **Settings** and enter your
**API key** and **User ID** (found in your Gelbooru account options). They are stored only on your device.

## Adding a language

1. Add an entry to `appLanguages` in `Localization.kt`.
2. Create `app/src/main/res/values-<code>/strings.xml` by translating `values/strings.xml`.

## Notes

- Gelbooru's image servers reject non-browser clients, so the app sends a browser-like User-Agent and a `Referer` header.
- The comments API is currently disabled on Gelbooru's side, so the comments feature is switched off in code.
- "More like this" is not part of the public API and is not implemented.

## License

[MIT](LICENSE)

---

## На русском

Неофициальный Android-клиент для Gelbooru: поиск по тегам с автодополнением, просмотр картинок, GIF и видео,
локальное избранное с резервной копией, защита PIN-кодом и отпечатком, темы и выбор цвета акцента, 6 языков.

Сборка: откройте папку в Android Studio, дождитесь синхронизации Gradle и запустите приложение.
В настройках приложения укажите свои **API key** и **User ID** от аккаунта Gelbooru.
Лицензия: MIT.
