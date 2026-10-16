# Decisions

## 0.2 Project setup

- `applicationId` and namespace: `io.github.dreadboy.navigationshortcuts`.
- `minSdk 31` (needed for `widgetFeatures="reconfigurable"` and `requestPinAppWidget` behaviour), `targetSdk`/`compileSdk 36`.
- AGP 9.4.1 (built-in Kotlin), Gradle 9.8.1 wrapper (AGP 9.4.1 needs ≥ 9.6.0), Kotlin Gradle plugin 2.4.21, JDK 21 toolchain with Java 17 bytecode.
- Lint `OldTargetApi`/`GradleDependency` warn that API 37 exists. The android-37 platform is not installed and disk is tight, so the bump to 37 is deferred to the backlog.

## 0.3 Widget rendering technology

**Decision: plain `RemoteViews`**, no Jetpack Glance.

The grid is built per widget as nested `LinearLayout` rows: inflate a row layout per row and a cell layout per column and attach them with `RemoteViews.addView`.

- Arbitrary rows × cols: built from the stored grid size at render time, no fixed layout per size.
- Transparent empty cells: an empty cell is just a cell layout with no icon and no label on a transparent background.
- Per-cell tap: `setOnClickPendingIntent` on each cell. Small grids don't need a collection adapter, so there is no fill-in-intent template to manage.
- Icons: `setImageViewResource` for bundled vectors (works for vector drawables on API 31+), `setImageViewBitmap` for resolved bitmaps.
- APK size: no extra dependency. The pre-MVP release APK (plain RemoteViews, R8 + resource shrinking) is **33,078 bytes** (`ls -l app/build/outputs/apk/release/app-release.apk`, v0.1.0). Glance would add the Compose runtime plus Glance (roughly 1–2 MB or more), and it is still a translation layer over RemoteViews with its own limits. For a static grid its convenience doesn't pay off.

## 0.4 Storage

**Decision: Preferences DataStore** (not Room). Decision only, not implemented yet.

- One JSON-serialized config per widget under key `widget_<appWidgetId>` (`kotlinx.serialization` planned), plus one key for the pending destination list.
- Why: the data is tiny (at most a few dozen entries), always read and written whole per widget, needs no queries or relations; DataStore writes are atomic; no schema migrations, KSP or Room annotation processing, and a smaller APK. Room would be overkill.
- Cleanup: `AppWidgetProvider.onDeleted` removes the widget's key.
- The v2 single global destination list fits the same approach (one key).

## 1.6 Icons — option 2 (own Sticker-style set)

**Decision:** draw our own Sticker-style vectors; Material Symbols remain the fallback. Runtime loading from Niagara is not viable for v1.

Evidence (Niagara Launcher `bitpit.launcher` 1.16.33, versionCode 1639, inspected with apktool):
- Every Anycon ships in the free APK as a readable, unencrypted themed vector: 891 `ic_ip_*` drawables (549 per-app, 157 category, 185 action), 24×24 viewport, plus coral/opal gradient textures. Nothing downloads icons; Pro is a code-side check. (`assets.niagaralauncher.app` serves the app→category database and fonts.)
- Each glyph is three paths whose colours come from Niagara theme attributes. The Sticker look is composed at render time by obfuscated code: white sticker border underneath, glyph at 0.7 scale, palette gradient fill, 1.5 outline. Coral palette: outline `#482658` on a coral gradient; opal: black outline on an opal gradient.
- No glyphs for pool, stadium or gym.
- A runtime load (`<queries>` + `getResourcesForApplication` + `getIdentifier` + Niagara theme styles) would yield only the bare glyph, depend on private resource names and the user's private palette, and reuse Pro-gated art.

Recipe for our set: hand-drawn 24×24 glyphs, white sticker border, 0.7-scaled glyph, gradient fill matching the user's palette, 1.5 outline. Exact palette to be matched against the user's phone in step 5.2.

## 1.1 Niagara APK source

