# AdSkipper — auto-skip YouTube ads on Android

**AdSkipper taps “Skip ad” for you the moment it appears in the YouTube app, and mutes the sound while an ad is playing.** Free, open source, no account, no tracking.

**[⬇ Download the APK](https://github.com/AleksTeslaSmart/AdSkipper/releases/latest/download/AdSkipper.apk)** · **[Website & install guide](https://claude.ai/artifact/Kxt33ovzCAr3y7yEzrCk9N)** · [All releases](https://github.com/AleksTeslaSmart/AdSkipper/releases)

Android 7.0+ · ~650 KB · 11 languages: English, Русский, Українська, Español, Deutsch, Français, Português, Türkçe, Latviešu, Lietuvių, Eesti

## What it does

- **Taps “Skip” automatically** as soon as the button becomes active. You don't touch anything.
- **Mutes ads from the first second.** It recognises the ad label with its timer (“Ad · 0:15”) and turns the sound back on when your video starts. You can switch muting off.
- **Sees only YouTube.** The service is limited to the official YouTube app (`com.google.android.youtube`).
- **Updates in place.** The app tells you when a new version is out; it installs over the old one.

Ads without a “Skip” button can't be skipped, but they will be muted.

## How it works

AdSkipper is an Android [accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service). It watches the YouTube app's screen for the skip button (by view id and by its text in many languages) and presses it. That's all: no ad lists, no network filtering, no root.

## Install

1. [Download AdSkipper.apk](https://github.com/AleksTeslaSmart/AdSkipper/releases/latest/download/AdSkipper.apk) on your phone and open it. Allow your browser to install apps when asked.
2. Open AdSkipper and follow the two buttons on the screen:
   - **Unlock** (Android 13+): in the screen that opens, tap ⋮ → *Allow restricted settings*.
   - **Turn on**: switch AdSkipper on and tap *Allow*. You're taken back to the app automatically.

The [website](https://claude.ai/artifact/Kxt33ovzCAr3y7yEzrCk9N) explains every warning your phone may show and what to tap.

## Privacy

- Reads only the YouTube app's screen, only to find the skip button and the ad label.
- Sends no data anywhere. The only network request checks GitHub for a newer version.
- No analytics, no ads, no account.

## Build

Open the folder in Android Studio, or build with Gradle 8.10+ and JDK 17:

```bash
gradle assembleDebug
```

Releases are built and signed by GitHub Actions ([build.yml](.github/workflows/build.yml)).

## License

[GPL-3.0](LICENSE). You may use, change and share AdSkipper; copies and modified versions must stay open source under the same license.

---

### Русский

AdSkipper сам нажимает «Пропустить» на рекламе в приложении YouTube и выключает звук, пока идёт реклама. Бесплатно, с открытым кодом, без регистрации и слежки.
**[Скачать APK](https://github.com/AleksTeslaSmart/AdSkipper/releases/latest/download/AdSkipper.apk)** · **[Сайт и инструкция](https://claude.ai/artifact/Kxt33ovzCAr3y7yEzrCk9N)**
