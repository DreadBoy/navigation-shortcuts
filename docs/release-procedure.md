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

## CI and release workflows (GitHub Actions)

### CI — `.github/workflows/ci.yml`

Triggers: push to `main`; manually (repo → Actions → **CI** → **Run workflow**, or `gh workflow run ci.yml --repo DreadBoy/navigation-shortcuts --ref main` with `gh` switched to the personal account); called by `release.yml`. The `e2e` job runs only on manual runs (see below). A newer run on the same ref cancels the older one. Permissions: `contents: read`.

| Job | What | Timeout |
|---|---|---|
| `unit` | `./gradlew testDebugUnitTest lintDebug assembleDebug` (JDK 21 Temurin, Gradle cache via `setup-java`) | 30 min |
| `e2e` | **Experimental, manual runs only** (`if: github.event_name == 'workflow_dispatch'`, `continue-on-error: true`): emulator + Niagara + Google Maps, then `e2e/run-e2e.sh` (same script as locally). Skipped on push and in `release.yml`; never blocks | 120 min (job); inside the script `SETUP_TIMEOUT=1800` s for provisioning and `E2E_TIMEOUT=2700` s for the Gradle part |
| `release-build` | `assembleRelease`, after `unit` passes (not gated on `e2e`) | 30 min |

E2E emulator (`reactivecircus/android-emulator-runner@v2`, `ubuntu-latest` with KVM): `system-images;android-36;google_apis_playstore;x86_64` (Google Play image, rev 7, ships Google Maps; local AVD is the arm64 twin), profile `pixel_7` (the runner image ships cmdline-tools 12.0, whose device list has no `pixel_8`; `pixel_7` has the same 1080×2400 screen and 420 dpi density bucket as the local `pixel_8` AVD, so the fixed tap/swipe coordinates in `setup-emulator.sh` still match; only the nominal physical size differs, 6.31″ vs 6.17″, which the emulator does not use), 2 cores (3 until run 3), 4 GB RAM, 228 MB heap, 6 GB data partition, cold boot (`-no-snapshot`, no AVD cache: same path as local runs), `-gpu software` (CI attempt 4; runs 1–3 used `swiftshader_indirect`, which emulator 36.4.9 deprecated together with `swangle_indirect` and `guest`, making `-gpu software` the supported software path, Lavapipe by default. In runs 2 and 3 the whole guest froze about 45 s after boot when setup tapped Niagara's "Agree & Continue", QEMU stayed busy (run 3: 263–314 % CPU, about 5 GB RSS, no host OOM) and exited about 37 s into the freeze; the host renderer is the remaining suspect. Run 4 with `-gpu software` (Lavapipe/SwANGLE) froze the same way, and its thread samples showed a deadlock: every QEMU thread idle except one gfxstream render thread blocked in the host kernel (D state). Attempt 5 adds `-feature -HostComposition`, so hwcomposer composes in the guest instead of through the host render pipe), `-feature -HardwareDecoder -feature -Vulkan` (CI only; in run 2 the guest froze about 20 s after Niagara's onboarding started a host-side `c2.goldfish.hevc.decoder`, and the emulator process died about 60 s after that, i.e. a hang rather than an instant crash. Two suspects, one flag each: `-HardwareDecoder` makes the guest use its software video codecs instead of the host decoders; `-Vulkan` turns off guest Vulkan on gfxstream/swiftshader, valid since emulator 35.3.11 really disables it, and HWUI already renders with `skiagl`. Emulator 36.3.10 also fixed "Vulkan composition crashes when video codecs run in gpu texture mode". `run-e2e.sh` logs the `hwcodec`/`vulkan` props after boot to show both took effect; local AVDs are unchanged), animations off on CI since attempt 5 (`disable-animations: true`, set right after boot, so Niagara's onboarding transition is a single frame; nothing in the suite or setup relies on animations; local AVDs keep them on), boot timeout 15 min. `run-e2e.sh` and `setup-emulator.sh` disable the Play Store (`pm disable-user com.android.vending`, `auto_update 0`) right after boot (decisions 1.2); `setup-emulator.sh` stops with an error if Maps is missing.

The emulator sizing (2 cores, 4 GB RAM; 2 vCPUs leave two host cores to the software renderer) assumes the standard `ubuntu-latest` runner for public repos (4 vCPU, 16 GB RAM); adjust `cores`/`ram-size` in `ci.yml` if the repo becomes private (2 vCPU, 8 GB) or moves to a larger runner.

Niagara: downloaded per run from the developer's GitHub release into `$RUNNER_TEMP` (never into the repo) and checked with `sha256sum -c`; a mismatch fails the job. Pinned in `ci.yml` (job `e2e`, `env`): `NIAGARA_VERSION=1.16.33`, `NIAGARA_SHA256=639d0578…84f3`. To bump: download the new `Niagara_Launcher_v<ver>-release.apk` from `https://github.com/NiagaraLauncher/Niagara-Issues/releases`, `shasum -a 256` it (or take the asset's `digest` from the GitHub API), update both values, run the E2E suite locally against it first.

