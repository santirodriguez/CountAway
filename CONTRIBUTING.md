<p align="center">
  <img src="docs/assets/branding/countaway.png" alt="CountAway app icon" width="100" />
</p>

<h1 align="center">Contribute to CountAway</h1>

<p align="center">
  Good translations, useful feedback, and small improvements make CountAway better.<br />
  You don't need to know Android to help.
</p>

<p align="center">
  <a href="#how-you-can-help">Ways to help</a> ·
  <a href="#translate-countaway">Translate</a> ·
  <a href="#open-a-pull-request">Send a translation</a> ·
  <a href="#before-you-submit">Checklist</a>
</p>

---

## How you can help

| I'd like to… | Where to start |
| --- | --- |
| **Translate or improve wording** | Follow the [translation guide](#translate-countaway). Android programming isn't required. |
| **Report a bug or unclear text** | [Open an issue](https://github.com/santirodriguez/CountAway/issues/new) with what happened and how to reproduce it. |
| **Suggest an improvement** | [Share the idea in an issue](https://github.com/santirodriguez/CountAway/issues/new) before starting a larger change. |
| **Improve documentation or code** | Start from the current `main` branch and open a focused pull request. See [the README](README.md#build) for build instructions. |

Not ready to open a pull request? A clear bug report or a suggestion about awkward wording is already helpful.

## Translate CountAway

CountAway uses standard Android XML text files. **You provide the language; the maintainer handles the Android integration**, including the language picker, icons, packaging, and release setup.

You can edit translation files on GitHub or in any text editor. Android Studio is not required for translation work.

### 1. Start from the latest code

Fork the [CountAway repository](https://github.com/santirodriguez/CountAway), update your fork from `main`, and create a branch such as `translation/<language-tag>`.

If you're new to pull requests, GitHub's [fork and pull request guide](https://docs.github.com/en/get-started/start-your-journey/hello-world) walks through the basics.

### 2. Create your language folder

The original English text is spread across the XML files in:

`app/src/main/res/values/`

Create the matching Android language-qualified folder inside `app/src/main/res/` (for example, `values-fr/`). Use a script-aware qualifier when your language needs one. If you're unsure which Android qualifier to use, [ask in an issue](https://github.com/santirodriguez/CountAway/issues/new) first.

Translate **all current translatable** `string`, `plurals`, and `string-array` resources, not just `strings.xml`. Keep the XML file grouping where practical. Skip styling, colors, and values marked `translatable="false"`.

### 3. Translate the meaning, not just the words

- Keep each resource's `name`, XML structure, formatting placeholders (`%1$s`, `%2$d`), and markup intact.
- Keep `string-array` entries in the same order and count.
- Use plural categories that make sense in your language; every plural resource needs an `other` form.
- Cover **countdowns and count-up events**, widgets, reminders, Help, accessibility, backup/restore, sharing, and other current screens.
- Leave the product name as **CountAway**. You can suggest a localized name in the PR, explaining its meaning and tone.
- Write natural text, ideally by a native or proficient speaker. Translation tools can assist, but don't submit unreviewed machine output or placeholder text.

<details>
<summary><strong>XML examples and terminology notes</strong></summary>

A resource key stays the same even when its text changes:

```xml
<string name="action_save">Save</string>
```

The `name="action_save"` attribute must stay unchanged. The visible word `Save` is what you translate.

Formatting tokens are part of the app's contract. In strings such as `%1$d days` or `%1$s · %2$s`, keep the original indexed placeholders and their types. Preserve XML escaping and any nested markup.

Some CountAway terms have specific meanings:

| In the app | Meaning |
| --- | --- |
| **Count down** | Days remaining until an upcoming event. |
| **Count up** | Days elapsed since an event's starting date. |
| **Next countdown** | The nearest upcoming countdown event; it doesn't include count-up events. |

</details>

### 4. Check the translation

From the repository root, run:

```bash
python3 scripts/verify-localizations.py --resource-dir values-fr
```

Replace `values-fr` with your own language folder. The script uses Python 3 and checks completeness, duplicate or unexpected resources, and formatting contracts. It also validates the existing languages.

If you can't run it yourself, say so in the PR. Automated checks will run on the proposed changes; the maintainer will help with integration issues.

## Open a pull request

Open a **new pull request against `main`** with the current translation resources. Keep it focused on the translation and any closely related linguistic corrections.

You can copy this short description into your PR:

```text
Language and native name:
Language tag / Android resource folder:
Written or reviewed by a native/proficient speaker:
Validation run (or not run):
Terminology or plural choices worth checking:
Translation tools used, if any:
Optional localized product-name suggestion:
```

There's no need to edit `supported_languages.xml`, Kotlin, Gradle, workflows, signing files, flags, or branding. The maintainer will review the wording, check the packaged language, and handle activation.

For a new contribution, work from current `main` rather than copying outdated integration code from an old PR. There is no guaranteed release date for adding a language.

## Before you submit

- [ ] I used the latest `main` resources and included all translatable text.
- [ ] Resource names, XML structure, placeholders, arrays, and plurals are correct.
- [ ] The translation reads naturally and has received appropriate language review.
- [ ] I kept the change limited to language resources and related linguistic fixes.
- [ ] I included the language details and validation result in the PR.

Thanks for helping make CountAway easier to use.
