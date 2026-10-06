# Contributing translations

CountAway keeps translations in ordinary Android XML resources. Translators should be able to contribute language content without editing Kotlin, Gradle, workflows, signing configuration, or release infrastructure.

## Translate the app

1. Start from the current `release/1.2.3` branch.
2. Copy the complete default `app/src/main/res/values/*.xml` translatable resource set into the correct Android language-qualified `values-...` directory.
3. Translate the user-facing text while preserving every resource `name`, XML structure, formatting argument index/type (for example `%1$s` or `%2$d`), escaping, and markup.
4. Keep plurals linguistically correct for the target language. Do not add categories only to mirror English; Android requires an `other` branch.
5. Include the language's native endonym and a short note identifying who performed or reviewed the translation as a native/proficient speaker.
6. Run `python3 scripts/verify-localizations.py`.

The validator checks completeness, duplicate/type drift, formatting contracts, accidental overrides of non-translatable resources, and the maintained language catalog. Android build/lint remains authoritative for Android resource semantics.

## Meaning matters

Translate the current product, not only the oldest countdown screens. The resource set includes count-down and count-up events, Help, errors, accessibility text, presets, backups/imports, widgets, notifications, sharing, appearance, and reliability messages.

- **Count down**: days remaining until a future event.
- **Count up**: days elapsed since a starting event.
- **Next countdown**: intentionally considers upcoming count-down events only.

Do not machine-fill missing strings or submit placeholder content just to make validation pass.

## Language activation and branding

A translation directory alone does not activate a language. Maintainers own the enabled-language catalog, runtime matching, chooser integration, packaged/offline checks, store-locale mapping, and visual branding.

If the language has a natural localized product-name idea, include suggestions and explain their meaning/tone. A localized name is optional and requires native review plus maintainer approval. Translators do not need to create Android drawables or wordmarks.

For Simplified Chinese specifically, technical matching tests do not mean Chinese is shipped. Activation requires a fresh complete current translation, native review, script/region selection checks, packaged/offline validation, UI acceptance, and an approved naming/branding fallback.
