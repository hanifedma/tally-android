# Setting the Android app up

Most of the work is in the web repo's
[SETUP.md](https://github.com/hanifedma/tally/blob/main/SETUP.md) — one
Supabase project and one Google OAuth client serve both apps. Do that first.

What is left here is three lines and one fingerprint.

---

## 1. Point it at your project

Edit `supabase.properties` at the repo root:

```properties
supabase.url=https://YOUR-PROJECT.supabase.co
supabase.anonKey=eyJ...
google.webClientId=1234-abcd.apps.googleusercontent.com
```

All three are the same values the web app uses, and all three are public by
design — the anon key can only ever act as whoever is signed in, because every
table is behind row level security.

`google.webClientId` is the **Web application** client id, not the Android one.
Credential Manager asks Google for a token *for the web client*; the Android
client exists only so Google will issue that token to an app signed with the
right certificate.

Rebuild after editing — these become `BuildConfig` fields.

---

## 2. Register the signing certificate

Google checks the certificate that signed the APK. A debug build and a release
build have different ones, so each needs its own entry under
**Google Cloud → APIs & Services → Credentials → OAuth client ID → Android**
with package name `com.hanifedma.tally`.

Get the fingerprint of the release key:

```bash
keytool -list -v -keystore tally-release.jks -alias tally
```

The password is in `keystore.properties`, which is not committed.

For a debug build, the fingerprint is the debug keystore's:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

---

## 3. Keep the key safe

`tally-release.jks` and `keystore.properties` are both gitignored, on purpose.
Back them up somewhere private.

Android identifies an app by its signing certificate. Lose that key and you can
never ship an update to an existing install — the only way forward is a new
package name, which means a new app and a new entry in Google Cloud.

---

## Building

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Copy the APK to the phone and open it. Android will ask you to allow installing
from that source, once.

## If sign-in does not work

| What you see | What it means |
|---|---|
| "No Google account is available on this device" | No account signed in, or no Play Services. |
| The chooser appears, then it fails | The SHA-1 registered does not match the certificate the APK was signed with. |
| "Sign-in isn't configured yet" | `google.webClientId` is still a placeholder, or does not end in `.apps.googleusercontent.com`. |
| A "provider" or "audience" error in logcat | The web client id is missing from Supabase → Authentication → Google → **Authorised Client IDs**. |
| "One setup step is left" on launch | `supabase.url` or `supabase.anonKey` is still a placeholder. |
