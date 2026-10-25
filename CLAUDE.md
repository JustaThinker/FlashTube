# FlashTube — Specifications and Working Rules

Native Android YouTube **player** built with Kotlin, Jetpack Compose, AndroidX Media3, and NewPipeExtractor. Supports optional Google sign-in with full sync, zero ads, SponsorBlock, and background playback.

Package namespace: `com.thinker.flashtube`

## Core Architecture & Product Decisions

- **Pure Player Experience:** 4 primary navigation tabs (**Home**, **Subscriptions**, **Search**, **Library**).
- **Google Sign-In & Sync:** Optional sign-in via Google web login in WebView. Session cookies (`SAPISIDHASH`) are stored AES-GCM encrypted via Android Keystore.
- **Player & Controls:** Overlay controls with double-tap ±10s seek, draggable scrubber, auto-hiding controls, quality picker, and pinch-to-zoom / fill-screen scaling.
- **Mini-Player Morph:** Morphing container transition between watch screen and mini-player dock.
- **SponsorBlock:** Customizable skip actions (Skip, Ask, Show, Off) with audio fade transitions.
- **BotGuard & PO Token Minter:** Integrated `PoTokenWebView` minter engine for generating Proof-of-Origin tokens and `PlayerFallback` mechanism for bypassing YouTube bot checks.
- **Background Playback & PiP:** Background audio service with media notification controls and Picture-in-Picture window support.

## Project Structure (`app/src/main/java/com/thinker/flashtube/`)

- `YouTube.kt` — Data models, search/details via NewPipeExtractor, stream cache, and selection logic.
- `PlaybackService.kt` — Media3 `MediaSessionService` with ExoPlayer, DASH manifest generation, and SponsorBlock skipper.
- `PlayerState.kt` — UI-side playback state over `MediaController`.
- `PlayerUi.kt` — Portrait watch layout, action buttons, comments card, and mini-player.
- `VideoView.kt` — `VideoContent` surface, fullscreen player, scrubber, playback sheets, and aspect scaling.
- `MainActivity.kt` — App shell, navigation tabs, bottom navigation dock, and screen overlays.
- `Screens.kt` — Home, Subscriptions, Search, Library, and Playlist screens.
- `SettingsScreen.kt` — App appearance, theme selection, and playback preferences.
- `PlayerFallback.kt` — Intercepts YouTube bot-check challenges and substitutes challenge-free player responses (`TVHTML5`, `ANDROID_VR`, `WEB_EMBEDDED_PLAYER`).
- `FlashtubePoTokenProvider.kt` — Connects `PoTokenGenerator` into NewPipeExtractor.
- `potoken/` — WebView BotGuard script runner and PO Token minter engine (`PoTokenWebView.kt`, `PoTokenGenerator.kt`).
- `Account.kt` & `InnerTube.kt` — Google session manager and InnerTube web API client for feeds, comments, likes, subscriptions, and history.
- `SponsorBlock.kt` — Hash-prefix segment lookup, local caching, and skip settings.
- `UiKit.kt`, `Theme.kt`, `Motion.kt`, `Net.kt` — Shared Compose UI components, Material 3 themes, motion curves, and OkHttp network client.

## Build Commands

```sh
# Compile Kotlin debug
./gradlew :app:compileDebugKotlin

# Build Debug APK
./gradlew assembleDebug

# Build Release APK (R8 shrunk, signed)
./gradlew assembleRelease
```

Release APK output: `app/release/app-release.apk`.
