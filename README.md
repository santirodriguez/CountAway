# CountAway

<p align="center">
  <img src="docs/assets/branding/countaway.webp" alt="CountAway" width="160" />
  <img src="docs/assets/branding/ya-estamos.webp" alt="Ya Estamos" width="160" />
  <img src="docs/assets/branding/ja-queda-poc.webp" alt="Ja Queda Poc" width="160" />
</p>

CountAway is a lightweight Android countdown app for the moments you are waiting for.

The project is intentionally small and local-first. It does not include ads, analytics, accounts, cloud synchronization, or network access.

## Why

CountAway started with a very small problem: I wanted a home-screen widget that simply showed how many days were left until something important.

A surprising number of alternatives either glitched out or decided that “count the days” needed a full executive career path and fourteen unnecessary features.

So CountAway does the obvious thing: pick a date, show the number, and move on with your life.

## Status

The 1.0.0 feature set is complete. CountAway can create, edit, delete, sort, and display local countdowns using simple presets for trips, first flights, exams, parties, birthdays, events, and custom dates.

The home-screen widget supports multiple independent instances, compact/standard/large layouts, System/Light/Dark appearance, and battery-minded daily refreshes. The project is currently in release hardening before the first public release.

## Names and languages

The app uses the device locale for its visible name:

- 🇺🇸 English: **CountAway**
- 🇦🇷 Spanish: **Ya Estamos**
- <img src="docs/assets/flags/flag-catalonia.svg" alt="Catalonia flag" width="20" /> Catalan: **Ja Queda Poc**

## Requirements

- JDK 17 or newer
- Android SDK 36

## Build

```bash
./gradlew test lint assembleDebug assembleRelease
```

Debug APKs are written under `app/build/outputs/apk/debug/`.

## Distribution

Public releases will be published through GitHub Releases as a signed universal APK. Once releases begin, the repository can also be followed with Obtainium for update discovery and installation.

Maintainer release and signing steps are documented in [`docs/RELEASING.md`](docs/RELEASING.md).

## Privacy

CountAway stores its data locally on the device. The application currently declares no network permission.

## Author

Created by [Santiago Rodriguez](https://santiagorodriguez.com).

More things that probably started with “this should be simple” live there too.

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
