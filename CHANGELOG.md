# Changelog

## 1.2.2

- Add count-up events for tracking days since a start date across the app, fixed widgets, sharing, and local backups.
- Keep **Next countdown** focused on upcoming count-down events while fixed widgets support both count directions.
- Add an explicit, accessible language chooser for English, Español, and Català, including a return-to-System option.
- Make widget pin callbacks more reliable across supported Android versions and preserve widget configuration identity safely.
- Refine Home cards with a more compact day-count tile, more room for event titles, a subtle edge action notch, and a polished contextual actions menu with Add widget up front.
- Give confirmations and pickers a consistent CountAway dialog treatment, including clearer destructive Delete/Discard actions in light and dark themes.
- Harden contextual-menu focus and touch behavior across supported Android versions without shrinking the real 48dp action target.
- Improve large-text widget layouts and use neutral event wording where count-up and count-down modes share the same surface.
- Migrate existing local data automatically to storage schema 7; count-up events intentionally do not use recurrence or reminders.
- No new permissions, Internet access, accounts, analytics, cloud sync, or runtime dependencies.

## 1.2.1

- Restore wrapped titles and event icons in narrow, tall widgets when space permits.
- Prevent long counts from clipping in minimum-size widgets with enlarged text.
- Keep enlarged titles inside wide, short widgets instead of clipping their second line.
- Use the available height consistently in widgets and configuration previews while protecting count legibility.
- Add rendered regression checks for title, emoji, icon and count together, including enlarged text.
- Update release upgrade and APK-size checks to use the published 1.2.0 baseline.
- No new features, permissions, dependencies or data-format changes.

## 1.2.0

- Set up widgets with a long press, a live preview, nine styles, and direct Home-screen pinning.
- Repeat countdowns weekly, monthly, or yearly, including shorter months and recurring reminders.
- Share a Ridge-style image card in the selected language, with better handling of long titles, emoji, and line breaks.
- Keep edits and backup steps on track when the screen rotates or you leave and return.
- Make widgets easier to configure, scroll, and read with larger text; keep previews and daily counts up to date.
- Improve reminder timing, tidy up Help, and keep all three languages available offline.
- Keep existing data and backups compatible. Still local, lightweight, and account-free.

## 1.1.8

- Add optional yearly recurrence across the app, widgets, sharing, backups, and reminders, including February 29 fallback behavior.
- Harden local persistence and restore handling with AtomicFile recovery, strict schema/UTF-8/date validation, historical self-backup compatibility, and exact import snapshots.
- Prevent stale editors from silently overwriting or resurrecting countdowns changed elsewhere.
- Keep reminders coherent across date/time/timezone changes, bound failed delivery retries, and reconcile already-visible notifications after edits, deletes, and restores.
- Improve countdown UX with next-occurrence/reminder summaries, unsaved-draft protection, safer external intents, stronger contrast, and better large-font/IME behavior.
- Make widgets more resilient with responsive Compact/Short/Standard/Large layouts, true System-theme following, safe host-ID restore, batched data resolution, and direct pinning from a saved countdown.
- Improve functional locale fallback while preserving the existing English, Spanish, and Catalan product variants.
- Strengthen release validation with exact-SHA candidate binding, retained test/lint evidence, independent rebuild comparison, final-APK permission/runtime/native-code checks, public-asset immutability, and an explicit F-Droid publication gate.
- Keep the release lightweight and local-only: no new runtime dependencies, Internet permission, accounts, analytics, cloud sync, exact alarms, foreground services, or permanent services.

## 1.1.7

- Show elapsed days after a countdown date has passed, including fixed widgets and native sharing.
- Keep compact past-event widgets visually distinct from upcoming countdowns.
- Limit reminder choices to schedules that can still happen, while preserving unchanged historical reminder settings.
- Add regression coverage for elapsed countdowns and dynamic reminder availability.

## 1.1.6

- Add System appearance alongside Light and Dark, matching the widget's existing theme options.
- Add native countdown sharing without accounts, links, or new permissions.
- Improve accessibility for selected controls and countdown milestone states.
- Prevent different countdowns from accidentally sharing the same Android notification identity.
- Refresh F-Droid documentation to match the current inclusion workflow instead of documenting one soon-to-be-old version forever.

## 1.1.5

- Improve reminder delivery checks for notification permission, app-level blocking, and notification-channel availability, while preserving configured reminders when Android notification settings block delivery.
- Reject newly created or changed reminder configurations whose scheduled date is already in the past, while keeping existing historical reminders editable, preserving same-day recovery, and avoiding late catch-up notifications.
- Preserve unsaved editor and widget-configuration state across Activity recreation.
- Make the widget configuration preview render the selected countdown's actual title, icon, and countdown state, including Next countdown and refreshed event edits.
- Polish About with direct countdown creation, supported-launcher widget pinning, compact backup controls, and a clearer three-step layout; add a calendar cue to the empty state.
- Harden local storage and backup handling with bounded reads and encoded output, duplicate/blank validation, future-schema detection, compatibility-safe field limits, and safer overwrite behavior.
- Preserve pending backup-import confirmation across Activity recreation and revalidate the selected backup before replacing local data.
- Keep widgets and editor state available when local data cannot be read safely or writes fail, with localized recovery messages.
- Add regression coverage for storage validation, future schemas, reminder scheduling and editor behavior, widget preview content, and data-size limits.

## 1.1.2

- Add a complete 512×512 Fastlane/F-Droid store icon.
- Refresh the public CountAway branding asset used by the README.
- No functional changes.

## 1.1.1

- Improve distribution metadata and screenshot compatibility.
- No functional changes.

## 1.1.0

- Add configurable local reminders for the event day or 1, 3, or 7 days before.
- Add nine selectable widget backgrounds with automatic light and dark variants and a live configuration preview.
- Keep explicit Light and Dark widget appearances independent of the phone's system theme.
- Add a dynamic Next countdown widget mode that automatically follows the nearest upcoming event.
- Add local JSON backup export and validated restore without storage or network permissions.
- Harden local storage handling so corrupt or unsupported data is not silently overwritten.
- Keep existing 1×1 and resizable widgets, local-only storage, battery-minded refreshes, themes, and translations.

## 1.0.0

- Add local countdown creation, editing, deletion, sorting, presets, and custom event icons.
- Add localized names and UI for English, Spanish, and Catalan.
- Add resizable home-screen widgets, including a compact 1×1 layout, with independent event selection and System, Light, or Dark appearance.
- Add subtle 3 · 2 · 1 · 0 arrival states in the app and widgets.
- Add an optional one-time local notification when an event day arrives.
- Add a one-tap Light/Dark app appearance control.
- Keep countdown data and notifications local to the device with no accounts, ads, analytics, cloud synchronization, or Internet permission.
- Keep battery-minded widget and notification scheduling without exact alarms or foreground services.
- Enable R8 code optimization and resource shrinking for release builds.
