# CountAway

CountAway is a lightweight Android countdown app for the moments you are waiting for.

The project is intentionally small and local-first. It does not include ads, analytics, accounts, cloud synchronization, or network access.

## Status

CountAway is under active development. The current codebase contains the Android foundation, local data model, persistence layer, countdown calculation, localization scaffolding, and CI. User-facing countdown management and the home-screen widget will follow in later development phases.

## Names and languages

The app uses the device locale for its visible name:

- English: **CountAway**
- Spanish (Argentina): **Ya Estamos**
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

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
