# PLAN — Navigation Shortcuts Widget

Spec: `nav-widget-spec.md`. Decisions: `docs/decisions.md`. Rules: `CLAUDE.md`.
A step is ticked only after verification, with a one-line summary of how it was verified.
"Done" for v1 = the E2E suite (spec §Testing, scenarios 1–7) passes on the emulator against Niagara, locally and in CI.

Legend: **[M]** measure-and-decide (experiment → thresholds → decision in `docs/decisions.md` → then build), **[B]** build, **[U]** needs the user.

---

## v1 — MVP

### Phase 0 — Repo and project skeleton
- [x] 0.1 Init git with personal identity, commit spec as-is, `CLAUDE.md`, `.gitignore` (`vendor/`, keystores, build output). — `git log` shows both commits as `Matic Leva <vertical3life@gmail.com>`.
- [ ] 0.2 [B] Android project: Kotlin, single `app` module, Gradle version catalog, `applicationId` decided and recorded, `minSdk 31` (needed for `widgetFeatures="reconfigurable"` and `requestPinAppWidget` behaviour), `targetSdk` = latest stable. Debug build installs on an emulator.
- [ ] 0.3 [M] Widget rendering technology: plain `RemoteViews` (nested `LinearLayout` rows built per grid size) vs Jetpack Glance. Criteria: arbitrary rows×cols, transparent empty cells, per-cell `PendingIntent`, icon bitmaps, APK size. Recommendation going in: plain `RemoteViews` (fewer moving parts, full control over transparency). Record decision.
- [ ] 0.4 [M] Storage: Preferences DataStore vs Room for per-widget config `{widgetId → rows, cols, [entries{label, destination, icon{source,name}}]}` plus the pending list. Recommendation going in: DataStore with a serialized JSON/proto per widget. Record decision.

### Phase 1 — E2E harness first (the gate everything else is verified through)
- [ ] 1.1 [M] Niagara APK: find the package name, download from a public mirror into `vendor/niagara.apk` (git-ignored). If the download is blocked, stop this sub-step and ask the user to drop it in `vendor/`.
- [ ] 1.2 [M] Emulator image: Pixel profile, Google Play system image. Measure: is Google Maps preinstalled and does `google.navigation:q=…&mode=d` reach the navigation/route screen without sign-in? Which first-run dialogs (terms, location permission, "driving mode" tips) appear, and how to dismiss them reliably? Set a mock location so routes exist. Record findings.
- [ ] 1.3 [M] Niagara on the emulator: sideload, `cmd package set-home-activity`, dismiss onboarding. Explore once and record UI Automator selectors for: home screen, long-press menu, widget picker, finding our widget, placing into the widget stack below a notes widget, swiping the stack, widget long-press menu. Record in `docs/decisions.md`.
- [ ] 1.4 [M] Niagara capabilities: (a) does long-press on a widget offer "Reconfigure" (`widgetFeatures="reconfigurable"`)? (b) does Niagara honour `requestPinAppWidget`? (c) does Niagara launch the config activity on placement? Decide which Flow 3 / Flow 2 entry points v1 keeps (spec scenario 7).
- [ ] 1.5 [B] UI Automator 2.3+ test module with helpers: launcher setup, place widget, find widget cell by label, wait for Maps navigation foreground, force-stop app. Proven by a smoke test that places a stub widget on Niagara's home screen.
- [ ] 1.6 [M] CI feasibility: GitHub Actions Linux runner with KVM, headless emulator (`-no-window -no-boot-anim`, wait for `sys.boot_completed`), boot time and suite runtime. **Open problem to decide:** CI needs the Niagara APK but it must not be committed. Options: download from mirror in CI (fragile), cache keyed by version, or a private storage location. Record decision and the mirror/version pinning.

### Phase 2 — Core widget (Flow 1, Flow 4, scenarios 1, 2, 3, 5)
- [ ] 2.1 [B] Data model + repository (per-widget config, icon reference `{source, name}`, cleanup on `onDeleted`).
- [ ] 2.2 [B] Built-in icon set: small bundled Material Symbols set (home, work, gym, stadium, school, pool, shop, generic pin) as vectors. Icon resolver `{source,name} → bitmap`.
- [ ] 2.3 [B] Widget provider: render rows×cols, icon + label per filled cell, transparent empty cells, entries fill left-to-right/top-to-bottom; filled tap → `google.navigation:q=<dest>&mode=d` with Maps package set; empty tap → config activity for that widget ID. Resizes sensibly in Niagara's stack.
- [ ] 2.4 [B] Config activity: grid size (default 1×4), destination list with add manually / edit / delete / drag reorder, icon picker, tip about sharing from Maps; opens on placement and on empty-cell tap; save updates the widget immediately; saving with zero entries allowed.
- [ ] 2.5 [B] E2E scenarios 1, 2, 3, 5 green on Niagara.

