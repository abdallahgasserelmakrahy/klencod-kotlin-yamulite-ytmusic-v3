# NOTE — backend changed (YouTube Music, no sign-in)

The Yandex OAuth screen and Yandex Music API were removed. Music now comes from **YouTube Music** through
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (`data/yt/`): search (songs / albums / artists), album and
artist pages, and audio streams — no account, token or API key. Playback never ends: `AudioPlayer` appends YouTube "radio"
tracks when fewer than 4 remain in the queue. Favorites are stored locally (`filesDir/favorites.json`).
Spotify has no public full-track stream (DRM), but its catalogue is on YouTube Music, so searching by song name finds it.
The extractor is vendored in `newpipe-extractor/` (v0.26.5) with one patch in `utils/Utils.java` (no `URLEncoder.encode(String, Charset)`, which crashes Android < 13). If YouTube breaks search/playback, copy a newer upstream release into that module and re-apply the patch.
Everything below this note describes the old Yandex backend and is outdated where it mentions auth, tokens or Retrofit.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Unofficial Android client for Yandex.Music (`dev.pdv.yamulite`, "YaMuLite"), Kotlin + Jetpack Compose. Single-module Gradle project. See `REPORT.md` for full feature inventory and known limitations.

## Build / install / run

All Gradle invocations need `JAVA_HOME` pointing at JDK 17 (the project's Kotlin compiler/AGP target):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/yamulite-*-debug.apk
adb shell am force-stop dev.pdv.yamulite
adb shell am start -n dev.pdv.yamulite/.MainActivity
```

The APK filename includes `versionName` (set in `app/build.gradle.kts`), e.g. `yamulite-0.1.0-debug.apk` — bump `versionName`/`versionCode` there before cutting a release.

`local.properties` must contain `sdk.dir=...`. SDK setup: `~/Library/Android/sdk` with `platform-tools`, `platforms;android-36`, `build-tools;36.1.0`, `cmdline-tools;latest`, all licenses accepted.

There are no unit tests in the project at the moment — `./gradlew test` exists but does nothing useful.

## Debugging on device

Stack traces from Compose / coroutine crashes appear in `adb logcat` filtered by app PID:

```bash
adb logcat -d --pid=$(adb shell pidof dev.pdv.yamulite | tr -d '\r')
```

When the user reports a UI error, prefer screenshotting the device — error text is rendered in the screens (Search/Favorites surface API failures via "خطأ: ..." text) and is faster to read than parsing logs:

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/s.png
```

Then `Read /tmp/s.png` to view it. (`adb shell input keyevent KEYCODE_WAKEUP` is blocked on MIUI/HyperOS — ask the user to wake the screen manually.)

## Version pinning that matters

Kotlin is pinned to **2.0.21** (not 2.1.x): Hilt 2.52's metadata reader cannot parse Kotlin 2.1 metadata and `:app:hiltJavaCompileDebug` fails with `Unable to read Kotlin metadata`. KSP must match Kotlin (`2.0.21-1.0.28`). Bump Hilt to ≥2.55 first if you want to move to Kotlin 2.1.

## Architecture: how the pieces talk

### Auth gate is reactive, not navigated

`AppViewModel.gate` is a `StateFlow<AuthGate>` derived from `TokenStore.tokenFlow`. `AppRoot` switches between `AuthScreen` / `MainScreen` based on `gate.token`. There is no `nav.navigate()` for sign-in transitions — when `AuthRepository.startAuth()` writes the token to DataStore, the flow re-emits and the UI flips automatically. The `gate.ready` flag (false until first DataStore emission) prevents an auth-screen flash on cold start.

### Two Retrofit instances

- `@AuthRetrofit` (`NetworkModule`) → `https://oauth.yandex.ru/`, plain OkHttp, no auth header. Used only by `AuthApi` for device flow.
- `@MusicRetrofit` (`MusicModule`) → `https://api.music.yandex.net/`, OkHttp with `AuthInterceptor`. Used by `MusicApi`.

`AuthInterceptor` reads the token via `runBlocking { tokenStore.tokenFlow.first() }`. This blocks an OkHttp worker thread (never the UI thread), which is acceptable because DataStore reads are fast.

### Yandex API DTO quirks

The Yandex API is undocumented and inconsistent. Two patterns are load-bearing in `data/music/dto/MusicDtos.kt`:

1. **Permissive defaults.** Most fields have defaults (`id: Long = 0`, `name: String = ""`) because Yandex sometimes omits them — e.g. unknown / "various" artists have no `id`. Without defaults, kotlinx-serialization throws `Field 'id' is required`. Pair this with `Json { ignoreUnknownKeys = true; coerceInputValues = true }` in `NetworkModule`.

2. **`TrackDto.id` uses `FlexibleStringSerializer`** (`data/network/Serializers.kt`) because the same field arrives as a JSON string from `/users/{uid}/likes/tracks` but as a JSON number from `/search`. Don't change to `Long` — track ids can have `<id>:<albumId>` form in some flows.

Like/unlike returns `{"result":{"revision":N}}`, hence `ApiResponse<RevisionDto>`, not `ApiResponse<String>` — check the response shape before assuming the type.

### OAuth: device flow with embedded client_secret

`AuthRepository` uses the well-known community client_id `23cabbbdc6cd418abb4b39c32c41195d` and its publicly-known client_secret. These are not real secrets — Yandex's OAuth requires both for `grant_type=device_code`, and the same pair is hardcoded in every unofficial Yandex.Music library. Do not treat them as compromised credentials. If the user wants a private app, they can register at `oauth.yandex.ru/client/new` and supply their own pair.

### Stream URL resolution

`StreamUrlResolver.resolve(trackId, preferredBitrate)` does three things:

1. `GET /tracks/{id}/download-info` → list of `{codec, bitrate, downloadInfoUrl}` options. Picks highest-bitrate mp3 ≤ preferred.
2. `GET <downloadInfoUrl>` → tiny XML with `<host>`, `<path>`, `<ts>`, `<s>`. Parsed with regex (no XML library).
3. Computes `md5(SALT + path[1:] + s)` and assembles `https://{host}/get-mp3/{md5}/{ts}{path}`. The `SALT` (`XGRlBW9FXlekgbPrRHuSiA`) is the documented community constant.

Only `mp3` codec is implemented. Other codecs use different signed-URL prefixes (`get-aac`, etc.).

### Playback prefers local files

`AudioPlayer.playCurrent()` checks `DownloadManager.localPath(trackId)` before resolving a streaming URL. Downloaded tracks play from `file://...` with no network call. The same `AudioPlayer` instance backs all UI surfaces — `Search`, `Favorites`, and `NowPlaying` ViewModels inject it directly. The "queue" is just whichever list (search results / liked tracks) the user tapped from; that list is passed to `play(tracks, startIndex)`.

### Downloads

`DownloadManager` writes to `context.filesDir/tracks/{trackId}.mp3` (no runtime permissions needed for internal storage). It exposes a `StateFlow<Map<String, DownloadInfo>>` that the entire UI observes. Filenames replace `:` with `_` for safety; the startup scan reverses this.

Downloads run in a singleton `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and do not survive process death. There is no WorkManager.

### Coil uses the shared OkHttp

`YaMuLiteApp` implements `SingletonImageLoader.Factory` and injects the singleton `OkHttpClient` via Hilt — Coil shares the connection pool with API calls. Disk cache: 64 MB at `cacheDir/coil_covers`.

## Testing changes that touch the API

Yandex DTO surprises are the most common source of bugs. When adding endpoints or fields:

- Add new optional fields with defaults (`= null` / `= 0` / `= ""`).
- If a field can be either string or number, use `@Serializable(with = FlexibleStringSerializer::class)`.
- If unsure of the response shape, log the body via `HttpLoggingInterceptor.Level.BODY` first (currently `BASIC`).
- A failed deserialization in a coroutine launched inside a ViewModel will crash the app unless wrapped in `runCatching` — `MusicRepository.like/unlike` already do this; new mutations should follow the same pattern.

## Out of scope (intentionally not implemented)

- Background playback / `MediaSessionService` / media notification.
- Lossless / aac / opus codecs.

See `REPORT.md` § 5 for the complete list. Don't add these without explicit user request.

## UI: iOS 26 Liquid Glass design system

The whole UI follows Apple's iOS 26 design (guided by `dickwu/apple-design-skill` → `liquid-glass.md`, `materials.md`).

- **Tokens**: `ui/theme/Theme.kt` — `IosPalette` (`LocalIos`) holds iOS system colors (light/dark). No Material dynamic color. The accent is `IosPalette.tint` (systemBlue); change it in one place to rebrand. Typography is the iOS scale (34/28/22/17/15/13/12/11).
- **Two layers rule**: glass appears only in the *functional layer* (tab bar, top bar, search field, segmented control, buttons, alerts, snackbars, now-playing controls). Never put `GlassSurface` inside the `content` of `LiquidScaffold`, otherwise the backdrop would record itself.
- **`LiquidScaffold(overlay, content)`** (`components/GlassSurface.kt`): records `content` into a graphics layer and gives it to `overlay` as a `GlassBackdrop`; floating glass blurs that layer (real blur on Android 12+, opaque fallback below). `ScrollEdge` = scroll edge effect (blur + fade) under floating bars.
- **Controls** live in `components/LiquidControls.kt`: `GlassButton`, `GlassCircleButton`, `GlassSearchField`, `GlassSegmentedControl`, `LiquidAlert`, `LiquidSnackbarHost`, `IosSpinner`, `IosProgressRing`, `GroupedSection`/`GroupedCheckRow`, `ContentUnavailable`, `LiquidTopBar`, `LargeTitle`. Use them instead of Material `Button`, `AlertDialog`, `TopAppBar`, `TabRow`, `CircularProgressIndicator`, `RadioButton`.
- **Motion**: springs for press/selection (`liquidPress`), iOS push transitions with parallax in `MainScreen` (direction follows RTL), tab bar minimizes on scroll (`compact`).
- Screens draw edge-to-edge: `MainScreen` adds no top padding; each screen handles `statusBarTop()` itself.
