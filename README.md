# Tally for Android

The phone half of [Tally](https://hanifedma.com/tally/) — a money manager for
people whose money is in more than one currency.

Web app: [hanifedma/tally](https://github.com/hanifedma/tally)

---

## What it is

The same ledger as the website, not a copy of it. Both apps read and write the
same Postgres and both listen to the same realtime stream, so an expense
entered here appears in the browser about a second later, and the other way
round.

- Kotlin, Jetpack Compose, Material 3
- **minSdk 23** — Android 6.0, which is roughly 99% of the phones in use. As
  low as Tally can go: Credential Manager's Google sign-in needs 23, and below
  that there is no sign-in to offer.
- Google sign-in through **Credential Manager**, the current API
- Works with no signal: the ledger is cached on the device and edits are queued
- English and Korean, dark and light — the setting lives on the account, so
  changing it on a laptop changes it here

## Setup

See [**SETUP.md**](SETUP.md). It is short, because the real work is in the web
repo's [SETUP.md](https://github.com/hanifedma/tally/blob/main/SETUP.md) — one
Supabase project and one Google OAuth client serve both apps.

## Building

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew installDebug        # onto a connected device or emulator
./gradlew test                # 45 unit tests, no device needed
./gradlew connectedAndroidTest  # the screen tests; needs a device
```

Signing is described by a `keystore.properties` at the repo root, which is not
committed — see `keystore.properties.example`. Without it the release build
falls back to the debug key: still installable, still testable, just not
something to publish, and Google sign-in will refuse it because the certificate
is not the one registered.

## Layout

```
core/       Money, Dates, Calc, Compute, Models, Ids
            — the same arithmetic as the web app's money.js
data/       Supabase, LedgerRepository, LocalStore, Prefs
auth/       AuthManager — Credential Manager → Supabase
i18n/       Strings.kt — GENERATED, do not edit
ui/         theme, TallyViewModel, TallyApp, screens/, components/
```

### `Strings.kt` is generated

It is produced from the web app's `i18n.js`:

```bash
cd ../tally && node tools/gen-android-strings.mjs ../tally-android
```

Two hand-maintained copies of two hundred and forty strings drift within a
week — a key renamed on one side and the phone quietly starts showing
`tx.saveAnother` where a button should be. So the web table is the source and
this is a build artefact that happens to be committed. `ParityTest` fails if it
falls behind, or if either language is missing a key or a `{placeholder}`.

### The parity tests

`app/src/test/.../ParityTest.kt` is not a test that Kotlin does what Kotlin
does. Every expected value in it was produced by running the *web* app's
`money.js` in Node and pasting the answer in. It asserts that the phone and the
browser put the same number on the screen for the same row — the same formatted
string, the same converted amount, the same derived UUID. If one side drifts,
it fails.

### Why the release build is not minified

Ktor and kotlinx.serialization — the two libraries the whole sync layer rests
on — resolve types reflectively at the edges, and a wrong keep rule fails at
runtime on a user's phone rather than at build time here. A few extra megabytes
is the right trade for a sync layer that cannot silently work in debug and
break in release.

## A limitation worth knowing

Google sign-in needs Google Play Services. On a phone that has none — some
Huawei models, some custom ROMs — there is no way in, and Tally will say so
rather than showing a button that cannot work. The web app works on any of them.
