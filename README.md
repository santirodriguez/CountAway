# CountAway

<p align="center">
  <img src="docs/assets/branding/countaway.webp" alt="CountAway" width="180" />
</p>

<p align="center">
  <strong>A lightweight Android countdown for the things worth waiting for.</strong>
</p>

<p align="center">
  🇺🇸 <strong>CountAway</strong> &nbsp;·&nbsp; 🇦🇷 <strong>Ya Estamos</strong> &nbsp;·&nbsp; <img src="docs/assets/flags/flag-catalonia.svg" alt="Catalonia flag" width="18" /> <strong>Ja Queda Poc</strong>
</p>

CountAway keeps countdowns local, simple, and visible where they are actually useful: on your home screen.

No accounts. No ads. No analytics. No cloud. No unnecessary network access.

## Why

I wanted a home-screen widget that simply showed how many days were left until something important.

A surprising number of alternatives either glitched out or decided that “count the days” needed a full executive career path and fourteen unnecessary features.

So CountAway does the obvious thing: pick a date, put it on your home screen, and move on with your life.

## What it does

- Multiple local countdowns
- Resizable home-screen widgets
- Compact, standard, and large layouts
- System, light, and dark appearance
- English, Spanish (Argentina), and Catalan
- Battery-minded daily refreshes
- No account or cloud dependency

## Preview

A real app screenshot will be added from the final 1.0.0 release candidate so the README shows the shipped UI rather than a mockup.

## Install

The first public version will be distributed as a signed universal APK through GitHub Releases.

### Obtainium

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522com.santiagorodriguez.countaway%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252Fsantirodriguez%252FCountAway%2522%252C%2522author%2522%253A%2522santiagorodriguez.com%2522%252C%2522name%2522%253A%2522CountAway%2522%257D">
    <img alt="Add to Obtainium" src="https://img.shields.io/badge/Add_to-Obtainium-6C3FC5?style=for-the-badge" />
  </a>
</p>

The button points Obtainium at this GitHub repository and becomes useful as soon as the first public release is available.

Manual source URL:

```text
https://github.com/santirodriguez/CountAway
```

## Build

Requires JDK 17 and Android SDK 36.

```bash
./gradlew test lint assembleDebug assembleRelease
```

Debug APKs are written under `app/build/outputs/apk/debug/`.

Maintainer signing and release steps are documented in [`docs/RELEASING.md`](docs/RELEASING.md).

## Privacy

CountAway stores its data locally on the device. It does not declare Internet access.

## Author

[santiagorodriguez.com](https://santiagorodriguez.com)

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
