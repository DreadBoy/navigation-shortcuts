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
- [x] 3.1 [B] Data model + repository per 0.4 (per-widget config, icon reference `{source, name}`, cleanup on `onDeleted`). Placement without config uses a default 1×4 grid (per 1.4(d); if Niagara requires a config activity, a minimal "OK" screen with the share tip).
- [x] 3.2 [B] Built-in icon set (home, work, gym, stadium, school, pool, shop, generic pin) + resolver `{source,name} → bitmap`.
- [x] 3.3 [B] Widget renders rows×cols from stored config: icon + label, transparent empty cells, left-to-right/top-to-bottom fill. Empty cell tap: in MVP shows the "share a place from Google Maps" tip (full config comes in Phase 4).
- [x] 3.4 [B] Link resolver: follow redirect once at add time, parse coordinates + place name, fall back to place-name query and say so. Unit tests over 1.5 fixtures. `INTERNET` permission.
- [x] 3.5 [B] Share target: `ACTION_SEND text/plain`, bottom-sheet activity "Add to navigation widget"; label prefilled; resolved destination shown; icon guessed from category + one tap to picker.
- [x] 3.6 [B] Widget chooser: one widget → hidden; several → chips "Label · Label · Label"; none → "Place new widget" via `requestPinAppWidget` if 1.4(b) allows, else pending list + explanation.
- [x] 3.7 [B] Grid full → offer "+1 column / +1 row" in one tap.
- [x] 3.8 [B] E2E: scenario 3b, scenario 2 over shared entries, scenario 5. Signed APK v0.2 to the user [U]. — 9 E2E tests (share→Add→navigate, two entries + app kill, force-stop mask, grid full 1×4→1×5→2×5, pin Okay/Cancel→pending, empty-cell tip, ghost widget); final fresh verifier HAPPY; merged tree 9/9 debug and 9/9 release; `install -r` over 0.1.0 OK. `dist/navigation-shortcuts-0.2.0.apk` sha256 `53f08fb5…920fe7`. requestPinAppWidget works on Niagara only when none of our widgets is placed; see decisions 3.x.

### Phase 4 — Rest of v1 flows
- [x] 4.1 [B] Config activity (per widget): grid size (default 1×4), icon style (Niagara pack + palette; from Phase 5), destination list with add manually / edit / delete / drag reorder, icon picker; on placement (if kept), on empty-cell tap, pending destinations prefilled.
- [x] 4.2 [B] App launcher activity: list of placed widgets (thumbnail-of-labels) → config.
- [x] 4.3 [B] Long-press "Reconfigure" if 1.4(a) allows; otherwise recorded as dropped.
- [x] 4.4 [B] E2E scenarios 1, 3, 4, 7. — 13 E2E tests (9 previous + config via empty cell with resize/delete/reorder, Niagara long-press → Change widget settings, style change re-renders, launcher list → manual add); fresh verifier 13/13 debug ×2, 13/13 release, one Maps-side stall in a 4th run. `configuration_optional` honoured by Niagara (no config on placement). 0.3.0 sha256 `3842609b…ffb9ae`.

### Phase 5 — Icons inherited from Niagara (user decision 2026-10-16)
Goal: the widget shows Niagara's own Anycon glyphs, read at runtime from the installed Niagara, drawn in the pack/palette the user picks in the widget config. New glyphs in future Niagara versions appear automatically; new packs/styles and the user's Niagara choice can't (Niagara-private), accepted by the user. If a Niagara glyph can't be loaded (Niagara missing, name removed in an update, resources unreadable), the cell uses the built-in Material icon.
- [x] 5.1 Own Sticker-style vector set — built, then dropped by user decision (2026-10-16): no own copy of Niagara's look; fallback is the built-in Material set. Removed from the repo (history: commit 0e525b5).
- [x] 5.2 [M] How Niagara styles Anycons: are pack colours, textures, stroke and shape parameters for the 9 packs (Material, Sticker, Dots, Outline, Outline Chroma, Watercolor, Marble, Bright, Stellar) defined in Niagara's resources (styles/attrs/drawables), and which parts live in code? If in code: decompile (jadx) and document each pack's compositing recipe precisely enough to re-create it. Record in decisions. — All 9 packs computed in code, re-creatable; glyph XML + 5 textures read at runtime; spec in git-ignored `vendor/niagara-styles/`; see decisions 5.2.
- [x] 5.3 [M] Runtime glyph loading spike on the emulator: `<queries>` for `bitpit.launcher`, `getResourcesForApplication` + `getIdentifier`, Niagara theme applied so colour attrs resolve; render a few `ic_ip_*` glyphs on our widget. Measure: load time, failures, behaviour when Niagara is missing/updated. — Works on device: ~125 ms per cold 12-cell update off the main thread, ~1 ms/cell warm; Niagara uninstalled → Material fallback, no crash. See decisions 5.3.
- [x] 5.4 [B] Style renderer: re-create the packs (Sticker first, then the other 8) per 5.2; bitmap cache; fallback to the built-in Material icon when Niagara or a glyph is missing. — `:anycons` module: glyph parser, OKLCH palettes, 9 composers (22 variants), `NiagaraGlyphSource` returning null on any failure; 58 JVM tests incl. all 891 real glyphs; fresh verifier HAPPY. Not yet wired into `:app`; on-device check is 5.3.
- [x] 5.5 [B] Icon picker over Niagara glyphs (categories/actions/apps, searchable); stored as `{source: niagara, name}`; name-based guess maps to Niagara glyph names.
- [ ] 5.6 [U] User compares each pack against Niagara on the Pixel 10 (reference screenshots per pack help); mismatches → backlog.
- [ ] 5.7 [B] 0.3.1 (user feedback on 0.3.0, 2026-10-16): Material icons only when Niagara is missing (picker, auto-pick, existing Material entries render as the nearest Niagara glyph); share sheet in two steps (place: icon/label/destination, Cancel/Next → widget list with "+ Create new widget" last, Back/Add; "+ Create new" always requests pinning, the Niagara stuck-overlay with an existing widget is accepted as a known Niagara bug; "Save for a new widget" removed); Slovenian keywords for the icon guess.

