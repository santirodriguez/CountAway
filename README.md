# CountAway

CountAway is a lightweight Android countdown app for the moments you are waiting for.

The project is intentionally small and local-first. It does not include ads, analytics, accounts, cloud synchronization, or network access.

## Why

CountAway started with a very small problem: I wanted a home-screen widget that simply showed how many days were left until something I was looking forward to. The options I tried either broke or had somehow turned "count the days" into a surprisingly ambitious product category.

So CountAway exists to keep that job simple: choose a date, see the number, and get on with your life.

## Status

CountAway is under active development. The current codebase contains the Android foundation, local data model, persistence layer, countdown calculation, localization scaffolding, and CI. User-facing countdown management and the home-screen widget will follow in later development phases.

## Names and languages

The app uses the device locale for its visible name:

- English: **CountAway**
- Spanish: **Ya Estamos**
- Catalan: **Ja Queda Poc**

## Requirements

- JDK 17 or newer
- Android SDK 36

## Build

```bash
./gradlew test lint assembleDebug assembleRelease
```

Debug APKs are written under `app/build/outputs/apk/debug/`.

## Distribution

Public releases will be published through GitHub Releases. Once releases begin, the repository can also be followed with Obtainium for update discovery and installation.

## Privacy

CountAway stores its data locally on the device. The application currently declares no network permission.

## Author

Created by [Santiago Rodriguez](https://santiagorodriguez.com). More things that probably started with "this should be simple" live there too.

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