- Package `bitpit.launcher`, v1.16.33 (versionCode 1639), universal APK (no splits), 14,263,561 bytes, sha256 `639d0578eb50b9b2d79937360b0ff65145760022fe1273053b4338a7103e84f3`. Signer `CN=Peter Huber, …, C=DE`, cert SHA-256 `ccb918002c40334b416899258de4f730a36c5d38821b3cb6fcb8c15a4b8198ed`.
- Source: the developer's official GitHub releases (linked from help.niagaralauncher.app): `https://github.com/NiagaraLauncher/Niagara-Issues/releases/download/v<ver>/Niagara_Launcher_v<ver>-release.apk`. The GitHub API publishes each asset's sha256, so the download can be pinned and checked. Mirrors are not needed (APKPure 403, Uptodown Cloudflare, APKMirror outdated).
- Home activity: `bitpit.launcher/.ui.HomeActivity`.
- Consequence for CI (1.7): since the developer publishes the APK on GitHub, CI can download a pinned version + sha256 from there instead of a private fixtures repo. To confirm in 1.7.

## 1.2 Emulator and Google Maps

- Image `system-images;android-36;google_apis_playstore;arm64-v8a` r07; AVD `navshort-e2e` (pixel_8 profile, 4 GB RAM). Headless cold boot 36–38 s.
- Maps 25.11.01 is preinstalled and navigation works **without sign-in**: with `adb emu geo fix 14.5058 46.0569`, `google.navigation:q=…&mode=d` goes straight to active turn-by-turn (Maribor, `46.2397,15.2677`, "Stožice Stadium, Ljubljana"), ~1 s warm.
- First-run dialogs after `pm clear` (with location + notification permissions pre-granted via `pm grant`): "Make it your map" → `Skip`; safety disclaimer → `android:id/button1` "Got it"; "How navigation data…" → `maps:id/dialog_positive_button` "Dismiss".
- "Navigation is showing" assertion: any of `com.google.android.apps.maps:id/navigation_time_remaining_label`, `step_instruction_content`, `nav_container` (absent on Maps home — `search_omnibox_text_box` — and route preview — desc `Start`).
- Firing a new destination during navigation shows "Exit navigation?" with a ~5 s countdown; timeout keeps the old route. E2E: force-stop Maps between taps, or tap `Yes`.
- Don't use `ftid` in the navigation intent — it does not start navigation.
- Risks: Play Store/GMS self-update ~3 min after first boot kills Maps and grows the AVD's userdata (~0.5 GB / 15 min); adb `uiautomator dump` is flaky during navigation (instrumented UI Automator to be proven in 1.8).

### AVD disk growth mitigation (2026-10-16)

