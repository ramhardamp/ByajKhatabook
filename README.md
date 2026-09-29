# Guru Shree — ByajKhatabook (Native Android)

## ⚠️ One-time setup: persistent signing key (do this before your next release)

Right now every CI build signs the APK with a brand-new random key, so installing a new build
over an older one fails ("App not installed") and forces an uninstall — which erases the old
app's local data first. Do this once and every future build will be installable as a normal
update:

1. On your own computer (needs a JDK installed), run:
   ```
   keytool -genkeypair -v -keystore release.keystore -alias release \
     -keyalg RSA -keysize 4096 -validity 10000 \
     -dname "CN=Guru Shree App, OU=Release, O=BabaSitaRam, L=Indore, ST=Madhya Pradesh, C=IN"
   ```
   It will ask you to set a password twice (store password and key password — you can use the
   same value for both). **Save this file and password somewhere safe — if you lose them you
   will be back to square one.**
2. Base64-encode it: `base64 -w0 release.keystore > release.keystore.b64` (macOS: `base64 -i release.keystore -o release.keystore.b64`)
3. In your GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**,
   and add three secrets:
   - `RELEASE_KEYSTORE_BASE64` — the contents of `release.keystore.b64`
   - `RELEASE_STORE_PASSWORD` — the store password you chose
   - `RELEASE_KEY_PASSWORD` — the key password you chose
4. Push to `main`. The workflow will now use this key automatically (it says so in the build
   log) instead of generating a throwaway one.

Do **not** commit `release.keystore` itself to the repository — GitHub Secrets is the safe place
for it.

## What this app does

Three things, for a local lending/khata business: a **customer ledger** (udhaar/jama), **byaaj**
(interest) loan tracking, and a **diary**. Everything is stored locally on the device, encrypted
with the Android Keystore.

## What changed in this update

See the in-code comments (search for `BEFORE:`/`AFTER:`) for the reasoning behind each change —
every rewritten file explains what it replaced and why. Highlights: a corrected compound-interest
formula shared by the app and the background reminder, crash-safe backup/restore, an actually
enforced PIN lock, a Material 3 light/dark UI, edit/delete + a recycle bin for every record type,
multi-business support, and a shareable multi-page PDF statement.
