# Sijiuguan (DSH Tavern) — Android v1.9.5

**简体中文** | [English](README.en.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)]()
[![Language](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-blue.svg)]()

A **local-first AI character-card tavern for Android**. Bring your own cards, bring your own API key — no account, no server, no cloud sync. Everything (cards, chats, worldbooks, settings) stays on your device.

Works with **DeepSeek** and any **OpenAI-compatible API** (relays and multi-provider gateways included).

> For adult users only (18+), for fictional creative writing.

> **⬇️ Download the latest APK** — [direct link (always latest)](https://github.com/joker3025/sijiuguan/releases/latest/download/sijiuguan.apk)
> ｜ [release page](https://github.com/joker3025/sijiuguan/releases/latest)
> ｜ Requires Android 8.0+, debug-signed, sideload install

---

## Screenshots

| Library | Card workshop |
| --- | --- |
| ![Library](docs/screenshot1.png) | ![Workshop](docs/screenshot2.png) |

---

## Features

- **Character cards** — import/export PNG (character card V3 metadata embedded) and JSON; built-in editor; duplicate/delete/search/sort.
- **Chat** — SSE streaming, model switcher, per-message variants, continue/regenerate, checkpoints, branch storage, prompt inspector.
- **Long conversations** — automatic memory summarization + checkpoints; context budget is computed from the model's real window.
- **Worldbooks** — keyword/regex triggers, constant entries, insertion depth/order, priority, probability, global worldbook shared across cards, and a favorites library with groups.
- **AI workshop** — randomize options/synopsis via API, multi-round polish with undo/redo, import TXT/EPUB novels (full-text chunked summarization), import a screenshot with a vision model, then generate a complete card.
- **Cost & privacy** — token usage and cost estimate, daily budget, app lock (PIN), FLAG_SECURE, auto backup, offline share pack (card + chats).
- **Local only** — SQLite on device, API key encrypted with Android Keystore; no analytics, no telemetry.

## Requirements

- Android 8.0+ (API 26)
- An OpenAI-compatible API endpoint and key (DeepSeek, OpenRouter-style relays, etc.)
- For screenshot reading: a model that accepts image input

## Download

1. Download `sijiuguan.apk` from the [latest release](https://github.com/joker3025/sijiuguan/releases/latest).
2. Enable "install unknown apps" for your browser/file manager.
3. Install and open the app.
4. Go to **Settings → Provider**, fill Base URL / API Key / model, then import or create a character card.

> The direct link `https://github.com/joker3025/sijiuguan/releases/latest/download/sijiuguan.apk` always serves the newest build as long as each release keeps an asset named `sijiuguan.apk`.

## Build from source

Requirements: JDK 17+, Android SDK platform 34 + build-tools 34.0.0, Gradle 8.7.

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

`build.ps1` locates JDK / Android SDK / Gradle via `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_BIN`, copies the project to an ASCII build directory (`%TEMP%\tavern-build`) and runs `assembleDebug`.

Without the script:

```bash
# 1. create local.properties with sdk.dir
# 2. build
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Unit tests: 34 tests covering prompt building, worldbook triggering, JSON round-trips, source import, error parsing, etc.

## Privacy & security

- No account, no server, no cloud sync, no analytics.
- API keys are encrypted with Android Keystore (AES/GCM) and stored locally.
- Cards, chats, memory, worldbooks and settings live in a local SQLite database.
- Network traffic goes only to the provider you configure (chat, image reading, model list).
- Exports (PNG/JSON/share pack/backup) are created locally and only shared when you choose to.

## Known limitations

- No real-device test run has been published; verify on your own device.
- Worldbook matching is literal by default (regex optional), no recursive scanning.
- Interactive panels depend on the model following tag conventions; weak models may be unstable.
- Group chat is one model playing several characters, not multiple models.
- Losing the app-lock PIN requires clearing app data.
- AI-generated content and imported source texts are the user's responsibility.

## License

[MIT License](LICENSE) — free to use, modify and distribute with the copyright notice.

Character-card content, novels/source texts and AI-generated text are the user's own responsibility. This project is a local tool and ships no content.
