# PLAN — Navigation Shortcuts Widget

Spec: `nav-widget-spec.md`. Decisions: `docs/decisions.md`. Rules: `CLAUDE.md`.
A step is ticked only after verification, with a one-line summary of how it was verified.
"Done" for v1 = the E2E suite (spec §Testing, scenarios 1–7) passes on the emulator against Niagara, locally and in CI.

Legend: **[M]** measure-and-decide (experiment → thresholds → decision in `docs/decisions.md` → then build), **[B]** build, **[U]** needs the user.

Order (set by the user, 2026-10-15): get an APK onto the phone as early as possible. Gather all facts first, then ship a pre-MVP with a hardcoded destination, then the share sheet as the only way to add shortcuts (MVP), then the rest of v1.

---

## v1

### Phase 0 — Repo and project skeleton
- [x] 0.1 Init git with personal identity, commit spec as-is, `CLAUDE.md`, `.gitignore` (`vendor/`, keystores, build output). — `git log` shows both commits as `Matic Leva <vertical3life@gmail.com>`.
- [x] 0.2 [B] Android project: Kotlin, single `app` module, Gradle version catalog, `applicationId` decided and recorded, `minSdk 31` (needed for `widgetFeatures="reconfigurable"` and `requestPinAppWidget` behaviour), `targetSdk` = latest stable. Debug build installs on an emulator. — Fresh verifier: clean `assembleDebug lint testDebugUnitTest assembleRelease` 90/90 tasks green; AGP 9.4.1, Gradle 9.8.1, compile/target 36.
- [x] 0.3 [M] Widget rendering technology: plain `RemoteViews` (nested `LinearLayout` rows built per grid size) vs Jetpack Glance. Criteria: arbitrary rows×cols, transparent empty cells, per-cell `PendingIntent`, icon bitmaps, APK size. Recommendation going in: plain `RemoteViews`. Record decision. — Decided plain RemoteViews (release APK 33 KB); see decisions 0.3.
- [x] 0.4 [M] Storage: Preferences DataStore vs Room for per-widget config `{widgetId → rows, cols, [entries{label, destination, icon{source,name}}]}` plus the pending list. Recommendation going in: DataStore with one serialized config per widget. Record decision. — Decided Preferences DataStore, JSON per widget; see decisions 0.4.
- [x] 0.5 [B] Release keystore generated locally now (never committed or printed), so every APK the user sideloads — pre-MVP included — is signed with the same key and later builds install over it. Keystore location and backup steps in `docs/release-procedure.md`. — Keystore at `~/.config/navigation-shortcuts/` (600, alphanumeric passwords); apksigner shows CN=Matic Leva, v2+v3; missing properties fails release clearly.

### Phase 1 — Test harness and fact-finding (all [M], can run in parallel)
- [x] 1.1 Niagara APK: find the package name, download from a public mirror into `vendor/niagara.apk` (git-ignored). If the download is blocked, ask the user to drop it in `vendor/`. — v1.16.33 from the developer's GitHub release, sha256 pinned; confirmed by a second agent. See decisions 1.1.
- [x] 1.2 Emulator image: Pixel profile, Google Play system image. Is Google Maps preinstalled, and does `google.navigation:q=…&mode=d` reach navigation without sign-in? Which first-run dialogs appear and how to dismiss them reliably; mock location so routes exist. — API 36 Play image; Maps preinstalled, navigation works signed out; dialogs and assertion IDs confirmed by a second agent. See decisions 1.2.
- [x] 1.3 Niagara on the emulator: sideload, `cmd package set-home-activity`, dismiss onboarding. Record UI Automator selectors for: home, long-press menu, widget picker, finding our widget, placing into the widget stack below a notes widget, swiping the stack, widget long-press menu. — Selectors in decisions 1.3, exercised by the E2E suite; stacks and pop-up widgets confirmed Pro-only with our app.
- [x] 1.4 Niagara capabilities: (a) long-press "Reconfigure" (`widgetFeatures="reconfigurable"`)? (b) honours `requestPinAppWidget`? (c) launches the config activity on placement? (d) can a widget be placed with **no** config activity? Decide which entry points v1 keeps. — (a)(c)(d) yes, (b) declared, to test in Phase 3; size options unreliable (grid must not depend on them). See decisions 1.4.
- [x] 1.5 Google Maps share: capture what Maps shares on the emulator (text format, short-link host) and where the short link redirects (`/@lat,lng,`, `!3d…!4d…`). Collect ~10 samples (address, business, POI, dropped pin) as test fixtures. — 12 fixtures in `app/src/test/resources/`; Mac-side resolution confirmed by a second agent, on-device capture re-check pending. See decisions 1.5.
- [x] 1.6 Niagara APK internals (apktool): Anycons in the APK or downloaded after Pro? Readable or obfuscated? Sticker = static asset or base vector + runtime parameters? Decide icon path (expected: own Sticker-style set). — Decided option 2 (own set); fresh verifier confirmed 7/8 claims, the 8th corrected; see decisions 1.6.
- [ ] 1.7 CI feasibility: GitHub Actions Linux runner with KVM, headless emulator, boot time, suite runtime. **Open problem:** CI needs the Niagara APK but it must not be committed — mirror download pinned to a version, cache, or private storage. Decide.
- [x] 1.8 [B] UI Automator 2.3+ test module with helpers: launcher setup, place widget, find cell by label, wait for Maps navigation foreground, force-stop app. Proven by a smoke test placing a stub widget on Niagara's home screen. — Separate `:e2e` test module + `e2e/run-e2e.sh`; final fresh verifier: 3/3 runs green (74–80 s). See decisions 1.8.

