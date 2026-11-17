# FlareTune Client

[简体中文](README.md) · [English](README.en.md)

**A native music client for your own FlareTune library, focused on everyday listening.**

FlareTune Client connects to your self-hosted HTTPS [FlareTune](https://github.com/arctan303/FlareTune) instance and provides a native browsing, queue management, synchronized lyrics, and system media playback experience across phones and tablets. An Android project is currently available for local builds and is continuously checked via GitHub Actions for unit tests, Lint, and debug builds.

[Requirements and scope](#requirements-and-scope) · [Current features](#current-features) · [Platform roadmap](#platform-roadmap) · [Local build](#local-build) · [Contributing](#contributing)

> **Current status**: The Android client is under active development and has no formal release yet. Unit tests, Lint, and debug builds pass in CI, while real-device behavior across different Android devices, Bluetooth car kits, lock screens, and OEM background policies remains under ongoing verification.

## Current features

- **Instance connection and isolated multi-account support**: Sign in to an HTTPS FlareTune root origin, with support for saving and switching across multiple accounts on the same or different instances. Session cookies are encrypted locally with Android Keystore and AES-GCM, and users can update their display name and avatar.
- **Listening-focused navigation**: Organized around four primary tabs — Home, Discover, Search, and Library. Home brings together quick shortcuts, a mixed Continue Listening shelf, random recommendations, and newly added tracks proportional to your library's languages; Discover offers language filtering alongside artist and album browsing.
- **Favorites, playlists, and batch organization**: Full read/write support for personal favorites and playlists, including playlist and track reordering as well as multi-select batch actions (play next, add to queue, add to playlist, favorite, or remove).
- **Clear playback semantics**: Distinguishes contextual collection playback from single-track Play Next insertion in search and history lists (each paired with an explicit Play All action). Locally maintains an account-isolated 4-tab Play History page (Songs, Artists, Albums & Playlists, and recoverable Queue Snapshots) and listening statistics.
- **Fullscreen player and lyrics**: Responsive phone and two-column tablet player layouts with cover-driven ambient backdrops and a drag-reorderable persistent queue. Supports line- and word-synchronized lyrics, translation display toggling, and on-demand translation requests driven by your instance's server state.
- **System audio and Bluetooth lyrics**: Built on AndroidX Media3 `MediaSessionService` for background playback, notification/lock-screen controls, audio focus handling, and a sleep timer (timed or end-of-track). Includes a three-state Bluetooth Lyrics option (Off / Bluetooth audio connected / Always) that maps the active lyric line into system media metadata, plus shared artwork caching to reduce cover flicker when switching tracks.

## Platform roadmap

| Platform | Status | Notes |
| --- | --- | --- |
| **Android** | In development (buildable) | Built with Kotlin, Jetpack Compose, and Media3 (`minSdk 23` / Android 6.0+, `compileSdk` & `targetSdk 36`) |
| **Windows** | Planned | Confirmed future official desktop direction; no project created yet |
| **iOS** | Open to community | No official project or schedule at this time |

## Requirements and scope

- **Requires a running FlareTune instance**: This app ships no built-in music catalog and connects to a ready HTTPS [FlareTune](https://github.com/arctan303/FlareTune) server. Available online capabilities (such as lyric source chains or external audio sources) depend on your server version and installed plugins. See the host contract in [API.md](https://github.com/arctan303/FlareTune/blob/main/server/API.md).
- **Client scope**: Focused on personal listening and playlist organization. Instance setup, administration panels, media ingestion, and plugin configuration remain on the server/web side.
- **Not yet supported**: Offline downloads and persistent audio file caching are not implemented. No release signing keys are stored in this repository.

## Repository layout

```text
.
├── android/                  # Android Gradle project (app module and libs.versions.toml)
├── scripts/                  # Windows PowerShell helpers for building, deploying, and mirroring
└── .github/workflows/        # GitHub Actions CI (unit tests, Lint, assembleDebug)
```

## Local build

The Android project lives under `android/`, with dependency versions pinned in [`android/gradle/libs.versions.toml`](android/gradle/libs.versions.toml) (Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.06.01, Media3 1.11.1).

### Prerequisites

- **JDK**: JDK 17 or newer (JDK 21 is used in CI and local verification)
- **Android SDK**: Android SDK Platform 36 and Build Tools 36.0.0
- Configure `JAVA_HOME` and `ANDROID_HOME` via environment variables, or set your local SDK path in a gitignored `android/local.properties`

### Build and verify

From the repository root on Windows PowerShell:

```powershell
# Build debug APK
.\scripts\build-android.ps1

# Also run Android Lint
.\scripts\build-android.ps1 -Lint
```

Or inside `android/` using the Gradle Wrapper (Windows / macOS / Linux):

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Use `.\gradlew.bat` on Windows. The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk` for development and testing only.

### Device debugging scripts (Windows)

Helper scripts in `scripts/` assist with local USB-debugging devices (with `ANDROID_HOME` configured):

- `.\scripts\deploy-android.ps1`: Builds and installs the debug APK onto a single connected device and restarts the app (supports `-Serial`, `-SkipBuild`, `-Preview`).
- `.\scripts\watch-android.ps1`: Watches `android/` sources for changes and incrementally builds, reinstalls, and relaunches (supports `-Once`, `-Preview`).
- `.\scripts\mirror-android.ps1`: Launches a local `scrcpy` window for screen mirroring during testing.

## Contributing

- **Branch workflow**: Daily development happens on `dev`. Direct commits to `main` are disabled; changes enter `main` via pull requests after passing the `verify` CI check. Use short-lived branches from `dev` only when isolation is needed, and remove them after merging back and verifying.
- **Keep secrets out of Git**: Never commit credentials, session cookies, signing keystores (`*.jks`, `*.keystore`, `keystore.properties`), `android/local.properties`, or build artifacts.
- **Reporting issues**: Include your device model, Android version, server version, reproduction steps, and redacted observations.

## Related repositories and license

- Server and web app: [FlareTune](https://github.com/arctan303/FlareTune)
- Official plugins: [FlareTune-Plugins](https://github.com/arctan303/FlareTune-Plugins)

Source code and documentation in this repository are licensed under the [MIT License](LICENSE).