`run-e2e.sh` env used by CI: `NIAGARA_APK` (the download), `E2E_TIMEOUT` (seconds; default 2700) and `SETUP_TIMEOUT` (seconds, for `setup-emulator.sh`; default 1800) (exit 124 on timeout, through the EXIT trap so diagnostics are kept; uses `timeout`/`gtimeout` if installed, else a watchdog), `E2E_ARTIFACTS_DIR` (logcat for the whole run; on failure also final screenshot, `uiautomator` dump, `dumpsys activity activities`, the suite's own screenshots), and for `setup-emulator.sh` `SETUP_LOOP_SCALE=3` (retry counts ×3 for its onboarding, clock long-press and Maps loops; default 1 locally; must be a positive integer), and for the instrumented suite `E2E_WAIT_SCALE=2` (its standard 5 s / 20 s UI waits ×2 via the instrumentation argument `waitScale`; integer 1–10, default 1 locally). Device-side collection happens inside the script because the runner kills the emulator after it.

Artifacts (14 days; the release APK 30 days):

| Artifact | When | Contents |
|---|---|---|
| `unit-reports` | `unit` failed | `app/build/reports/`, `anycons/build/reports/` (tests, lint) |
| `e2e-test-results` | always | `e2e/build/outputs/androidTest-results/`, `e2e/build/reports/androidTests/`, `e2e/build/outputs/run-e2e-results/` |
| `e2e-diagnostics` | `e2e` failed or cancelled | `logcat.txt`, `guest-top.log` (guest `top` every 10 s), `adb-devices.txt`, `host/` (`memory.log`: load average, memory, the QEMU process's RSS/CPU and its busiest threads every 10 s; `free`, `ps`, `dmesg`, the emulator's crash reports from `/tmp/android-*`, AVD `.ini`/logs), `final-screen.png`, `final-ui.xml`, `dumpsys-activities.txt`, `test-screenshots/` (emulator output is in the step log) |
| `release-apk` | signing secrets set | `navigation-shortcuts-<versionName>.apk` + `.sha256` |

### Why CI E2E is experimental

On the hosted `ubuntu-latest` runner the emulator (API 36 Google Play x86_64 image, software GPU, emulator 37.2.12) froze the whole guest during provisioning in runs 2–5 (about 45 s after boot, at Niagara's onboarding), and the emulator process then exited; run 4's thread samples point to a deadlock in the host-side gfxstream render path (decisions 1.7). Locally (Apple Silicon, host GPU, arm64 image) the same scripts are green. So the E2E suite stays the release gate, but it runs locally (next section's gate); the CI job is kept for manual experiments (Actions → **CI** → **Run workflow**) and its diagnostics artifact.

### Signing secrets (user)

Add in repo → Settings → Secrets and variables → Actions → **New repository secret**, or with `gh` switched to the personal account (`gh auth switch --user DreadBoy`), so nothing is printed:

| Secret | Value | Command |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | the keystore, base64, single line | `base64 -i ~/.config/navigation-shortcuts/release.jks \| gh secret set RELEASE_KEYSTORE_BASE64 --repo DreadBoy/navigation-shortcuts` |
| `RELEASE_STORE_PASSWORD` | `storePassword` from `keystore.properties` | `gh secret set RELEASE_STORE_PASSWORD --repo DreadBoy/navigation-shortcuts` (prompts) |
| `RELEASE_KEY_ALIAS` | `keyAlias` (`navigation-shortcuts`) | `gh secret set RELEASE_KEY_ALIAS --repo DreadBoy/navigation-shortcuts` |
| `RELEASE_KEY_PASSWORD` | `keyPassword` from `keystore.properties` | `gh secret set RELEASE_KEY_PASSWORD --repo DreadBoy/navigation-shortcuts` |

With all four set, `release-build` rebuilds the files in the runner's temp directory (`umask 077`, never echoed, deleted in an `always()` step) and builds signed:

```sh
umask 077; dir="$RUNNER_TEMP/signing"; mkdir -p "$dir"
printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 --decode > "$dir/release.jks"
cat > "$dir/keystore.properties" <<EOF
storeFile=$dir/release.jks
storePassword=$RELEASE_STORE_PASSWORD
keyAlias=$RELEASE_KEY_ALIAS
keyPassword=$RELEASE_KEY_PASSWORD
EOF
NAVSHORT_KEYSTORE_PROPERTIES="$dir/keystore.properties" ./gradlew assembleRelease
```

then checks `apksigner verify` (`Verifies`, `Signer #1 certificate DN: CN=Matic Leva`), renames the APK to `navigation-shortcuts-<versionName>.apk`, writes its `.sha256` and uploads both as `release-apk`.

Without the secrets (any of the four missing) the run stays green: a `::notice::` says the release is not signed, and the job runs `assembleRelease -x validateSigningRelease`, which builds an **unsigned** APK as a compile/R8/lint-vital check only; nothing is uploaded.

### Release — `.github/workflows/release.yml`

Trigger: push of a tag `v*` only (no manual trigger). Tags are pushed only with the user's approval.

1. `preflight`: fails if any of the four secrets is missing (a release must be signed), if the tag is not `v` + `versionName` from `app/build.gradle.kts`, if `versionCode` is not a positive integer, or if it is not greater than the `versionCode` at the previous `v*` tag (`git describe --tags --abbrev=0 --match 'v*' HEAD^`; no earlier tag = first release, passes).
2. `ci`: `ci.yml` with the repo secrets: `unit` and the signed `release-build`; its `e2e` job is skipped (manual runs only). The E2E gate for a release is the local run below.
3. `publish` (`contents: write` on this job only): downloads `release-apk`, re-checks the `.sha256` and the signature (`CN=Matic Leva`), and runs `gh release create v<version> --draft --verify-tag` with the APK and its `.sha256` attached.

The release is a **draft**: the user checks it on the Releases page (compare the certificate SHA-256 in the job log with earlier releases), edits the notes and clicks **Publish release**. A failed or deleted draft can be redone by deleting the tag and pushing it again only if nothing was published; a published version number is never reused — bump `versionCode`/`versionName` instead.

### Required pre-tag gate: local E2E green on the release commit

CI does not run the E2E suite for a release, so before a tag is pushed the suite must pass locally on exactly the commit that gets tagged:

```sh
git switch main && git pull --ff-only     # the commit to tag
git status --porcelain                    # must print nothing (clean tree)
git rev-parse HEAD                        # note it: this commit gets the tag
e2e/run-e2e.sh                            # local AVD navshort-e2e + vendor/niagara.apk; boots the AVD if needed
```

Pass = exit code 0 and the summary line `[run-e2e] tests=N failures=0 errors=0 skipped=0`. A red test (including a known Maps flake, decisions 1.8) means: look at it, rerun, and tag only after a fully green run on that same `HEAD`. Put the commit and the summary line into the draft release notes.

Flow for a release: bump `versionCode` and `versionName`, commit, push `main` (CI green), run the local E2E gate above on that commit, then with approval `git tag v<versionName> && git push origin v<versionName>`.

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