### Phase 2 — Pre-MVP: hardcoded widget on the user's phone
- [x] 2.1 [B] Widget with one hardcoded destination (built-in icon + label), no config activity; tap → `google.navigation:q=<dest>&mode=d` with the Maps package set. Destination is a build-time constant. — Verified from the APK: receiver + provider info, intent `google.navigation:q=Maribor%2C%20Slovenija&mode=d` to Maps package, URI encoder matches AOSP on 200k random strings. On-device check is 2.2.
- [x] 2.2 [B] E2E on Niagara: place widget, tap, Maps navigation in foreground; repeat after `am force-stop`. — 3 tests (tap→navigation, Maps force-stop, app kill/force-stop) green 3× in a row by a fresh verifier, and against the release APK. On API 36 a force-stopped app's widget is masked by the system: first tap unmasks, second navigates (platform behaviour, accepted).
- [x] 2.3 [B] Signed release APK built locally (key from 0.5), handed to the user with sideload steps. — `dist/navigation-shortcuts-0.1.0.apk`, sha256 `f2b2ab6a…909d49`, CN=Matic Leva (cert `3adccc33…2013d`); in-place `install -r` keeps the placed widget working.
- [x] 2.4 [U] User sideloads on the Pixel 10, places it in the Niagara widget stack, tries it (ideally in the car), reports anything off → backlog. — 2026-10-16 screen recording from the Pixel 10: widget in a Niagara Pro stack under the notes widget, swipe to reveal, tap → "Finding best route…" → active navigation in ~2 s. User: "All works great so far".

### Phase 3 — MVP: add shortcuts via the share sheet (the only add path)
- [ ] 3.1 [B] Data model + repository per 0.4 (per-widget config, icon reference `{source, name}`, cleanup on `onDeleted`). Placement without config uses a default 1×4 grid (per 1.4(d); if Niagara requires a config activity, a minimal "OK" screen with the share tip).
- [ ] 3.2 [B] Built-in icon set (home, work, gym, stadium, school, pool, shop, generic pin) + resolver `{source,name} → bitmap`.
- [ ] 3.3 [B] Widget renders rows×cols from stored config: icon + label, transparent empty cells, left-to-right/top-to-bottom fill. Empty cell tap: in MVP shows the "share a place from Google Maps" tip (full config comes in Phase 4).
- [ ] 3.4 [B] Link resolver: follow redirect once at add time, parse coordinates + place name, fall back to place-name query and say so. Unit tests over 1.5 fixtures. `INTERNET` permission.
- [ ] 3.5 [B] Share target: `ACTION_SEND text/plain`, bottom-sheet activity "Add to navigation widget"; label prefilled; resolved destination shown; icon guessed from category + one tap to picker.
- [ ] 3.6 [B] Widget chooser: one widget → hidden; several → chips "Label · Label · Label"; none → "Place new widget" via `requestPinAppWidget` if 1.4(b) allows, else pending list + explanation.
- [ ] 3.7 [B] Grid full → offer "+1 column / +1 row" in one tap.
- [ ] 3.8 [B] E2E: scenario 3b, scenario 2 over shared entries, scenario 5. Signed APK v0.2 to the user [U].

### Phase 4 — Rest of v1 flows
- [ ] 4.1 [B] Config activity: grid size (default 1×4), destination list with add manually / edit / delete / drag reorder, icon picker; on placement (if kept), on empty-cell tap, pending destinations prefilled.
- [ ] 4.2 [B] App launcher activity: list of placed widgets (thumbnail-of-labels) → config.
- [ ] 4.3 [B] Long-press "Reconfigure" if 1.4(a) allows; otherwise recorded as dropped.
- [ ] 4.4 [B] E2E scenarios 1, 3, 4, 7.

