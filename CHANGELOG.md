# Changelog

## 1.1.5

- Improve reminder delivery checks for notification permission, app-level blocking, and notification-channel availability without discarding the user's reminder choice.
- Reject reminder configurations whose scheduled date is already in the past, while keeping same-day recovery and avoiding late catch-up notifications.
- Preserve unsaved editor and widget-configuration state across Activity recreation.
- Make the widget configuration preview render the selected countdown's actual title, icon, and countdown state, including Next countdown and refreshed event edits.
- Harden local storage and backup handling with bounded reads, duplicate/blank validation, future-schema detection, compatibility-safe field limits, and safer overwrite behavior.
- Keep widgets and editor state available when local data cannot be read safely or writes fail, with localized recovery messages.
- Add regression coverage for storage validation, future schemas, reminder scheduling, widget preview content, and data-size limits.

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
- Add subtle 3, 2, 1, and 0 arrival states in the app and widgets.
- Add an optional one-time local notification when an event day arrives.
- Add a one-tap Light/Dark app appearance control.
- Keep countdown data and notifications local to the device with no accounts, ads, analytics, cloud synchronization, or Internet permission.
- Keep battery-minded widget and notification scheduling without exact alarms or foreground services.
- Enable R8 code optimization and resource shrinking for release builds.
