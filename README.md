# FlashTube

A modern, ad-free YouTube player for Android built with Jetpack Compose and Material 3 Expressive.

No ads. Use it signed out, or sign in with Google to get your personalized recommendations,
subscriptions feed, watch history, and playlists with full likes, subscribes, and watch history sync.
Sponsor segments are automatically skipped with [SponsorBlock](https://sponsor.ajay.app).



## Download

**[FlashTube Release APK](https://github.com/JustaThinker/flashtube/raw/main/app/release/app-release.apk)**: Always the latest release build. Open the file on your Android device and allow installation from your browser or file manager.

## Features

- **Ad-Free Playback:** Stream YouTube videos with zero ads or interruptions.
- **High-Quality Streaming:** Up to 1080p / 60fps streaming using generated DASH manifests to bypass YouTube throttling.
- **Overlay Player Controls:** Quick ±10s double-tap seek, interactive scrubber, play/pause shape morphing, and auto-hiding gesture controls.
- **Seamless Mini-Player:** Morphing transition between full watch screen and floating mini-player. Swipe down to minimize, swipe up to expand.
- **Zoom to Fill & Aspect Ratio:** Pinch to zoom or toggle aspect mode to eliminate side black bars on ultrawide displays.
- **Background Audio & PiP:** Picture-in-Picture mode and background audio playback with media notification controls.
- **SponsorBlock Integration:** Automatically skip sponsor segments, self-promotions, intros/outros, and filler content with customizable skip actions.
- **Google Sign-In & Full Sync (Optional):** Access personalized Home recommendations, Subscriptions, History, Liked Videos, and Playlists. Account data stays encrypted using Android Keystore.
- **Comments & Replies:** View and post comments with real-time reply threads.
- **Downloads Section:** Built-in Downloads screen in Library for local offline content.
- **Custom Themes & Material You:** Dynamic wallpaper colors (Material You) with dark/light mode themes and custom accent palettes.

## Building

### Requirements
- Android SDK (`compileSdk 36`, `minSdk 26`)
- JDK 17+ (Android Studio's bundled Java SDK works)

### Gradle Commands

```sh
# Build Debug APK
./gradlew assembleDebug

# Build Release APK (R8 optimized)
./gradlew assembleRelease
```

The compiled release APK will be located at `app/release/app-release.apk`.

## Project Structure

```
app/src/main/java/com/thinker/flashtube/
  YouTube.kt                 Search + video details (NewPipeExtractor), stream cache
  PlaybackService.kt         Media3 session service, DASH manifest generator
  PlayerState.kt             UI playback state manager over MediaController
  PlayerUi.kt                Watch screen layout, overlay controls, mini-player
  VideoView.kt               Video surface, fullscreen player, scrubber, playback sheets
  MainActivity.kt            App shell, tab navigation, floating nav dock
  Screens.kt                 Home, Subscriptions, Search, Library screens
  SettingsScreen.kt          App settings & appearance customization
  PlayerFallback.kt          BotGuard & player fallback handler for rate-limits
  FlashtubePoTokenProvider.kt PO Token minter integration for NewPipeExtractor
  potoken/                   BotGuard PO token WebView minter engine
  Account.kt & InnerTube.kt  Google account authentication and InnerTube API client
  SponsorBlock.kt            SponsorBlock segment skipper and settings
  UiKit.kt, Theme.kt, Net.kt Shared UI components, color palettes, and OkHttp client
```

## Credits & Dependencies

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor): YouTube extraction engine (GPL-3.0)
- [AndroidX Media3](https://github.com/androidx/media): Audio & video player framework (ExoPlayer)
- [Coil](https://github.com/coil-kt/coil): Image loading framework
- [SponsorBlock](https://sponsor.ajay.app): Crowd-sourced sponsor segment skipping

## License

FlashTube is licensed under [GPL-3.0](LICENSE).

*Disclaimer: FlashTube is an independent open-source project and is not affiliated with, sponsored by, or endorsed by Google or YouTube.*
