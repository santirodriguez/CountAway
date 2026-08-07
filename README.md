# CountAway

<p align="center">
  <img src="docs/assets/branding/countaway.webp" alt="CountAway" width="160" />
  <img src="docs/assets/branding/ya-estamos.webp" alt="Ya Estamos" width="160" />
  <img src="docs/assets/branding/ja-queda-poc.webp" alt="Ja Queda Poc" width="160" />
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

- Multiple local countdowns
- Resizable home-screen widgets, including a compact 1×1 layout
- System, light, and dark widget appearance
- One-tap light/dark app appearance
- English, Spanish (Argentina), and Catalan
- Battery-minded daily refreshes
- No account or cloud dependency

## Screenshots

_Final 1.0.0 screenshots will be added from the release candidate._

## Build

Requires JDK 17 and Android SDK 36.

```bash
./gradlew test lint assembleDebug assembleRelease
```

Maintainer signing and release steps are documented in [`docs/RELEASING.md`](docs/RELEASING.md).

## Privacy

CountAway stores its data locally on the device. It does not declare Internet access.

## Author

[santiagorodriguez.com](https://santiagorodriguez.com)

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