### Phase 3 — Share from Google Maps (Flow 2, scenario 3b)
- [ ] 3.1 [M] Capture what Maps actually shares on the emulator (text format, short-link host) and where the short link redirects to (URL shapes with `/@lat,lng,` and `!3d…!4d…`). Collect ~10 real samples (address, business, POI, dropped pin) as test fixtures.
- [ ] 3.2 [B] Link resolver: follow redirect once at add time, parse coordinates + place name, fall back to place-name query on failure (offline, unknown shape) and say so. Unit tests over the fixtures. `INTERNET` permission.
- [ ] 3.3 [B] Share target: `ACTION_SEND text/plain`, bottom-sheet-styled activity labelled "Add to navigation widget"; label prefilled, resolved destination shown, icon guessed from place category when available + one tap to picker.
- [ ] 3.4 [B] Widget chooser: one widget → hidden; several → chips showing "Label · Label · Label"; none → "Place new widget" via `requestPinAppWidget` if 1.4(b) says Niagara supports it, else pending list + explanation, and Flow 1 prefills pending destinations.
- [ ] 3.5 [B] Grid full → offer "+1 column / +1 row" in one tap or open full config.
- [ ] 3.6 [B] E2E scenario 3b green; plus "share with no widget placed" per 1.4.

### Phase 4 — Editing entry points (Flow 3, scenarios 4, 7)
- [ ] 4.1 [B] App launcher activity: list of placed widgets (thumbnail-of-labels) → tap opens config.
- [ ] 4.2 [B] `widgetFeatures="reconfigurable"` long-press path, if 1.4(a) says Niagara supports it; otherwise recorded as dropped.
- [ ] 4.3 [B] E2E scenarios 4 and 7 green.

### Phase 5 — Icons in Niagara "Sticker" style
- [ ] 5.1 [M] Inspect `vendor/niagara.apk` with apktool: are Anycons in the APK or downloaded after Pro purchase? Plain/readable drawables or obfuscated? Sticker = static asset or base vector + runtime parameters? Record answers and the decision (expected: option 2, own icon set). If option 1 is viable, build the runtime loader with a pack/variant setting instead of 5.2.
- [ ] 5.2 [B] Own Sticker-style vector set for the icons in 2.2 (stroke weight, sticker outline, colours/gradient). Reference: Niagara help-center Anycons images. `source=own`; built-ins remain the fallback.
- [ ] 5.3 [U] User compares widget icons to the launcher's on the Pixel 10 and reports mismatches (→ backlog).

### Phase 6 — In-car flow and full suite
- [ ] 6.1 [B] E2E scenario 6: notes widget on top of the stack, swipe to reveal, tap, Maps navigation in foreground.
- [ ] 6.2 Full suite (1–7) green three runs in a row locally with no retries; flakes fixed by waiting on real UI conditions.

### Phase 7 — CI and release
- [ ] 7.1 [B] GitHub Actions: on push to `main`, emulator + Niagara (per 1.6) → E2E suite → `assembleRelease`.
- [ ] 7.2 [B] Release keystore generated locally (never committed or printed); `docs/release-procedure.md` with exact steps.
- [ ] 7.3 [U] User keeps the offline keystore copy and adds secrets (base64 keystore, passwords, alias) to the repo's Actions secrets — exact names given in the procedure.
- [ ] 7.4 [U] Create GitHub repo `DreadBoy/navigation-shortcuts` and first push — on explicit go-ahead only; identity check before push.
- [ ] 7.5 [B] On tag `v*`: signed APK attached to a GitHub Release; `versionCode` increments every release.
- [ ] 7.6 [U] User installs v1.0.0 from the release page on the Pixel 10, checks icons (5.3), and confirms an update over an earlier build keeps config.

---

## v2 (do not start without explicit go-ahead)
- Backup & restore: single global destination list in app preferences with Auto Backup; every widget renders that list (removes the "which widget?" step and the widget list in Flow 3). Pull forward if the chooser proves annoying.
- Multiple independent lists — only if the global list is too limiting.
- Other launchers: stock Pixel launcher first, then generic icon-pack support (`org.adw.launcher.THEMES` / `com.novalauncher.THEME`, `appfilter.xml`), as a test-matrix entry, not per-launcher code.
- Other Anycon packs beyond Sticker.
- Auto-update (Obtainium or in-app updater).

## Backlog
_(issues found during verification, with repro and severity)_
