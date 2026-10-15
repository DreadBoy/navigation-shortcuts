# Release procedure

The app is not on Google Play. Releases are signed APKs, sideloaded onto the phone. Every APK, the pre-MVP included, is signed with the same release key, so later builds install over earlier ones.

## Keystore and signing properties

| What | Where |
|---|---|
| Keystore (PKCS12, RSA 4096, ~30 years validity, `CN=Matic Leva`, alias `navigation-shortcuts`) | `~/.config/navigation-shortcuts/release.jks` |
| Signing properties | `~/.config/navigation-shortcuts/keystore.properties` |

Both live outside the repo. The directory is `chmod 700`, both files `chmod 600`. `*.jks`, `*.keystore` and `keystore.properties` are git-ignored as a second line of defence. Never commit, print or paste either file or the passwords.

`keystore.properties` has four keys (passwords are alphanumeric so the file parses cleanly with `java.util.Properties` and can be written from CI secrets):

```
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=navigation-shortcuts
keyPassword=...
```

`app/build.gradle.kts` finds the properties file in this order:

1. Gradle property `navshort.keystoreProperties` (e.g. `./gradlew -Pnavshort.keystoreProperties=/path/keystore.properties assembleRelease`)
2. Environment variable `NAVSHORT_KEYSTORE_PROPERTIES`
3. Default `~/.config/navigation-shortcuts/keystore.properties`

If the file is missing, debug builds, unit tests and `lint` still work; a release build fails at `validateSigningRelease` with a message naming the path it looked at.

## Offline backup (user)

Losing the keystore or its passwords means no update can ever be installed over the existing app: users would have to uninstall (losing their widget config) and install a new app signed with a new key.

Do this once, now:

1. Copy **both** `release.jks` and `keystore.properties` from `~/.config/navigation-shortcuts/` to offline storage, e.g. an encrypted USB stick, and/or attach them to an entry in your password manager.
2. Check the copy is readable (open the properties file on the backup medium; `keytool -list -keystore <copy>` asks for the store password).
3. Keep at least two copies in different places. Don't store them in the git repo or an unencrypted cloud folder.

## CI signing (planned, not implemented yet)

GitHub Actions secrets the user will add (repo → Settings → Secrets and variables → Actions → New repository secret):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -i ~/.config/navigation-shortcuts/release.jks` (single line) |
| `RELEASE_STORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `RELEASE_KEY_ALIAS` | `keyAlias` (`navigation-shortcuts`) |
| `RELEASE_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |

The workflow will reconstruct the files in the runner's temp directory and point the build at them:

```sh
echo "$RELEASE_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/release.jks"
cat > "$RUNNER_TEMP/keystore.properties" <<EOF
storeFile=$RUNNER_TEMP/release.jks
storePassword=$RELEASE_STORE_PASSWORD
keyAlias=$RELEASE_KEY_ALIAS
keyPassword=$RELEASE_KEY_PASSWORD
EOF
NAVSHORT_KEYSTORE_PROPERTIES="$RUNNER_TEMP/keystore.properties" ./gradlew assembleRelease
```

## Building a release locally

```sh
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Verify the signature:

```sh
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs -v app/build/outputs/apk/release/app-release.apk
```

Expect `Verifies`, `Signer #1 certificate DN: CN=Matic Leva`, and the same certificate SHA-256 digest as every previous release. The APK carries v2 and v3 signatures; with minSdk 31 apksigner only reports v3 as verified unless run with `--min-sdk-version 24`.

`versionCode` must increase with every published release (a version number is never reused).

## Sideloading onto a Pixel

1. Get the APK onto the phone: download it from the GitHub release page in Chrome, or copy it over (USB file transfer, Google Drive, `adb install app-release.apk`).
2. Open the APK (from the download notification or the Files app).
3. The first time, Android asks to allow **Install unknown apps** for the app you opened it from (Chrome, Files, Drive). Allow it once in the settings screen it opens, go back, and tap **Install**.
4. Updates: open the newer APK the same way. It installs over the old one and keeps the data only if it is signed with the same key and its `versionCode` is the same or higher. A different key fails with "App not installed" / package conflict.
5. Add the widget: long-press the home screen → Widgets → **Navigation Shortcuts**.

## Approvals

The first public push of the repo and publishing any release (GitHub Release, tag `v*`) need the user's explicit approval. Only fully verified builds are published.
