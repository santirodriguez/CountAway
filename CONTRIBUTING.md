# Contributing translations

CountAway keeps translations in ordinary Android XML resources. Translators should be able to contribute language content without editing Kotlin, Gradle, workflows, signing configuration, or release infrastructure.

## Translate the app

1. Start from the current `release/1.2.3` branch.
2. Copy the complete default `app/src/main/res/values/*.xml` translatable resource set into the correct Android language-qualified `values-...` directory. For Simplified Chinese, use `app/src/main/res/values-b+zh+Hans/`.
3. Translate the user-facing text while preserving every resource `name`, XML structure, formatting argument index/type (for example `%1$s` or `%2$d`), escaping, and markup.
4. Keep plurals linguistically correct for the target language. Do not add categories only to mirror English; Android requires an `other` branch.
5. Include the language's native endonym and a short note identifying who performed or reviewed the translation as a native/proficient speaker.
6. Run `python3 scripts/verify-localizations.py`. Candidate language directories are validated even before they are enabled in the release catalog. You can also check one explicitly, for example `python3 scripts/verify-localizations.py --resource-dir values-b+zh+Hans`.

The validator checks completeness, duplicate/type drift, formatting contracts, accidental overrides of non-translatable resources, and the maintained language catalog. Android build/lint remains authoritative for Android resource semantics.

## Meaning matters

Translate the current product, not only the oldest countdown screens. The resource set includes count-down and count-up events, Help, errors, accessibility text, presets, backups/imports, widgets, notifications, sharing, appearance, and reliability messages.

- **Count down**: days remaining until a future event.
- **Count up**: days elapsed since a starting event.
- **Next countdown**: intentionally considers upcoming count-down events only.

Do not machine-fill missing strings or submit placeholder content just to make validation pass.

## Language activation and branding

A translation directory alone does not activate a language. For a new language, do not edit `app/src/main/res/xml/supported_languages.xml`, Kotlin language-selection code, Gradle, or workflows as part of the linguistic contribution. Maintainers own catalog activation, runtime matching, chooser integration, packaged/offline checks, store-locale mapping, and visual branding.

If the language has a natural localized product-name idea, include suggestions and explain their meaning/tone. A localized name is optional and requires native review plus maintainer approval. Translators do not need to create Android drawables or wordmarks.

For Simplified Chinese specifically, keep the product name as `CountAway` in the XML unless a localized name has been explicitly approved. Name ideas and their meaning/tone can be proposed separately in the PR. Technical matching tests do not mean Chinese is shipped. Activation requires a fresh complete current translation, native review, script/region selection checks, packaged/offline validation, UI acceptance, and an approved naming/branding fallback.
