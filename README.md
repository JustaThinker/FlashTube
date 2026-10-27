# FlashTube

A modern, ad-free YouTube player for Android built with Jetpack Compose and Material 3 Expressive.

No ads. Use it signed out, or sign in with Google to get your personalized recommendations,
subscriptions feed, watch history, and playlists with full likes, subscribes, and watch history sync.
Sponsor segments are automatically skipped with [SponsorBlock](https://sponsor.ajay.app).

---

## 🌟 Key Features

- **Ad-Free Playback:** Stream YouTube videos with zero ads or interruptions.
- **High-Quality Streaming & 4K Downloads:** Up to 4K (2160p) / 1440p / 1080p streaming and fast parallel chunk downloading with on-device hardware MediaMuxer.
- **Draggable Floating Mini-Player:** Seamless morphing transition between watch screen and a 2D floating mini-player you can position anywhere on screen.
- **Overlay Player Controls:** Quick ±10s double-tap seek, smooth edge gesture controls for brightness and volume, play/pause shape morphing, and auto-hiding controls.
- **Multi-Language Audio Tracks (Dubs) & Closed Captions:** 1-tap switching between multi-language YouTube audio tracks (English, Hindi, Spanish, etc.) and closed caption tracks.
- **Interactive Comment Timestamps & Timeline Chapters Carousel:** Tapping timestamps in comments instantly seeks the player, plus a horizontal slideshow carousel of chapters.
- **SponsorBlock Integration:** Automatically skip sponsor segments, self-promotions, intros/outros, and filler content with customizable skip actions.
- **Location & Content Region Selection:** Select your preferred content location (India, United States, United Kingdom, Canada, Australia, etc.) in Settings.
- **Google Sign-In & Multi-Account Support:** Access personalized Home recommendations, Subscriptions, History, Liked Videos, and Playlists with 1-tap multi-account switching. Encrypted using Android Keystore.
- **Live Stream Badges & YouTube Mix Playlists:** Red LIVE badges on live stream thumbnails and support for infinite procedural YouTube Mix playlists (`RD...`).

---

## 🛠️ Building

### Requirements
- Android SDK (`compileSdk 36`, `minSdk 26`)
- JDK 21+ (Android Studio's bundled Java SDK works)

### Gradle Commands

```sh
# Build Debug APK
./gradlew assembleDebug

# Build Release APK (R8 optimized)
./gradlew assembleRelease
```

---

## 📚 Credits & Dependencies

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor): YouTube extraction engine (GPL-3.0)
- [AndroidX Media3](https://github.com/androidx/media): Audio & video player framework (ExoPlayer)
- [Coil](https://github.com/coil-kt/coil): Image loading framework
- [SponsorBlock](https://sponsor.ajay.app): Crowd-sourced sponsor segment skipping

---

## 📄 License

FlashTube is licensed under [GPL-3.0](LICENSE).