### Phase 5b — Exploratory review and look fixes (user decision 2026-10-16)
- [ ] 5b.1 Exploratory verifier on 0.3.0 (the build the user tested), blind to the user's reports: all flows so far, whole-screen view next to Niagara — fit with Niagara, does the screen make sense, every control visibly works, improvements. Output: bugs + suggestions with repro and screenshots.
- [ ] 5b.2 Triage 5b.1 with the user's own 0.3.0 reports (both icon sets in the picker; white Material stadium among Sticker icons; "Save for a new widget" seemed to do nothing; one-step share sheet; Slovenian place got no icon guess; widget icons ~1.4× Niagara's; "App colours" makes every icon the same blue; colour swatches clickable but ignored outside theme-colour palettes). Record how many of the user's reports the verifier also found.
- [ ] 5b.3 Look fixes from the triage, at least: icon size matching Niagara's default plus a size setting; per-destination colours for "App colours" (by category, stable fallback per destination); swatches only for theme-colour palettes; icon-to-label spacing and label style like Niagara's.

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
- [low/medium] After uninstall + reinstall of our app while Niagara runs, a newly placed widget shows "Problem loading widget" (stale provider cache in Niagara); `am force-stop bitpit.launcher` fixes it. `run-e2e.sh` should force-stop Niagara after installing. — Phase 3: `run-e2e.sh` now installs, force-stops Niagara, then runs the suite.
- [medium] With Niagara's "Replace clock with widget" on, long-pressing our widget starts navigation instead of opening Niagara's menu.
- [idea] Per-destination app shortcuts could appear in Niagara's free pop-ups — a free way around the one-widget limit. Untested.
- [low] apksigner shows v2=false with minSdk 31 (v3 present); confirm intended.
- [low] No overall timeout on the Gradle/instrumentation step in `run-e2e.sh` (CI could hang).
- [low] `:app` androidTest dependencies are unused since tests live in `:e2e`.
- [medium] Release builds shrink resources: any drawable looked up via `getIdentifier` (not `R.drawable`) is stripped — reference built-in icons directly.
- [low] Anycons: Outline dark-text stroke hard-coded #1A1C1F (Niagara varies it by palette).
- [low] Anycons: `act_pause_circled_48`/`act_play_circled_48` render broken as Sticker (48×48 viewport + 12,12 pivot, as in the spec) — exclude from the Sticker picker.
- [low] Anycons: Dots renders without Niagara (no glyph) — `:app` must not treat a non-null bitmap as "Niagara available".
- [low] Anycons: launcher shadow blur can be cut at the bitmap's bottom edge for full-bleed shapes.
- [low] CI keystore reconstruction from secrets is documented but not implemented (7.1).
- [low] (Phase 3) Widget chooser with several *displayed* widgets is untested end to end: free Niagara shows one custom widget (the ghost case is covered by `GhostWidgetE2eTest`). Check on the Pixel 10 with Niagara Pro (two of our widgets in a stack → share → newest first → pick the other → it gets the cell). Also check whether the hidden stack page loads renders (else it would be flagged "not seen on home screen", decisions 3.9).
- [medium] (Phase 3) Niagara ghost widget IDs (decisions 3.7/3.9), Repro A: our widget → long-press → Add Custom Widget → Clock → Analog → Replace widget → Add anyway → share a place → the only bound widget is the ghost, chooser hidden, Add puts it there ("Added to …" names it, but nothing is visible). The render probe flags a ghost only when another widget of ours is displayed (witness), so a lone ghost is never flagged. Way out: "Save for a new widget". Ghosts also never get deleted (only uninstall clears them).
- [low] (Phase 3) Niagara lays our widget out ~32 px (12 dp) wider than the visible area (15 px left inset, none on the right): the last grid column is clipped at the right edge (decisions 3.4). Invisible at 1×4 (icons narrower than cells); with 5–6 columns the last icon/label can be cut. Repro: grow to 1×6, look at the rightmost cell. Check on the Pixel 10 before adding a compensating end padding.
- [low] (Phase 3) Uninstall does not always clear ghosts for good: once (after `GhostWidgetE2eTest` left 1 visible widget + 2 ghosts), uninstall + reinstall re-bound two IDs of ours (151, 152) when Niagara came back; removing the visible widget left 152 bound with nothing on screen. A second uninstall + reinstall cleared everything. `WidgetE2eTest.setUp` fails fast with a clear message if this happens; it didn't in 12 suite runs.
- [low] (Phase 3) The render-probe provider is exported without permission (the launcher must read it): another local app could pre-confirm a bound widget's latest real render and so hide a ghost flag. It can no longer invent widgets/keys or confirm unsent renders (decisions 3.9).
- [low] (Phase 3) `requestPinAppWidget` with a widget already placed on free Niagara leaves an invisible `AddItemActivity` over a "Create Widget Stack?" dialog that cannot be tapped; only Back escapes (decisions 3.2). We no longer request pinning in that case; Niagara-side bug, could be reported to the developer.
- [low] (Phase 3) Labels ellipsize early in a 1×4 grid (Niagara default slot, 79 dp cells): "Stožice Stadium" → "Stožice Stad…". Repro: share Stožice Stadium, look at the cell. Consider 11 sp / two lines / auto-size, or shorter default labels.
- [low] (Phase 3) A just-shared Maps short link answers 404 for ~2–5 s; the sheet keeps "Looking up the place…" up to 12 s (decisions 3.5). If Google's delay grows beyond that, the user gets "Could not resolve the link (link not found (HTTP 404))" and must type the address. Watch on the phone.
- [low] (Phase 3) Bare-URL share with resolution failure (offline) leaves label and destination empty; the user must type both (expected per 3.4 rules, but slow). Repro: airplane mode, share from Maps.
- [low] (Phase 3) Share sheet sent to the background (Home while the sheet is open, started via adb in its own task) was gone afterwards instead of resuming; from Maps it lives in Maps' task and was not affected in the suite. Check whether Home → back to Maps keeps the sheet on the phone.
- [low] (Phase 3) Maps restores its last route/directions screen after a force-stop; the E2E Maps helper backs out to "Search here" first (decisions 3.5). Harmless for users, noted for test maintenance.
- [medium] (Phase 3) Grids with 3 or more rows are unreadable in Niagara's default slot (315×80 dp): 3×6 icons are tiny, 6×6 icons vanish. Repro: grow a full grid with "+1 row" twice and look at the widget. The sheet now suggests Move and resize; consider auto-shrinking labels/hiding them, or capping rows unless the slot is taller.
- [low] (Phase 3) A full Maps URL with an invalid percent-escape in the `/place/` segment fails with the reason "not a Google Maps link" (shown as "This isn't a Google Maps place link") instead of "unexpected link". Repro: share text `https://www.google.com/maps/place/Foo%ZZ/@1,2`.
- [low] (Phase 3) A place segment in degrees-minutes-seconds with `!3d!4d` (e.g. `/place/46°04'17.4"N+14°31'37.8"E/…!3d46.07!4d14.52`) gets the DMS text as label instead of "Dropped pin". Repro: long-press a spot where Maps names it in DMS, share.
- [low] (Phase 3) The resolved destination is read-only in the sheet, so a vague name-only query (e.g. "Stožice Stadium" without address) can't be corrected before Add. Repro: share a place whose `/place/` segment is only the name.
- [fixed in 0.3.0: the shared picker highlights the current icon] [low] (Phase 3) The icon picker does not mark the currently selected icon. Repro: share, tap the icon, look at the grid.
- [low] (Phase 3) Sharing the same place twice creates two identical cells (no duplicate check). Repro: share Stožice, Add, share again, Add.
- [info] (Phase 3) A destination equal to the (mock) current location doesn't show Maps' navigation chrome (Maps behaviour, "you're already there"); E2E places are a few km from the mock location.
- [fixed in 0.3.0: `resolve()` also runs `coordinateQuery` on the redirect target; unit test `coordinateSearchShortLinkRedirectIsADroppedPin`] [medium] (Phase 3) Coordinate-search shares fail on Maps 25.11: Maps → search "47.0 14.5" → Share → Add to navigation widget → "Couldn't read this Google Maps link — type the address or share the place again", label/destination empty (4/4). Maps shares a short link `https://goo.gl/maps/<id>` whose first `Location` is `https://maps.google.com/?q=47.000000,14.500000&entry=gps&…`; `MapsLinks.resolve()` applies `coordinateQuery` only to the shared URL, not to the redirect target, so the target fails `isFullMapsUrl` ("unexpected redirect"). Fix: also run `coordinateQuery` on the fetched URL + unit test; the "share again" advice is misleading here. Workaround: type the coordinates (verified: cell navigates).
- [low] (Phase 3) Ghost hint "hasn't appeared on your home screen since <time>" uses the time the unconfirmed render was pushed, not when the widget was last shown (e.g. says 12:59 for a widget last shown 12:57).
- [info] (Phase 3) Emulator flake: once in ~20 suite runs the emulator's system_server was killed by its watchdog (`android.fg` blocked 67 s in the GPS HAL while Maps was force-stopped); the running test fails with an empty message and later tests don't run. Emulator-side; identifiable by `sys.system_server.start_count` > 1.
- [low] (Phase 4) Config screen: Cancel/Back discard unsaved edits without asking. Repro: empty cell → add a destination → Back → it's gone. Consider a "Discard changes?" prompt.
- [low] (Phase 4) Drag-to-reorder has no accessible alternative (TalkBack users can't reorder). Repro: enable TalkBack, try to move a destination. Add "Move up/down" actions.
- [low] (Phase 4) A placed widget keeps fallback (Material) icons after Niagara is installed or updated until its next update (no manifest broadcast for other packages' installs). Repro: uninstall Niagara, place widget elsewhere, reinstall Niagara → icons stay Material until Save/share/reboot. Re-render on our next process start would cover most cases.
- [info] (Phase 4) Uninstalling Niagara deletes the widgets it hosted and with them their configs (`onDeleted`); the user's destinations are gone. Platform behaviour; v2 global list + backup would keep them.
- [low] (Phase 5.5) Icon picker lists ~330 Niagara glyphs by resource name (English, e.g. "soccer game", "air travel"); no sections or destination-first ordering; many `*_game` glyphs are noise. Repro: open the picker, scroll.
- [low] (Phase 5.5) "App colours" palettes (Sticker/Dots/Watercolor/…) use Niagara's fallback blue for every destination (there is no app); all icons look alike. Repro: config → Sticker → App colours.
- [low] (Phase 5.5) With a tall 6×6 grid icons are 53 px (bitmap budget, decisions 5.5) and look soft when the slot is resized tall. Repro: 36 destinations, Move and resize the widget to full height.
- [low] (Phase 5.5) Style preview and row icons sit on a fixed dark strip; Outline "Dark text" icons are hard to see there. Repro: config → Outline → Text: Dark.
- [low] (Phase 4) `WidgetConfigActivity` is exported (needed for APPWIDGET_CONFIGURE): another local app could open it for one of our widget IDs; the user still has to tap Save, nothing is changed silently.
- [info] (Phase 4) E2E: Maps now and then opens Play services' "Location Accuracy" prompt over itself before navigating (2 of ~40 navigation assertions, after re-provisioning); the Maps helper taps "Turn on" (decisions 4.1). Across 9 suite runs on 0.3.0 the remaining red runs were all identifiable Maps-side flakes: cold-start race (1.8, 1×), "Finding best route..." stall to a 120 km destination (1×, destinations since moved to Ljubljana), place suggestion not opening (3.5, 2×).
- [low/medium] (Phase 4, verifier J2) Growing rows in the config screen doesn't resize Niagara's slot: 2×2 and larger grids are squeezed into the default 1-row-high slot (at 6×6 labels overlap and icons vanish). Same root as the Phase 3 "3 or more rows" item. Repro: config → More rows (or 36 destinations) → Save → home screen. Workaround: Move and resize. Consider a "make the widget taller" hint after adding rows, as the share sheet does.
- [low] (Phase 5.5, verifier J2) Picker shows two identical "home" entries (`act_home` and `cat_home`). Repro: picker → search "home".
- [low] (Phase 4, verifier J2) Two config screens open for the same widget (empty cell → own task, app list → app task): save a delete in A, then save B → the deleted entry comes back (additions are never lost). Rare.
- [low] (Phase 4, verifier J2) Unsaved config edits are lost when the activity is recreated (dark-mode toggle / process death reload from the store). Repro: edit, toggle dark mode → edits gone. Related to "Cancel/Back discard unsaved edits".