- **Fix: `adb shell pm disable-user --user 0 com.android.vending`** (plus `settings put global auto_update 0`, which is harmless) right after the first boot of a fresh userdata. Nothing else was needed: with the Play Store disabled, GMS stayed at `25.08.34 (260400-739221510)` and Maps at `25.11.01` for the whole session (~45 min), and Maps kept working.
- Numbers (`du -sm ~/.android/avd/navshort-e2e.avd`, sampled every 60 s): 956 MB at boot → 1,743 → 1,867 MB in the first 2 min (first-boot optimisation + provisioning: Niagara install, Maps first run), then 1,876 → 2,019 MB over the next ~42 min, which includes ~8 full E2E runs (APK installs, logs): **~3.5 MB/min vs ~33 MB/min before** (0.5 GB / 15 min). Host free space stayed 3.1–3.8 GB.
- Pitfall: `-wipe-data` on this Play Store AVD deletes userdata **first** and then refuses to recreate it unless 1.2 × the data partition (7,373 MB for 6 GB) is free on the host; the AVD is then left without userdata. Play Store AVDs force `disk.dataPartition.size` back to 6 GB (`config.ini` is rewritten at launch, `-partition-size` is ignored).
- Recovery that worked with ~5 GB free: create an **empty ext4** userdata image (sparse, 64 MB on disk) with the SDK's own tool and boot normally (no `-wipe-data`); Android formats/encrypts it on first boot:
  `MKE2FS_CONFIG=$ANDROID_HOME/platform-tools/mke2fs.conf $ANDROID_HOME/platform-tools/mke2fs -F -t ext4 -b 4096 -O encrypt,quota,verity -L data ~/.android/avd/navshort-e2e.avd/userdata-qemu.img 1572864`.
  A zero-filled image does not work (ranchu's fstab has no `formattable`; init gets stuck trying to reboot into recovery). After that: disable the Play Store, run `e2e/setup-emulator.sh`.
- Boot flags: `-no-window -no-boot-anim -no-snapshot` (cold boot ~42 s, no snapshot written).

## 1.3 Niagara on the emulator

Setup: `adb install -r vendor/niagara.apk`; `cmd package set-home-activity bitpit.launcher/.ui.HomeActivity`; `appwidget grantbind --package bitpit.launcher --user 0`; onboarding `button_positive` → pick a favourite + `large_fab_extended` → `Get started` → `floating_tip_button` → one alphabet-bar swipe + BACK. Scripted in `e2e/setup-emulator.sh` (82 s from clean).

Adding a widget: long-press `bitpit.launcher:id/clock` → `Add Custom Widget` → picker `bitpit.launcher:id/recycler_view` (search field finds apps, not widgets; scroll to our label) → app row → preview (`AppWidgetHostView`, desc = widget label). Placed widget appears under the clock in `container`. HOME on Niagara opens app search; close with BACK.

**Widget stacks are Niagara Pro-only** (second custom widget → "Create Widget Stack?" with only "Unlock with Niagara Pro" / "Replace widget"). Impact: the stack parts of scenarios 1 and 6 can't run on the free-tier emulator — see open user decision in PLAN.md.

### Re-check with our app (2026-10-16, Niagara 1.16.33 free, API 36)

- **Picker preview:** our `previewLayout` is rendered **live** (`bitpit.launcher:id/live_preview_container` → `AppWidgetHostView` desc "Navigation Shortcuts" → our `cell_root`/`cell_icon`/`cell_label` "Maribor"), not the app icon. Under it: header "Navigation Shortcuts" and our `widget_description` ("Tap a destination to start Google Maps driving navigation"). The app row reads "Navigation Shortcuts" / "1 Widget". Tapping the card (e.g. the description text) places the widget; no confirmation dialog (bind permission pre-granted by setup).
- **Placed widget:** `bitpit.launcher:id/widget_stack_recycler` → `container` → `AppWidgetHostView` (desc "Navigation Shortcuts") under the clock, 823×252 px. The free tier also uses the "stack" recycler, with one item.
- **Sizes our provider receives** (debug log in `NavigationWidgetProvider`): the very first placement on a fresh Niagara: `onUpdate` + `onAppWidgetOptionsChanged` with `minW=315 minH=80 maxW=315 maxH=80`, `OPTION_APPWIDGET_SIZES=[315.43×80.0]` dp. **Every later placement (≈22 times): no options at all** (`0/0/0/0`, sizes `null`, no `onAppWidgetOptionsChanged`; `dumpsys appwidget` shows none). "Move and resize" (drag the bottom handle `resize_handle_widget`) does send them: `315×206` dp, sizes `[315.43×206.86]`. So the grid must not depend on options being present; width ~315 dp, height default ~80 dp, resizable in height only.
- **Long-press menu on a placed widget** (title = widget label): Edit favorites, App info, Move and resize, Replace clock with widget, Fill screen, Add Custom Widget, Remove, Niagara settings, Niagara Pro. (No "Change widget settings" for our widget — it has no configure activity.)
- **Remove flow:** long-press the widget → `Remove` → dialog title "Navigation Shortcuts", text "Do you want to remove this widget?", buttons `Okay` (`bitpit.launcher:id/button_positive`) / `Cancel` (`button_negative`) → slot disappears.
- **Second custom widget** (via "Add Custom Widget" in the widget menu or the clock sheet; picker opens normally, e.g. Clock → "Analog"): dialog **"Create Widget Stack?"** with bullets "Use multiple widgets without sacrificing screen space" / "Swipe to switch between your widgets", text "Instead of creating a widget stack, you can also replace your current widget.", buttons **"Unlock with Niagara Pro"** and **"Replace widget"**. Confirmed Pro-only.
- **"Replace clock with widget"** (widget menu → dialog "Replace clock with widget", `Enable` / `Keep disabled`): only moves the single custom widget into the clock's place; adding another widget still shows "Create Widget Stack?". Not a way to get two widgets. Side effect: in that position a long-press on our widget does **not** open Niagara's menu — the press reaches our cell and starts navigation (reproduced 3×). Removing the widget (here via uninstall) restored the clock and the option was off for the next placement.
- **Pop-ups:** app long-press → Advanced → "Edit <app> pop-up" has a "Widgets → Add widget" section; picking our widget shows "You discovered a Pro feature" — "Pop-up widgets … Get timely information at a glance while keeping your home screen clean." / "Universal Pop-ups …" with "Unlock with Niagara Pro" / "Cancel". **Pop-up widgets are Pro.** The same editor lists app shortcuts (e.g. "Maps shortcuts — Home, Work") and other apps as free pop-up entries, so per-destination launcher shortcuts from our app could be a free alternative (backlog idea, not tested).
- Conclusion: on free Niagara a notes widget and our widget cannot both be on the home screen at the same time.
- **Force-stop masking (Android 15+ platform behaviour, not Niagara):** after `am force-stop` of our app the system replaces our RemoteViews with its mask (`android:id/background` + `android:id/work_widget_app_icon`, the app icon). The first tap only ends the stopped state (`dumpsys package` `stopped=true → false`, process starts for the provider broadcast, `onUpdate` re-renders the cell) and does **not** navigate; the next tap does. Process death without the stopped state (`am kill`, what the system does to cached processes) keeps the cell and the first tap navigates. The E2E suite asserts both.

## 1.4 Niagara widget capabilities

- (a) Long-press reconfigure: **yes** — "Change widget settings" appears for widgets with a configure activity + `widgetFeatures=reconfigurable`.
- (b) `requestPinAppWidget`: Niagara declares `CONFIRM_PIN_APPWIDGET` (`bitpit.launcher.shortcut.AddItemActivity`); must be exercised with our app. Keep the pending-list fallback until proven.
- (c) Config activity on placement: **yes**. (d) Placement without a config activity: **yes**.
- Slot: one custom widget slot, ~313×192 dp; "Move and resize" changes height only.
- "Replace widget" left the old widget IDs bound, so `onDeleted` may not run: cleanup must prune stored configs against `getAppWidgetIds()`.
- Items (a), (c), slot size: second-agent re-check pending (disk); re-test with our app in 1.8/2.2.
- (d) re-checked with our app: placement without a config activity works (see 1.3 re-check). Slot with our widget: 315×80 dp by default (first-placement options), resize changes height only (315×206 dp after a drag). (b) `requestPinAppWidget` not tested yet.

## 1.8 E2E harness

- **Module:** separate `:e2e` Gradle module (`com.android.test`, `targetProjectPath = ":app"`, self-instrumenting), sources in `e2e/src/main/java`. Not `app/src/androidTest`: instrumentation inside `:app` runs in the app's process, so `am force-stop`/`am kill` of the app (scenario 5) would kill the test runner too. Self-instrumenting also keeps the suite black-box (it only sees the app through the UI).
- **Entry:** `e2e/run-e2e.sh` — reuses a running device or boots `navshort-e2e` headless; runs `setup-emulator.sh` when Niagara is missing / not default home / the device was never provisioned (marker `/data/local/tmp/navshort-e2e-setup-done`); sets `geo fix`; runs `./gradlew :e2e:connectedDebugAndroidTest` with APKs left installed; prints test counts/durations; exit code = Gradle's. Env: `NIAGARA_APK` (default `vendor/niagara.apk`, falling back to the main checkout's when run from a worktree), `AVD`, `ANDROID_SERIAL`, `FORCE_SETUP=1`, `GEO_LNG`/`GEO_LAT`.
- **Helpers:** `Niagara` (goHome — BACK closes search/menus, HOME only from other apps; addOurWidget; removeCustomWidgetIfPresent; cell(label)), `GoogleMaps` (assertNavigationInForeground, forceStop), `E2eDevice` (waits that fail with a message). `Configurator.waitForIdleTimeout = 500 ms` because Maps navigation never goes idle. `BySelector` is mutable — build a fresh one per use.
- Long-press = zero-length `UiDevice.swipe` with 60 steps (~33 ms/step measured, ~2 s).
- Picker: the app row can be "found" while only a sliver shows at the bottom edge, and Niagara does not scroll the expanded card into view (verifier: 1 failure in 10 runs, position depends on the Suggestions section). The helper scrolls the row into the upper half before tapping it, then scrolls forward until the description is above the lower quarter, then taps it.
- Maps' navigation first-run dialog ("Use Google Assistant while navigating", `navigation_welcome_title_text`, `android:id/button1` "Got it") came back once after a normal Maps launch and held navigation. `GoogleMaps.assertNavigationInForeground` taps "Got it" when that exact dialog is showing (as a user would) and keeps waiting; any other dialog still fails the assertion.
- Minute rollover: when Niagara's clock ticks to the next minute during a long-press (clock, date line or the custom widget), Niagara cancels the press and no sheet/menu opens (verifier: 1 red in 11 runs; manual repro 2/2 at :59.8). Every long-press goes through `Niagara.longPress`, which reads the device clock (`System.currentTimeMillis()`, the test runs on the device) and, if the press would start within 4 s of a minute boundary (press ≤ ~2.5 s + margin), waits until 1.5 s after the boundary. Verifier-measured cancel window: ~:59.9 to ~:00.3 (TIME_TICK at :00.002, Niagara relayout until ~:00.3), so the first version's 0.5 s settle left only ~0.2 s margin, too tight for a loaded CI host. Proven on device (with the 0.5 s settle): harness test started at :56 and :57 logged "waiting 3305 ms" (clock press) and "waiting 2326 ms" (widget long-press for Remove); both pressed at :00.5 and passed.
- Backlog: no overall timeout around the Gradle/instrumentation step yet (a hung instrumentation would hang CI).
- **Known Google Maps cold-start race (not ours).** Symptom: Maps cold-starts but stays on its home screen ("Search here", "Explore", hotel cards) instead of navigation for the whole wait (2 of ~16 suite runs on 2026-10-16). Verifier probe (instrumented; force-stop Maps, then launch, 25 s poll): stuck on Maps home with the full intent `dat=google.navigation:q=Maribor%2C%20Slovenija&mode=d` recorded by the system in 1/20 direct `am start` launches without our app (0/20 with a fresh geo fix), 1/20 widget taps after `am kill`, 2/20 widget taps with our process alive; otherwise navigation in 1.3–6.8 s. So the intent reaches Maps intact and Maps drops it.
  **Decision:** accepted as a known Maps-side flake (~2–5 % per cold-Maps assertion). No retry or re-tap in the suite (spec rule; a re-tap could hide a product bug). The assertion's failure message includes Maps' recorded intent (`dumpsys activity activities`) and the visible Maps texts, which identify this signature. Backlog: watch for it on the real phone (2.4); revisit if CI flakiness matters.
