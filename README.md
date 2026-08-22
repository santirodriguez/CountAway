# CountAway

<p align="center">
  <img src="docs/assets/branding/countaway.png" alt="CountAway" width="190" />
</p>

<p align="center">
  <strong>A lightweight Android countdown for the things worth waiting for.</strong>
</p>

CountAway keeps countdowns local, simple, and visible where they are actually useful: on your home screen.

No accounts. No ads. No analytics. No cloud. No unnecessary network access.

## Why

I wanted a home-screen widget that simply showed how many days were left until something important.

A surprising number of alternatives either glitched out or decided that “count the days” needed a full executive career path and fourteen unnecessary features.

So CountAway does the obvious thing: pick a date, put it on your home screen, and move on with your life.

## Installation

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="72">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/santirodriguez/CountAway)

Or download the signed APK directly from [GitHub Releases](https://github.com/santirodriguez/CountAway/releases).

## What it does

- Multiple local countdowns with presets and custom icons
- Compact 1×1 and resizable home-screen widgets
- Fixed-event widgets or an automatic **Next countdown** mode
- Nine widget backgrounds with a live preview and system, light, and dark appearance
- Optional local reminders on the event day or 1, 3, or 7 days before
- Local JSON backup and restore through Android's document picker
- Subtle 3 · 2 · 1 · 0 arrival states
- One-tap app light/dark mode
- English, Spanish, and Catalan
- Battery-minded local refreshes

## Screenshots

<p align="center">
  <img src="docs/assets/screenshots/home-dark.webp" alt="CountAway home screen in dark mode" width="30%" />
  <img src="docs/assets/screenshots/new-countdown-dark.webp" alt="Create a countdown in dark mode" width="30%" />
  <img src="docs/assets/screenshots/home-light.webp" alt="CountAway home screen in light mode" width="30%" />
</p>

## Author

[Santiago Rodriguez](https://santiagorodriguez.com)

<a href="https://santiagorodriguez.com/donate"><img src="docs/assets/badges/donate.svg" alt="Donate" height="52" /></a>

## Privacy

CountAway stores everything on your device and has no Internet permission. It does not need your location, contacts, camera, microphone, or a suspiciously creative excuse to collect any of them. If you enable reminders, Android may ask for notification permission. Backup and restore use Android's system document picker and do not require broad storage access.

## Build

Requires JDK 17 and Android SDK 36.

```bash
./gradlew test lint assembleDebug assembleRelease
```

Maintainer signing and release steps are documented in [`docs/RELEASING.md`](docs/RELEASING.md). F-Droid preparation and submission notes are in [`docs/FDROID.md`](docs/FDROID.md).

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
