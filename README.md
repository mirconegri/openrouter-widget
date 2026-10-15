# 📊 OpenRouter Widget

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Nothing OS](https://img.shields.io/badge/Nothing_OS-Style-000000?style=for-the-badge)](https://nothing.tech/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)
[![Orion Store](https://img.shields.io/badge/Get_it_on-Orion_Store-8A2BE2?style=for-the-badge)](https://rookieenough.github.io/Orion-Data/redirect.html?id=openrouter-widget)

Native Android home-screen widget that shows your live OpenRouter credit balance — nothing more, nothing less.

Built out of a recurring friction point: checking your OpenRouter balance meant opening a browser tab, logging in, and navigating to *Settings → Credits* — three steps for a number that should be one glance away. The widget fetches, caches, and renders that number in a monospace dot-matrix font inside a pure-black widget styled to match the Nothing OS design language. No account system, no analytics, no ads, no subscription.

## Table of Contents

- [Preview](#preview)
- [Features](#features)
- [Known Limitations](#known-limitations)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Design Decisions](#design-decisions)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Configuration](#configuration)
- [Roadmap](#roadmap)
- [Changelog](#changelog)
- [Contributing](#contributing)
- [License](#license)

## Preview

| Configuration | Post-Setup |
|:--:|:--:|
| ![Configuration](assets/config-preview.png) | ![Post-Setup](assets/post-setup.png) |
| *Paste your API key to get started* | *Display mode, threshold, and live balance* |

| Account Balance Widget | Single API Key Widget |
|:--:|:--:|
| ![Account Balance](assets/home-widget-account.png) | ![Single Key](assets/home-widget-key.png) |
| *Remaining credit — green dot while above threshold* | *Cumulative usage of one key — red dot when over budget* |

> Screenshots taken on a Nothing Phone 2a running Nothing OS 3.0 (Android 14).

## Features

- Two display modes — show either your total account balance (`total_credits − total_usage`) or the cumulative usage of a single API key
- Nothing OS aesthetic — pure black (`#000000`) or dark-grey (`#1B1B1D`) background, thin `1dp` border, `28dp` rounded corners, VT323 dot-matrix numbers, uppercase muted labels
- Color-coded alert dot — green when fine, Nothing-red when the balance drops below your custom threshold (or, in key mode, when usage exceeds it)
- Manual refresh — tap the refresh icon on the widget to trigger an immediate sync with a live `SYNC...` status indicator
- Background auto-refresh — `WorkManager` refreshes every 45 minutes when a network connection is available
- Error resilience — if the network is down or the key is invalid, the widget keeps showing the last known good value with a small red indicator instead of crashing or blanking out
- Encrypted key storage — API key persisted via `EncryptedSharedPreferences` backed by the Android Keystore, never logged, never transmitted anywhere except OpenRouter
- Resizable — supports 2×1 (compact) and 2×2 (full) layouts, `targetCellWidth="2"` on Android 12+
- Zero framework overhead — classic `RemoteViews` and XML layouts; no Jetpack Glance, no Compose, no navigation library

## Known Limitations

These are deliberate scope boundaries and OpenRouter platform constraints, not bugs:

- **Workspace mode unavailable on personal accounts** — OpenRouter only exposes `/workspaces` and `/keys` management endpoints to Business and Enterprise plans via a Provisioning API Key. On a personal Free or Standard account, that key does not exist, so workspace-level display modes are unreachable. The current release ships Account and single-key modes only.
- **OAuth not supported** — the OpenRouter OAuth flow does not accept custom URI schemes (`openrouterwidget://`) as a `callback_url`, so the browser cannot return the authorization code to the app. A full PKCE implementation was built and tested — it dead-ends silently at the redirect step. This is an OpenRouter-side constraint. The API key is pasted manually.
- **No automatic refresh below 30 minutes** — `WorkManager`'s minimum periodic interval is 15 minutes, but Android throttles background work below 30 minutes on most devices. The 45-minute interval is a battery/accuracy tradeoff. Manual refresh always works immediately.
- **`RemoteViews` used instead of Jetpack Glance** — Glance's `glance-appwidget` compiler plugin produces opaque errors on OEM skins (Nothing OS included) under AGP 8.5 + Kotlin 1.9. This was a deliberate stability decision, not a knowledge gap.
- **Single configuration per app** — multiple widget instances are supported, but they all share the same configuration. Per-widget display mode override is a tracked roadmap item.

## Tech Stack

| Dependency | Version | Notes |
|---|---|---|
| Kotlin | 1.9.24 | |
| Android Gradle Plugin | 8.5.2 | |
| Gradle | 8.7 | |
| `minSdk` | 26 | Android 8.0 — required by `EncryptedSharedPreferences` |
| `targetSdk` / `compileSdk` | 34 | Android 14 |
| `androidx.work:work-runtime-ktx` | 2.9.1 | Periodic background refresh |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | Keystore-backed encrypted preferences |
| OkHttp | 4.12.0 | HTTP client for OpenRouter API calls |
| kotlinx-coroutines-android | 1.8.1 | Async suspend functions |
| VT323 | — | Open-source dot-matrix font (SIL OFL), bundled in `res/font/` |
| GitHub Actions | — | CI pipeline — builds the debug APK on every push to `main` |

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    MainActivity (config)                     │
│  • Paste API key → validates against /api/v1/credits         │
│  • Choose mode: ACCOUNT or KEY                               │
│  • Set alert threshold ($)                                   │
│  • Saves to EncryptedSharedPreferences                       │
│  • Forces immediate widget refresh via WorkManager           │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                    RefreshWorker (WorkManager)               │
│  • Runs on network connectivity only                         │
│  • Dispatches to /credits or /key based on displayMode       │
│  • Updates cachedBalance / cachedUsage / cachedLabel         │
│  • Writes result to Prefs, then triggers WidgetUpdater       │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                    WidgetUpdater (RemoteViews)               │
│  • Reads cached values + threshold + displayMode             │
│  • Formats balance as $X.XX with monospace font              │
│  • Chooses dot color (green vs red) based on mode + threshold│
│  • Wires PendingIntents: tap → MainActivity, icon → refresh  │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                    OpenRouter API                            │
│  GET /api/v1/credits  → { total_credits, total_usage }       │
│  GET /api/v1/key      → { label, usage, limit, remaining }   │
└──────────────────────────────────────────────────────────────┘
```

### Two display modes, two threshold semantics

The alert threshold has opposite meanings depending on the active mode:

| Mode | Value rendered | Threshold means | Red dot when |
|---|---|---|---|
| ACCOUNT | Remaining credit (`total_credits − total_usage`) | "Alert me when running low" | `balance < threshold` |
| KEY | Cumulative usage of the API key | "Alert me when I've spent too much" | `usage > threshold` |

This inversion is handled in a single `when` block in `WidgetUpdater.update()` — see [`WidgetUpdater.kt`](app/src/main/java/com/nothingwidget/openrouter/widget/WidgetUpdater.kt).

## Design Decisions

**Why a native widget instead of a PWA?** A PWA cannot render on the Android home screen as a first-class widget — it can only be added as a browser shortcut, which defeats the purpose. A native `AppWidgetProvider` was the only path that puts the balance on the home screen without opening anything.

**Why `RemoteViews` instead of Jetpack Glance?** Glance is Google's recommended widget API since 2023, but its compiler plugin still produces opaque errors on OEM skins under AGP 8.5 + Kotlin 1.9. Nothing OS is one of the affected skins. `RemoteViews` with XML layouts is battle-tested across a decade of Android versions, has no such surprises, and the widget has no state that would benefit from Glance's composability. Stability over novelty.

**Why a manual API key instead of OAuth?** OpenRouter's OAuth implementation does not support custom URI schemes as a `callback_url`. The full PKCE flow was implemented, tested, and confirmed to dead-end at the redirect step — the browser stays on `openrouter.ai` and never returns the authorization code. This is a documented OpenRouter constraint. The manual key path adds one copy-paste for the user and works unconditionally.

**Why `EncryptedSharedPreferences` instead of `DataStore`?** `DataStore` is the modern replacement for `SharedPreferences` but has no encrypted variant backed by the Android Keystore. `EncryptedSharedPreferences` is deprecated in the Jetpack Security library but remains the only officially supported path for Keystore-encrypted secrets without rolling custom crypto. When Google ships an encrypted `DataStore`, this will migrate.

**Why `WorkManager` instead of `AlarmManager` or a foreground service?** `WorkManager` is the only Android API that combines persistence across reboots, automatic network constraints, doze-mode compatibility, and exponential backoff in a single package. `AlarmManager` achieves the same result with more error-prone setup; a foreground service would be disproportionate for a widget that updates twice an hour.

**Why 45 minutes and not 15?** `WorkManager`'s documented minimum is 15 minutes, but `JobScheduler` on Nothing OS and most OEM skins throttles jobs below 30 minutes when the device is idle. 45 minutes is the interval that reliably fires on schedule rather than being silently deferred. Users who need fresher data use the manual refresh — that path is always immediate.

**Why cache the last known value instead of showing an error state?** A widget that blanks out the moment Wi-Fi drops provides no value. The last known balance is almost always still relevant for the "do I have enough credit for this prompt" question, so it stays on screen with a red dot and an `OFFLINE · HH:MM` status line. The value goes blank only if the app has never successfully fetched at all.

**Why GitHub Actions instead of local builds?** The development machine is a Windows PC without the Android SDK installed. The GitHub Actions workflow runs `gradle assembleDebug` on a clean Ubuntu runner and uploads the artifact — no local toolchain required. Local builds via Android Studio remain fully supported.

## Project Structure

```
OpenRouterWidget/
├── .github/
│   └── workflows/
│       └── build-apk.yml              # CI: builds debug APK on every push to main
├── app/
│   ├── build.gradle.kts               # Module config — AGP, Kotlin, dependencies
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nothingwidget/openrouter/
│       │   ├── MainActivity.kt        # Config screen — API key, mode, threshold
│       │   ├── data/
│       │   │   ├── Prefs.kt           # EncryptedSharedPreferences wrapper
│       │   │   └── OpenRouterApi.kt   # OkHttp client and JSON parsing
│       │   ├── widget/
│       │   │   ├── OpenRouterWidgetProvider.kt   # AppWidgetProvider receiver
│       │   │   └── WidgetUpdater.kt              # RemoteViews rendering logic
│       │   └── work/
│       │       └── RefreshWorker.kt   # WorkManager periodic and manual refresh
│       └── res/
│           ├── drawable/              # Widget background, dots, refresh icon
│           ├── font/
│           │   └── vt323.ttf          # Dot-matrix display font (SIL OFL)
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── widget_openrouter.xml
│           ├── mipmap-anydpi-v26/
│           │   └── ic_launcher.xml    # Adaptive launcher icon
│           ├── values/
│           │   ├── colors.xml         # Nothing OS palette
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── xml/
│               └── widget_info.xml    # AppWidgetProvider metadata
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

## Getting Started

### Prerequisites

- An OpenRouter account with at least one API key — create one at [openrouter.ai/settings/keys](https://openrouter.ai/settings/keys)
- A device running Android 8.0 (API 26) or later — designed for Nothing OS 3.0+ but works on any launcher that supports standard `RemoteViews` widgets
- For installing the APK: enable *Install unknown apps* for your transfer source (Telegram, browser, file manager)
- For building locally: Android Studio with JDK 17, or the Android SDK and Gradle 8.7 on the command line

### Installation (pre-built APK)

1. Download the latest `app-debug.apk` from the [Actions tab](https://github.com/mirconegri/openrouter-widget/actions) → latest green run → *Artifacts* → `openrouter-widget-debug`
2. Transfer the APK to your phone — Telegram *Saved Messages* is the fastest method
3. Tap the APK → *Install* → authorize the source if prompted
4. Open **OpenRouter Widget** from the app drawer
5. Paste your API key → tap **VERIFICA E SALVA**
6. Set threshold and display mode → **SALVA E AGGIORNA WIDGET**
7. Go to the home screen → long-press an empty area → *Widgets* → find **OpenRouter** → drag to place

### Building locally

```bash
git clone https://github.com/mirconegri/openrouter-widget.git
cd openrouter-widget
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `.\gradlew.bat assembleDebug` from PowerShell. The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

If you don't have the Android SDK installed, skip local builds — the GitHub Actions workflow produces the same APK on every push to `main`.

## Usage

### First-time setup

1. **API key** — paste your `sk-or-v1-…` key from OpenRouter. The app validates it against `/api/v1/credits`; if the balance appears in the status line, the key is valid.
2. **Display mode** — choose one:
   - *Saldo Account (credito residuo)* — widget shows `$credits − $usage`
   - *Uso chiave API (questa key)* — widget shows the cumulative usage of the pasted key
3. **Threshold** — in Account mode: your "running low" alert level. In Key mode: your "spent too much" alert level.
4. Tap **SALVA E AGGIORNA WIDGET**

### Daily use

- **Tap the widget** — opens the configuration screen
- **Tap the refresh icon** (top-right corner) — forces an immediate sync; shows `SYNC...` briefly
- **Widget turns red** — balance is below threshold (Account mode) or key usage exceeded it (Key mode)
- **No other interaction needed** — the widget refreshes itself every 45 minutes automatically

### Changing the API key

Open the app — the key field is pre-filled — paste a new key — tap **VERIFICA E SALVA**. The old key is overwritten in Keystore-encrypted storage immediately.

### Logging out

Tap **LOGOUT** on the configuration screen. This clears all data in `EncryptedSharedPreferences`, including the cached balance. The widget reverts to `NON CONFIGURATO`.

## Configuration

All configuration is stored in `EncryptedSharedPreferences` under the file name `openrouter_secure_prefs`. No `.env` file, no external config, no API keys committed to the repository.

| Key | Type | Default | Description |
|---|---|---|---|
| `user_api_key` | `String` | `""` | OpenRouter API key, encrypted at rest via Android Keystore |
| `mode` | `String` | `"ACCOUNT"` | `"ACCOUNT"` or `"KEY"` |
| `threshold` | `Float` | `2.0f` | Alert threshold in USD |
| `balance` | `Float` | `-1f` | Cached balance, or cached key usage in Key mode |
| `usage` | `Float` | `-1f` | Cached total account usage |
| `label` | `String?` | `null` | Display label under the balance — `"ACCOUNT"` or the key's own label |
| `last_update` | `Long` | `0L` | Timestamp of last successful fetch (epoch ms) |
| `last_error` | `String?` | `null` | Last error message; `null` if the last fetch succeeded |

## Roadmap

Explicitly planned, not yet implemented:

1. **Per-widget display mode override** — allow different widget instances to show different modes independently
2. **Sparkline history** — store the last 7 days of balance in a rolling buffer and render a mini line chart in the 2×2 layout
3. **Multiple key shortcuts** — store several API keys and let each widget instance bind to a specific one
4. **Nothing Glyph integration** — flash the glyph LEDs red when the balance drops below threshold (`NothingIntent` API is not publicly documented; requires reverse engineering)
5. **Nothing Playground widget preset** — recreate the visual design as a Nothing Essentials widget for users who want the aesthetic without sideloading an APK
6. **Light theme variant** — a white-background inverse for users who prefer Nothing's light mode

## Changelog

| Version | Date | Highlights |
|---|---|---|
| `v2.0.1` | Oct 2026 | Fix inverted threshold semantics in Key mode; remove unused OAuth and workspace code paths |
| `v2.0` | Oct 2026 | Add single-key display mode; refactor for OpenRouter's personal-account API surface |
| `v1.0` | Oct 2026 | Initial release — account balance widget, Nothing OS styling, WorkManager refresh, GitHub Actions CI |

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes with a clear message
4. Open a Pull Request

For bugs or feature ideas, open an [Issue](https://github.com/mirconegri/openrouter-widget/issues). When reporting a widget rendering issue, include your device model, Android version, and launcher — `RemoteViews` behavior varies significantly across OEM skins.

### Author

**Mirco Negri** — Computer Science @ UniTrento

[![Portfolio](https://img.shields.io/badge/Portfolio-00599C?style=for-the-badge&logo=globe&logoColor=white)](https://mirconegri.com)
[![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/mirconegri)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/mirco-negri-263810225)
[![Gmail](https://img.shields.io/badge/Gmail-D14836?style=for-the-badge&logo=gmail&logoColor=white)](mailto:mirconegri06@gmail.com)

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
