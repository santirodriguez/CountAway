# CountAway

<p align="center">
  <img src="docs/assets/branding/countaway.png" alt="CountAway" width="220" />
</p>

<p align="center">
  <strong>A lightweight Android counter for dates worth looking forward to — or remembering.</strong>
</p>

<p align="center">
  Pick a date. Add a time if you want. Put it on your home screen. Get on with your life.
</p>

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/santirodriguez/CountAway"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54" /></a>
  &nbsp;&nbsp;
  <a href="https://f-droid.org/packages/com.santiagorodriguez.countaway/"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="54" /></a>
  &nbsp;&nbsp;
  <!-- Staged for v1.2.4: wrap this badge in the verified public Play listing link at final approval. -->
  <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Google Play badge — public listing pending" height="54" />
</p>

<p align="center">
  <sub>Google Play: public listing pending. The badge link will be enabled after launch.</sub>
</p>

<p align="center">
  <a href="https://github.com/santirodriguez/CountAway/releases"><img src="https://img.shields.io/badge/GitHub-signed%20APK%20releases-181717?style=for-the-badge&logo=github&logoColor=white" alt="Signed APK releases on GitHub" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-4C9A73?style=for-the-badge" alt="Apache 2.0 license" /></a>
</p>

<p align="center">
  <sub>No accounts · no ads · no analytics · no cloud sync · no Internet permission</sub>
</p>

---

## The idea

I wanted a home-screen widget that simply showed how many days were left until something important.

Somehow, “count the days” often turns into an account, a subscription, a dashboard, and a minor career change.

CountAway skips all that. It counts the days and leaves you alone.

<table>
  <tr>
    <td width="33%" valign="top">
      <strong>Count what matters</strong><br /><br />
      Count down to future dates or count up from a starting date, with custom icons and optional times.
    </td>
    <td width="33%" valign="top">
      <strong>Keep it on Home</strong><br /><br />
      Classic day-count widgets, plus a dedicated live-hours widget for events with a time.
    </td>
    <td width="33%" valign="top">
      <strong>Keep it yours</strong><br /><br />
      Everything stays local. Backup and restore with JSON when <em>you</em> want portability. No dashboard required.
    </td>
  </tr>
</table>

## Highlights

- **Count both ways:** count down to future dates or count up from a start date.
- **Optional event times:** show live hours, minutes, and seconds, or keep counting calendar days.
- **Two kinds of widgets:** classic day-count widgets and a dedicated live-hours widget for timed events.
- **Widget-first:** long-press an event, choose a style, preview it, and add it to Home.
- **Flexible recurrence:** weekly, monthly, or yearly count-down events.
- **Nine widget backgrounds:** from clean Classic to Mist, Horizon, Sunset, Ember, Ridge, and more.
- **Three appearance modes:** System, Light, and Dark for both the app and widgets.
- **Useful reminders:** optional local notifications for count-down events.
- **Smart widgets:** fixed widgets support both directions; **Next countdown** follows the nearest upcoming count-down event.
- **Share without the screenshot ritual:** Send a Ridge-style PNG card, or full text for exceptionally long titles.
- **Local portability:** JSON export/import stays entirely under your control.
- **Multilingual:** English, Español, and Català.

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="CountAway Home in dark mode with countdown and count-up events" width="46%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="CountAway event editor with an optional Add time control" width="46%" />
</p>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="CountAway classic day-widget style configuration" width="46%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="CountAway About screen showing version 1.2.4 and local backups" width="46%" />
</p>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="CountAway Home in light mode" width="46%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="CountAway classic and live-hours widgets on an Android home screen" width="46%" />
</p>

## Privacy, by design

CountAway has no Internet permission, accounts, ads, analytics, or cloud sync.

Your events stay on your device. Revolutionary stuff, apparently.

## Help improve CountAway

Found a confusing translation, a bug, or an idea worth sharing? See [how to contribute](CONTRIBUTING.md). You don't need Android experience to help with translations and feedback.

## Build

Requires **JDK 17** and **Android SDK 36**.

```bash
./gradlew test lint assembleDebug assembleDebugAndroidTest assembleRelease
```

Maintainer signing and release steps are in [`docs/RELEASING.md`](docs/RELEASING.md), with Google Play preparation in [`docs/PLAY.md`](docs/PLAY.md) and F-Droid notes in [`docs/FDROID.md`](docs/FDROID.md).

## Author

Created by [Santiago Rodriguez](https://santiagorodriguez.com).

<a href="https://santiagorodriguez.com/donate"><img src="docs/assets/badges/donate.svg" alt="Donate" height="46" /></a>

## License

Licensed under the [Apache License 2.0](LICENSE).