- `run-e2e.sh` prints each failing test with the first line of its failure message. It also deletes old results before running (no stale summary after a build failure) and times out if the emulator is not online in 180 s or not booted in 300 s.

## 1.5 Google Maps share format

- Maps 25.11 (signed out) shares only the URL `https://maps.app.goo.gl/<id>?g_st=a` — no name or address. The phone's Maps may share "Name\nAddress\nURL"; the parser accepts both.
- Resolve by reading the first `Location` header with a non-browser user agent (single 302 to `https://www.google.com/maps/place/<segment>/data=…`). A browser user agent gets an `intent://` redirect instead — never send one.
- Named places (9 of 12 samples): `data=!4m2!3m1!1s<ftid>`, **no place coordinates** in the URL. Coordinates in the page HTML are the requester's IP viewport — never parse them. Long-press / coordinate search / dropped pin (3 of 12): `!3d<lat>!4d<lng>`.
- **Decision for 3.4:** `!3d!4d` present → destination `lat,lng`; otherwise destination = URL-decoded `/place/` segment as a text query (this is the normal case for named places, not a fallback — the UI must not call it one). Label = segment up to the first comma; for coordinates-only, "Dropped pin" (editable).
- Fixtures: `app/src/test/resources/maps-share-fixtures.json` (12 samples with `parser_rules`).