### Phase 5 — Icons in Niagara "Sticker" style
- [x] 5.1 [B] Per 1.6: own Sticker-style vector set (stroke weight, sticker outline, colours/gradient), or runtime loader if Niagara assets turn out usable. Built-ins remain the fallback. — 15 own Sticker-style vectors (`ic_own_*`, coral palette in `colors_icons.xml`, generated by `tools/gen_icons.py`); preview `docs/icons-preview.png`; fresh verifier: compile, render, recognisability, no reused Niagara paths. Not yet wired into the resolver.
- [ ] 5.2 [U] User compares widget icons to the launcher's on the Pixel 10; mismatches → backlog.

### Phase 6 — In-car flow and full suite
- [ ] 6.1 [B] E2E scenario 6: notes widget on top of the stack, swipe to reveal, tap, Maps navigation in foreground.
- [ ] 6.2 Full suite (1–7) green three runs in a row locally, no retries.

### Phase 7 — CI and release
- [x] 7.0 GitHub repo `DreadBoy/navigation-shortcuts` created by the user (public, empty as of 2026-10-15).
- [ ] 7.1 [B] GitHub Actions: on push to `main`, emulator + Niagara (per 1.7) → E2E suite → `assembleRelease`.
- [ ] 7.2 [U] User keeps the offline keystore copy and adds secrets (base64 keystore, passwords, alias) to Actions secrets — exact names in `docs/release-procedure.md`.
- [ ] 7.3 [B] On tag `v*`: signed APK attached to a GitHub Release; `versionCode` increments every release.
- [ ] 7.4 [U] User installs v1.0.0 from the release page, confirms it installs over the sideloaded build and keeps config.

---

### Open user decisions
- **Disk:** ~9.5 GB free after blocking Play Store self-updates in the AVD; E2E fits, but headroom is thin. Largest regenerable items not created by this project: `~/.gradle/caches/9.5.0` (5.4 GB), `~/.gradle/caches/8.11.1` (1.3 GB), system image `android-34/google_apis` (4.2 GB). Not touched without the user's go-ahead.

## v2 (do not start without explicit go-ahead)
- Backup & restore: single global destination list in app preferences with Auto Backup; every widget renders that list (removes the "which widget?" step and the widget list in Flow 3). Pull forward if the chooser proves annoying.
- Multiple independent lists — only if the global list is too limiting.
- Other launchers: stock Pixel launcher first, then generic icon-pack support (`org.adw.launcher.THEMES` / `com.novalauncher.THEME`, `appfilter.xml`), as a test-matrix entry, not per-launcher code.
- Other Anycon packs beyond Sticker.
- Auto-update (Obtainium or in-app updater).

## Backlog
_(issues found during verification, with repro and severity)_
- [low] Raise compile/targetSdk to 37 once the android-37 platform is installed (lint warns).
- [low] Widget info has `previewLayout` only, no `previewImage` fallback — check how Niagara's picker shows it (1.3/2.2).
- [medium] Maps missing/disabled → tap does nothing, no message. Add a fallback toast or `geo:` intent.
- [low] A second tap on the widget during active navigation shows Maps' "Exit navigation?" dialog; ignored unless the user confirms within ~5 s.
- [low] Google Maps sometimes ignores a navigation intent on cold start (2–5%, reproducible without our app: 1/20 via plain `am start`). Watch on the phone in 2.4.
- [low/medium] After uninstall + reinstall of our app while Niagara runs, a newly placed widget shows "Problem loading widget" (stale provider cache in Niagara); `am force-stop bitpit.launcher` fixes it. `run-e2e.sh` should force-stop Niagara after installing.
- [medium] With Niagara's "Replace clock with widget" on, long-pressing our widget starts navigation instead of opening Niagara's menu.
- [idea] Per-destination app shortcuts could appear in Niagara's free pop-ups — a free way around the one-widget limit. Untested.
- [low] apksigner shows v2=false with minSdk 31 (v3 present); confirm intended.
- [low] No overall timeout on the Gradle/instrumentation step in `run-e2e.sh` (CI could hang).
- [low] `:app` androidTest dependencies are unused since tests live in `:e2e`.
- [high until wired] Release builds shrink resources: `ic_own_*` are stripped unless referenced directly via `R.drawable` (or kept via `tools:keep`). Remove `tools:ignore="UnusedResources"` once wired.
- [low] Stadium icon is the weakest at ~40 px; simplify if the phone check agrees.
- [idea] More own glyphs (train, bus, beach, church, bank, pharmacy, fuel, kindergarten); opal palette; test that XMLs match `gen_icons.py`.
- [low] CI keystore reconstruction from secrets is documented but not implemented (7.1).