## 2.4 Test scope decisions (user, 2026-10-16)

- **Widget stacks:** E2E runs with our widget as the single custom widget (free-tier Niagara on the emulator). The stack under the notes widget (scenarios 1 and 6) is checked manually by the user on the Pixel 10 with Niagara Pro — done for 0.1.0 (swipe → tap → navigation in ~2 s).
- **Scenario 5 (process death):** after `am force-stop`, Android 16 masks the widget until the first tap (system behaviour, nothing to fix). Ordinary process death (`am kill`) keeps the widget and the first tap navigates. The suite asserts both; the spec stays as written.

## 5.x Icons: inherit from Niagara (user decision, 2026-10-16)

Supersedes the icon conclusion of 1.6. The widget reads Anycon glyphs from the installed Niagara at runtime and draws them in a pack/palette chosen in our widget config, re-creating Niagara's per-pack compositing (from resources if defined there, otherwise from the decompiled code). Accepted limits: new Niagara packs/styles need our re-implementation; the user's Niagara pack/palette choice is private and is picked again in our config; Android offers no API for a launcher's themed icons. Fallback (user decision): if a Niagara glyph can't be loaded, the cell uses the built-in Material icon. The own Sticker-style set from 5.1 was removed — no own copy of Niagara's look. The widget stays a widget (pinned shortcuts, which Niagara would theme itself, were considered and rejected: they lose the grid).
