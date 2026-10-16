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
- "Replace widget" left the old widget IDs bound, so `onDeleted` may not run: cleanup must prune stored configs against `getAppWidgetIds()`. **Note (3.7/3.9):** because the IDs stay *bound*, `getAppWidgetIds()` keeps returning them and pruning never removes them; they are "ghost" widgets until the app is uninstalled.
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

## 3.1 Storage and data model (Phase 3)

- Preferences DataStore file `widgets`. Key `widget_<appWidgetId>` = JSON (kotlinx.serialization) `{rows, cols, entries: [{label, destination, icon: {source, name}, id}]}`; key `pending` = JSON list of entries shared while no widget existed. Unknown JSON keys are ignored (forward compatible); an unreadable value is logged and ignored.
- `id` (UUID) is the only addition to the spec's entry shape. It is never shown; it makes "add this shared place" idempotent across the pending list and the pin callback (3.3).
- Every change is one atomic `DataStore.edit` over a pure `StoreState` (configs + pending) transform, unit-tested without Android (`StoreStateTest`).
- A widget without a stored config — newly placed, or a 0.1.0 widget after the update (no migration, the Maribor constant is gone) — gets the default 1×4 grid, empty, and takes over the whole pending list, growing columns first, then rows, up to 6×6 (leftovers stay pending).
- Cleanup: `onDeleted` deletes the widget's key, and every widget update and every share-sheet open prunes configs against `AppWidgetManager.getAppWidgetIds()` (Niagara's "Replace widget" may not call `onDeleted`, 1.4).

## 3.2 `requestPinAppWidget` on Niagara (1.16.33 free, API 36 emulator)

- `isRequestPinAppWidgetSupported` = true; `requestPinAppWidget(provider, null, callback)` returns true.
- Niagara shows a bottom sheet (`bitpit.launcher/.shortcut.AddItemActivity`): title **"Add to home screen"**, a card with our app icon, text **"Navigation Shortcuts"**, buttons **"Okay"** (`bitpit.launcher:id/button_positive`) and **"Cancel"** (`button_negative`). No widget preview, no size choice.
- **Okay** with the custom-widget slot empty: the widget is placed in the slot under the clock and Niagara's home comes to the front. Our provider gets `onUpdate` for the new ID (no size options, as in 1.3), then the success callback with `EXTRA_APPWIDGET_ID` ~25 ms later. The new widget showed exactly the shared place (one cell + three empty).
- **Cancel**: Niagara's sheet closes and our share sheet is in front again; no widget is placed and the callback does not fire.
- Decision: keep "Place new widget" via pinning for the no-widget case; the pending list stays as the fallback (pin unsupported, request refused, or cancelled).
- **Pinning while a widget already exists (measured 2026-10-16, free tier, widgets 2 and 3 bound):** the same "Add to home screen" sheet appears; **Okay** → Niagara opens **"Create Widget Stack?"** on its home screen, but its `AddItemActivity` stays on top as an empty, transparent, full-screen window: taps reach neither Niagara's dialog ("Unlock with Niagara Pro" / "Replace widget") nor our sheet. Only **Back** gets out (it closes both; nothing is placed, no callback; the entry stays pending).
  **Decision:** request pinning only when no widget of ours is bound. With widgets, the sheet's secondary action is **"Save for a new widget"** (pending list + explanation, no pin request); the next widget the user adds in Niagara takes the place over (3.9).

## 3.3 Pending list and pin callback mechanics

- "Place new widget": the entry is first saved to `pending`, then `requestPinAppWidget` is called with an explicit, mutable broadcast `PendingIntent` to `PinWidgetCallbackReceiver` (mutable because the launcher fills in `EXTRA_APPWIDGET_ID`; explicit component, so allowed on API 34+). The callback intent carries the entry itself as JSON (data URI `navshort://pin/<entryId>` keeps each request's PendingIntent distinct).
- Both paths can deliver the entry: the new widget's first `onUpdate` (no config yet → takes over the pending list) and the callback. The callback runs `completePin`: if the entry ID is already on any widget it only clears it from `pending`; otherwise it adds it to the called-back widget (growing the grid if needed) — so it also works if the pending list was lost. Both run as atomic edits, in either order, without duplicates (unit tests cover both orders, a lost pending list, and the entry already being on another widget).
- Sheet lifecycle: after a successful request the sheet shows "Confirm adding the widget…". When Niagara confirms, it brings its home to the front and our sheet stops; the sheet then finishes itself if a widget is bound (so it is not left behind in Maps' task). If the user cancels, the sheet resumes and explains: "Saved. Add the "Navigation Shortcuts" widget to your home screen (long-press the clock → Add Custom Widget in Niagara); this place will be in it." Pin unsupported/refused → the same explanation directly ("Save for later" button).

## 3.4 Widget layout

- Root `LinearLayout` (vertical) with one weighted row per grid row, each with weighted cells (`RemoteViews.addView`), so the grid fills whatever slot the launcher gives; nothing reads `appWidgetOptions` (unreliable in Niagara, 1.3).
- Filled cell: icon scaled to fit (`fitCenter`, vector, so sharp at any size) above a one-line 12 sp white label with shadow, ellipsized. Empty cell: transparent `FrameLayout`, content description "Empty shortcut slot", tap → tip dialog "Share a place from Google Maps to add it here." (with "Open Google Maps"). **0.3.0:** tap → the widget's config screen (4.1).
- Measured in Niagara's default slot (823×222 px content, 315×80 dp): a 1×4 cell is 210×222 px; the label "Stožice Stadium" ellipsizes to "Stožice Stad…".
- Right edge (round 3): Niagara insets the widget 15 px on the left (grid starts at x=120 inside its container 105–928) but lays it out 840 px wide, so the visible area ends at 928 while the grid ends at 960: the last 1×4 cell is visible as 750–928 (its label "Ptuj" is centred at 855, i.e. the cell is 750–960). Niagara's own "Tap to restore" placeholder overflows the same way. At 1×4 nothing visible is cut (the pin icon ends at ~906 px); with more columns the last icon/label can lose up to ~32 px (12 dp). Not fixable from our side without guessing Niagara's insets (a fixed end padding would be wrong if the host behaves differently on the phone) — backlog, check on the Pixel 10.
- PendingIntents: navigation `PendingIntent.getActivity(requestCode = appWidgetId shl 6 or cellIndex)`, `FLAG_IMMUTABLE|FLAG_UPDATE_CURRENT`; the tip intent also carries a per-cell data URI.
- `previewLayout`: three sample cells (Home, Work, Gym with built-in icons) and one empty cell, without IDs.

## 3.5 Share capture on the emulator (Maps 25.11, signed out)

- Reliable path to a place page through the UI: Maps launcher activity → tap `search_omnibox_text_box` → type the query into `search_omnibox_edit_text` → tap the typed suggestion row (e.g. "Stožice Stadium" for "Stozice Stadium Ljubljana"). Pressing Enter instead gives a result *list* (several places with their own Share buttons), so the suite always taps the suggestion.
- Place page Share: `com.google.android.apps.maps:id/card_stack_header_share_button` (desc "Share"). Maps then shows **its own share grid** (not the Android Sharesheet): Messages, Chrome, Bluetooth, Chat, Gmail, Quick Share, Save, Read aloud, **Add to navigation widget**, Copy to clipboard. Our target is a plain text item there.
- EXTRA_TEXT is the bare short URL (as in 1.5). Shared from the place page, the redirect's `/place/` segment is "Stožice Stadium, Vojkova cesta 100, 1000 Ljubljana, Slovenia" (the 1.5 fixture, captured differently, had the name only); label = "Stožice Stadium".
- Network works on the emulator. Default (Dalvik) user agent, single 302, no redirect following.
- **A just-created short link answers HTTP 404 for a few seconds.** Maps creates a new `maps.app.goo.gl` link on every Share tap (also for the same place), and the sheet asks ~0.1–1.7 s later. In the first suite runs 9 of ~25 shares failed with "HTTP 404 instead of a redirect"; the same links all returned 302 when probed from the Mac minutes later. With retries logged: live after 2.2–4.5 s (attempts at 0.1 / 2.2 / 4.0–4.5 s); most shares are live at the first attempt.
  **Decision:** the resolver retries a 404 (and transient connect errors / timeouts — seen once as an IPv6 "Failed to connect" on the emulator) every 1 s for up to 12 s while the sheet shows "Looking up the place…"; no network at all (unknown host) fails at once. Then the usual failure path applies (shared name, or type it). Unit-tested with a fake clock (`ShortLinkFetcherTest`).
- **Maps sometimes shares a cid link instead of a short link:** `https://maps.google.com/?cid=15712177930209977837&entry=gps` (1 of ~36 shares, Stožice Stadium). The cid is the decimal of the second half of the place's ftid (`0x…:0xda0cde94b1e5e5ed`). It redirects (302 → `maps.google.com/maps?cid=…` → 302 → `www.google.com/maps?cid=…` → 200) to a page with empty title/og tags; no `/place/` name anywhere we may read (1.5).
  **Decision:** no cid navigation (undocumented); the sheet fails with "Google Maps shared a link without the address — share the place again or type the address." (destination = shared name text if any, else typed).
- **Coordinate search shares (round 3):** searching "47.0 14.5" in Maps and sharing gives `https://maps.google.com/?q=47.000000,14.500000&entry=gps` (no short link). A Google Maps link without a `/place/` path whose `q` is a valid lat,lng pair (also `google.com/maps?q=…`, URL-encoded comma/space accepted) now resolves without network to destination `47.000000,14.500000`, label "Dropped pin" (or the shared name line). Text `q` values and cid links are unchanged.
- Failure messages (round 2) are per cause, never raw exception text: no internet ("No internet connection — type the address or try again."), network error/timeout, link still 404 after the retries ("The shared link isn't ready yet — share the place again or type the address."), cid link, not a Maps link, unknown Maps link shape. When the shared text had a name line it is used as the destination and the message says so.
- Grid growth tip (round 2): when the grid is full and "+1 row" would make 3 or more rows, the sheet adds "With 3 or more rows, make the widget taller: long-press it in Niagara → Move and resize." (3×6 icons are tiny and 6×6 icons vanish in the default 315×80 dp slot; backlog). The E2E helper does what the message says: Cancel and Share once more, logged; any other failure still fails the test.
- **Maps restores its last screen after `am force-stop`.** After a navigation test, a cold start of Maps can come back on the directions/route preview of the last destination ("Your location → Stožice Stadium", Drive, Start). Typing into its field edits the route destination instead of searching. The suite presses Back until the home omnibox reads "Search here" (relaunching Maps if Back left it), as a user would.
- Tapping the suggestion sometimes only puts its text into Maps' search field and the place does not open (failed one suite run after 40 s and several taps). The Maps helper now submits the search (Enter) when the place hasn't opened 10 s after a tap, as a user would, and taps the matching result if a list appears; otherwise it fails with "Maps did not open … (Maps-side)". In the last 3 green runs (~45 place openings) the fallback was needed 5 times and always opened the place.
- Maps' share grid orders targets by recent use: after the first share "Add to navigation widget" moves to the first position. The suite finds it by text.

## 3.6 Icons in the share flow

- User decision (2026-10-16): the bundled Material-style set (`source = builtin`) is the default for new entries, the picker and the name guess. The own Sticker-style set (`ic_own_*`) is not used and is being removed from main. Phase 5 loads icons from Niagara at runtime (`source = niagara`) with the built-in icon of the same name as fallback; the resolver has a branch per source, and an unknown source or name resolves to the built-in pin.
- Built-in set (14, white glyph with a dark translucent outline, 48 dp intrinsic, referenced directly via `R.drawable`, never `getIdentifier`, so release resource shrinking keeps them): pin, home, work, gym, stadium, school, pool, shop, restaurant, cafe, hospital, airport, parking, mountain. Material Icons paths (Apache 2.0) except stadium and pool, which are simple own glyphs in the same style.
- Guess from words in the place name (token match, Slovenian + English): fitnes/gym → gym, stadion/stadium → stadium, šol*/school → school, bazen/pool/kopališč* → pool, trgovin*/shop/store/market → shop, restavracij*/restaurant/gostiln*/pizzeri* → restaurant, kavarn*/cafe/coffee → cafe, bolnišnic*/bolnica/hospital → hospital, letališč*/airport → airport, parkirišč*/parking → parking, dom/home → home, služba/work/office → work, gora/koča/mountain/hut → mountain; else pin. Short words only as whole words ("Domžale" ≠ dom, "Park Zvezda" ≠ parking).
- In the sheet the icon sits on a dark-green round button (white glyphs need contrast); one tap opens a 4-column picker of all built-in icons.

## 3.7 Widget chooser and widget removal on Niagara

- Niagara's long-press → Remove → Okay deletes the widget ID (`onDeleted` runs): after ~25 place/remove cycles in the suite, `dumpsys appwidget` listed no widget of ours and the store held no `widget_*` key.
- **Correction (fresh verifier, round 2): "Replace widget" is not covered by pruning.** It leaves the old ID bound to our provider, so `getAppWidgetIds()` keeps returning it and pruning never removes it. Repro A: our widget → long-press → Add Custom Widget → Clock → Analog → "Create Widget Stack?" → Replace widget → "Add widget anyway?" → Add anyway: only the clock is visible, our ID stays bound, and a share with the chooser hidden went into it (toast "Added", nothing visible). Repro B: visible widget → Add Custom Widget → Navigation Shortcuts → Replace widget → new ID, old one still bound; the chooser showed two "Empty widget" chips with the ghost (lowest ID) preselected. Only uninstalling our app clears ghosts (Niagara force-stop and reboot don't). See 3.9 for the signal and the handling.
- Chooser (round 2): 1 bound widget → hidden; several → one radio row per widget (vertical, nothing hides off-screen), **newest first** (highest ID = most recently placed) with widgets flagged "not seen on home screen" (3.9) last; the first is preselected. Labels: "Label · Label · Label" or "Empty widget"; identical labels get "(newest)" / "(older)" / "(older 2)". None bound → "Place new widget" (3.2) or "Save for later" when pinning is unsupported. After Add the toast names the target: "Added to Home · Gym · …".
- Several *displayed* widgets of ours cannot exist on free Niagara (one custom widget, 1.3); Pro stacks need a check on the phone (backlog).

## 3.8 Phase 3 E2E suite (`WidgetE2eTest` 8 tests + `GhostWidgetE2eTest` 1 test)

Replaces the pre-MVP `PreMvpWidgetTest`. Everything through the real UI: Niagara placement, Maps search + Share, our sheet, widget taps. Nothing injected into the store.

1. `harness_newWidgetIsEmptyDefaultGrid_andCanBeRemoved` — new widget = 4 empty cells; remove helper works.
2. `emptyCellTap_showsShareTip` — empty cell → tip dialog → OK → widget unchanged.
3. `shareFromMaps_addsCell_andTapStartsNavigation` — spec 3b: label prefilled "Stožice Stadium", destination resolved, icon guessed "stadium", chooser hidden, Add → back on the Maps place page → widget shows 1 cell + 3 empty → tap → active navigation, Maps' recorded intent has the stored destination.
4. `twoSharedEntries_eachTapStartsNavigation_alsoAfterAppProcessDeath` — spec 2 over two shared places (Maps force-stopped between taps, 1.2) + spec 5 with `am kill`.
5. `afterAppForceStop_widgetIsMaskedUntilFirstTap_thenTapStartsNavigation` — spec 5 with `am force-stop` (mask, 2.4).
6. `gridFull_plusOneColumn_thenPlusOneRow` — 4 shares fill 1×4 (labels edited to S1…S4), 5th: "This widget is full (1×4)" → "+1 column", 6th: "full (1×5)" → "+1 row" → widget shows S1…S6 + 4 empty (2×5); S6 navigates.
7. `noWidget_share_placeNewWidget_pinsWidgetWithThePlace` — no widget → "Place new widget" → Niagara "Add to home screen" → Okay → widget with exactly the shared place; sheet not left behind; tap navigates.
8. `noWidget_share_pinCancelled_placeIsPending_andNextWidgetGetsIt` — Cancel → sheet says "Saved. …" → OK → no widget; placing one from Niagara's picker shows the pending place.

9. `GhostWidgetE2eTest.replaceWidget_shareLandsInVisibleWidget_ghostIsFlagged_andSaveForNewWidgetReachesTheNextWidget` (verifier's Repro B): widget with Stožice → Niagara long-press → Add Custom Widget → Navigation Shortcuts → Replace widget → new empty widget shown, old ID still bound → share Ljubljana Castle: chooser lists "Empty widget" first and preselected, the ghost ("Stožice Stadium") below → Add → the place is in the visible widget → next share: the ghost reads "Stožice Stadium — not seen on home screen" and is listed last → "Save for a new widget" → Replace widget again → the new widget shows the saved place.

- `run-e2e.sh` uninstalls the app first (the only way to clear ghost IDs), installs both APKs, force-stops Niagara (`am force-stop bitpit.launcher`, backlog fix for the stale provider cache), runs `WidgetE2eTest` (instrumentation arg `notClass`), then `GhostWidgetE2eTest` last (it leaves ghosts behind), and prints the merged results. `WidgetE2eTest.setUp` fails fast with a clear message if any ID of ours is still bound after removing the visible widget (`dumpsys appwidget`).
- Known Maps cold-start flake (1.8) is still accepted; it fails in `assertNavigationInForeground` with Maps' recorded intent and on-screen texts, which identifies it.

## 3.9 Ghost widget IDs: render probe signal (round 2)

Question: can the app tell a widget Niagara displays from a "ghost" ID that "Replace widget" left bound (3.7)? Time-boxed search, evidence from the emulator (Niagara 1.16.33 free, API 36):

- **System view: identical.** `dumpsys appwidget` lists ghost 2 and displayed 3 with the same host (`bitpit.launcher`, hostId 4354), host callbacks, provider and a `views=RemoteViews` entry; `getAppWidgetIds()` returns both; neither gets `onDeleted`; Niagara reports no size options for either (1.3). Replace sends us only `onUpdate` for the *new* ID. No public API exposes whether a host has a view for an ID.
- **Host behaviour: different — the render probe.** Every render carries an invisible 1 px `ImageView` whose `setImageViewUri` points at our exported `RenderProbeProvider` (`content://…renderprobe/probe/<widgetId>/<generation>.png`, returns a transparent pixel). A host that has a view for the widget must load that URI from us to apply the render (`openFile` from Niagara's uid 10214). Measured: placement → generations 1 and 2 of widget 2 loaded within 80 ms; after Replace, new widget 3 loaded its render; renders pushed to both while Niagara's app search covered the home page: neither loaded; back on the home page: widget 3 loaded generation 3 at once, **ghost 2 never loaded anything again** (several pushes, minutes).
- **Limits:** a render is only loaded when Niagara shows its home page, so "not loaded yet" is normal while the user is in Maps. On Niagara Pro, widgets on hidden pages of a widget stack may also not load renders — not measurable on the free tier. So the signal is used softly, never to hide a widget or delete its data:
  - per widget we store pushed/shown generations and since when the latest render is unconfirmed (`RenderProbe`, plain SharedPreferences);
  - **"not seen on home screen"** = latest render not displayed although **another widget of ours was displayed after that render was pushed** (a witness: the home screen was demonstrably shown, just without this widget). Only a witness counts, never elapsed time.
  - Round 3 (fresh verifier): the first version also flagged any widget unconfirmed for more than 10 minutes. That misflagged a visible widget when the user stayed in Maps for over 10 minutes (renders pushed while Maps is in front stay unconfirmed until the home screen is shown), and the hint then sent the place to the pending list where only a newly placed widget picks it up. The time rule is gone; a unit test pins "no witness → never flagged, regardless of age".
  - Each share sheet first reads these, then pushes a fresh render to every bound widget, so the next share knows more.
- **Probe provider hardening (round 3):** the provider must be exported (the launcher reads it) and has no permission, so any local app can open its URIs. It now records a display only for `openFile` mode `"r"`, for a widget ID currently bound to us (`getAppWidgetIds()`), and for a generation ≥ 1 that we actually pushed; anything else just gets the pixel. Residual (accepted): another app could still pre-confirm the latest real render of a bound widget, which can only hide a ghost flag, never misdirect data.
- **Handling in the sheet:** chooser newest first with flagged widgets last and labelled "… — not seen on home screen"; if the preselected widget is flagged, a red hint says "This widget hasn't appeared on your home screen since <time>. If you can't find it there, tap "Save for a new widget"". "Save for a new widget" is always offered when widgets exist (3.2: no pin request then). Configs of ghosts are kept (pruning only removes unbound IDs).
- Residual: a single ghost with no visible widget of ours (Repro A) is never flagged — there is no witness. The place goes into the ghost with the toast "Added to <its labels>" naming the target, and "Save for a new widget" is the way out. Backlog.

## 4.1 Config screen and its entry points (Phase 4, v0.3.0)

- **Screen** (`WidgetConfigActivity`, plain Android views, no Compose): grid rows × columns (steppers, 1–6 each), icon style (pack spinner + the pack's variant spinners from `AnyconSettings`/`AnyconStyle`: Material shade + line, Sticker palette, Dots dot + palette, Outline fill + line + text mode, Outline Chroma line + palette, Watercolor/Bright palette, Marble palette; icon shape for the shape packs; seed colour as 8 swatches) with a live preview of 4 icons (the first destinations, padded with home/office/fitness/shopping samples), and the destination list: tap the icon → picker, tap the text or the pencil → edit label + destination, bin → delete, drag handle → reorder (drag starts on touch, no long-press), "Add manually" (label + address; icon guessed from the label). The "share any place from Google Maps" tip is a hint line at the top. Nothing is stored until **Save**; Save re-renders the widget at once. Cancel/Back discard the edits.
- **Default style for new and 0.2.0 widgets: Sticker · Coral** (the user's Niagara setting on the phone). `style` is a new field of the per-widget JSON with this default, so 0.2.0 configs parse unchanged (unit test with a literal 0.2.0 JSON; on-device upgrade check in §5.5).
- **Grid shrink rule (simplest safe):** the screen never offers a grid smaller than the number of destinations — the "−" stepper is disabled and a note says "The grid can't be smaller than the number of destinations (N). Delete one first." Adding beyond the grid grows it (columns first, then rows, as the pending takeover does); at 36 destinations "Add manually" is disabled. On save, `StoreState.saveEdited` merges against the stored config: entries that arrived meanwhile (a share while the screen was open) are appended, and the store grows the grid rather than drop anything (beyond 6×6 → pending). No entry is ever dropped silently.
- **Opening the screen for a widget without a config** (just placed) runs the same `ensureConfig` as its first update (default 1×4 + pending takeover; idempotent). A widget ID that is no longer bound to us is refused ("This widget no longer exists"), so a stale PendingIntent can't create an orphan config that would swallow the pending list.
- **Entry points:**
  - (a) **Empty cell** → config for that widget (own task, `taskAffinity=""`, excluded from recents). Replaces the 0.2.0 tip dialog (`EmptyCellTipActivity` removed).
  - (b) **Niagara long-press → "Change widget settings"**: provider info has `android:configure` + `widgetFeatures="reconfigurable|configuration_optional"`; the activity handles `APPWIDGET_CONFIGURE` (exported). **Measured on Niagara 1.16.33 free: Niagara honours `configuration_optional`** — placement from the picker and via `requestPinAppWidget` open no config screen (only `onUpdate`; logcat shows no "config opened"), the Phase 3 placement, pin and pending-takeover tests stay green unchanged. The menu item opens our screen for the long-pressed widget (E2E). The activity still sets `RESULT_OK` + the widget ID up front, so a launcher that forces it on placement keeps the widget if the user backs out.
  - (c) **App launcher activity** (`MainActivity`): lists bound widgets newest first, labelled like the share sheet's chooser ("Home · Gym · …", "Empty widget", duplicates disambiguated) → tap opens the config; the list refreshes on resume.
- E2E (`ConfigE2eTest`, 4 tests, plus the reworked `emptyCellTap_opensConfigWithShareTip`): scenario 3+4 (empty cell → add 3 manually → 1×4 shows 3 + 1 empty → empty cell shows the 3 → 2×2, shrink blocked with the note → delete one → drag the last onto the first → widget shows the new order as 2×2 → a cell navigates), scenario 7 (long-press → Change widget settings → that widget's entry → rename → widget shows it → navigates), style change (Sticker Coral → Watercolor: >10 % of the icon area's pixels change on the home screen, screenshots saved; cell navigates), app list (opened through Niagara's app search) → config → manual add → list and widget show it → navigates.
- Manual destinations in the suite are text addresses a few km from the mock location ("BTC City, Ljubljana", "Ljubljana Zoo", "Tivoli Park, Ljubljana"). A first version used "Maribor/Ptuj/Celje, Slovenia" (~100–130 km): one run stalled on Maps' "Finding best route..." for the whole 20 s wait (Maps-side; identifiable by that text in the failure message).
- **Play services "Location Accuracy" prompt** (new Maps-side interruption seen in these runs, 2 of ~40 navigation assertions, both right after re-provisioning): Maps starts `com.google.android.gms/…LocationSettingsCheckerActivity` ("To continue, your device will need to use Location Accuracy", No thanks / Turn on) over itself; navigation waits behind it and the stale prompt stays in the task stack. `GoogleMaps.assertNavigationInForeground` now taps "Turn on" when exactly this prompt shows (as a user would; same pattern as Maps' "Got it" in 1.8) and the failure message lists the foreground texts of a non-Maps app, so any other interruption stays visible.

## 5.x Icons: inherit from Niagara (user decision, 2026-10-16)

Supersedes the icon conclusion of 1.6. The widget reads Anycon glyphs from the installed Niagara at runtime and draws them in a pack/palette chosen in our widget config, re-creating Niagara's per-pack compositing (from resources if defined there, otherwise from the decompiled code). Accepted limits: new Niagara packs/styles need our re-implementation; the user's Niagara pack/palette choice is private and is picked again in our config; Android offers no API for a launcher's themed icons. Fallback (user decision): if a Niagara glyph can't be loaded, the cell uses the built-in Material icon. The own Sticker-style set from 5.1 was removed — no own copy of Niagara's look. The widget stays a widget (pinned shortcuts, which Niagara would theme itself, were considered and rejected: they lose the grid).

## 5.2 How Niagara styles Anycons

Every pack's look is computed in Niagara's (obfuscated) code; resources supply only the glyphs, mask styles and five textures. All 9 packs can be re-created with Canvas/Paint (investigated statically by decompiling 1.16.33; two fresh verifiers agreed).

- **Read at runtime** from `bitpit.launcher` (`<queries>` + `getResourcesForApplication`): the `ic_ip_*` glyph XML, parsed by us (paths, per-path colour references, viewport, caps/joins — read per glyph, a few aren't 24×24 or round-capped), and the 5 `anycons_tex_*` textures (coral, opal, marble, two watercolor). Names look stable, but every lookup falls back to the built-in Material icon.
- **Re-implemented by us:** OKLCH colour maths from a seed colour, palettes, mask shapes, the 9 pack composers, and our own per-widget settings for pack, variant, shape, seed colour and text mode (Niagara's are private). Our port freezes the 1.16.33 look.
- Packs: Material (shape background, glyph 0.62, Light/Vivid/Dark), Sticker (white die-cut stamp, front 0.7, glyph-masked texture or solid fill, 1.5 outline), Dots (dot/diamond only — every destination looks alike), Outline (glyph 2/3, stroke in wallpaper text colour — needs our light/dark setting), Outline Chroma (pastel tint of seed), Watercolor (2 textures), Marble (1 texture), Bright (saturated, always filled), Stellar (procedural starfield, OpenSimplex2 noise).
- Destination glyphs that exist: home, office, fitness, education, children, shopping, food, cup, health, doctor, air travel, cars, public transport, taxi, banking, movies, books, mail, contacts, religious, plants, a few sports. Missing (→ Material): stadium, university, pool, bar, pharmacy, fuel, mountain, beach, hotel.
- The full implementer spec (contains decompiled excerpts — never commit or publish it) lives in git-ignored `vendor/niagara-styles/`.
- Risk: textures and art are gated behind Niagara Pro inside Niagara; we only read them from the user's own installed copy, for personal use.

## 5.3 Runtime glyph loading on the device (spike, 2026-10-16)

Emulator `navshort-e2e` (API 36, arm64), Niagara 1.16.33 free, debug build; timings from `NavShortIcons` logcat lines (`IconRendering`/`WidgetRenderer`).

- **Works**: `<queries><package android:name="bitpit.launcher"/></queries>` + `getResourcesForApplication` + `getIdentifier` read the `ic_ip_*` glyphs and the `anycons_tex_*` textures of the free tier; every glyph tried (home, office, fitness, education, shopping, food, cup, health, air travel, location) rendered in Sticker Coral and Watercolor on the widget and in our screens. No Pro check is involved.
- **`getXml` value forms on the device** (`ic_ip_cat_home`): theme refs come back as numeric `?<id>` strings — `fillColor=?2130968576` (= `AnyconsBgFillColor` 0x7f040000), `?2130968577` (Fg), `strokeColor=?2130968579` (Stroke), `scaleX/scaleY/strokeWidth=?2130968578/?2130968580` (Scale/StrokeWidth); enums as integers (`strokeLineCap=1`, `strokeLineJoin=1`); dimensions as `24.0dip`. `GlyphParser` already handles exactly these (numeric refs resolved by attribute name via `getIdentifier("Anycons…","attr",…)`, themed floats, integer enums, `dip` suffix) — no change needed.
- **Timings, 12 Niagara cells at 92 px, Sticker Coral** (texture palette = worst case of the cheap packs):
  - **cold** (fresh process after `adb install -r`, which sends `onUpdate`; nothing cached), 5 runs: glyph load (resources + XML parse) per cell median **0.8–1.6 ms**, total 11.7–17.4 ms; compose per cell median **6.5–8.3 ms** (the first cell includes decoding the 256² coral texture), total 103–114 ms. **≈ 125 ms per 12-cell widget update**, off the main thread (`goAsync`).
  - **warm** (glyphs and texture cached, new bitmaps — first render after a style change or a new destination): glyph 0 ms, compose median **1.0–1.3 ms** per cell (12 cells: 14.7 ms total; 2 cells Watercolor: 2.4 ms). Unchanged widgets hit the bitmap cache (`AnyconRenderer` LRU, 8 MB).
  - Glyph list for the picker (enumerating Niagara's drawable IDs, see 5.5): 327 pickable glyphs (cat + act, minus weather) enumerated in **2 ms** (release build).
- **Niagara uninstalled** (`adb uninstall bitpit.launcher`; the stock Pixel launcher becomes home and our Niagara-hosted widgets are deleted with their host): the session notices (`lastUpdateTime → -1`, caches dropped); share sheet shows the Material "home" for a `niagara:cat_home` guess; a widget placed in the Pixel launcher via `requestPinAppWidget` renders the cell with the Material fallback (`'cat_home' not drawable (Niagara installed=false) → built-in`), no exception, navigation unaffected; the picker offers only the built-in set with "Niagara isn't installed: only built-in icons are available." Afterwards Niagara was reinstalled and `e2e/setup-emulator.sh` re-run (82 s).
- **Niagara updated/reinstalled while we run**: `IconRendering.session()` compares Niagara's `lastUpdateTime` on every batch and rebuilds the glyph source and caches when it changes. A placed widget only picks up the change on its next update (backlog).
- Decision: keep runtime loading as planned (5.x); no workaround needed.

## 5.5 Icons in the product (v0.3.0)

- **Storage**: `{source: "niagara", name: "<glyph name without ic_ip_>"}` (e.g. `cat_home`), drawn in the widget's style with the destination's entry id as seed (same look on the widget, in the sheet and in the config screen). `builtin` refs from 0.2.0 stay valid and keep rendering as the Material vector; no migration. Fallback for a `niagara` ref that can't be drawn (Niagara missing, glyph renamed, any exception, null bitmap): the built-in icon of the same concept (`cat_home→home`, `cat_office→work`, `cat_fitness→gym`, `cat_education→school`, `cat_shopping→shop`, `cat_food→restaurant`, `act_cup→cafe`, `cat_health→hospital`, `cat_air_travel→airport`, `act_location→pin`, a few more), else the pin. Availability is checked with the glyph source (`source.glyph(name) != null` while Niagara is installed), never by a non-null bitmap (Dots draws without a glyph).
- **Name guess** (share sheet and "Add manually"): same keyword rules as 3.6 → concept → Niagara glyph first (home → `cat_home`, work/office → `cat_office`, gym → `cat_fitness`, school → `cat_education`, shop → `cat_shopping`, restaurant → `cat_food`, cafe → `act_cup`, hospital → `cat_health`, airport → `cat_air_travel`, anything else → `act_location`), built-in Material where Niagara has no glyph (stadium, pool, mountain, parking). All names checked against 1.16.33.
- **Picker** (one implementation for the sheet and the config screen): a search field + grid. Niagara's `cat_*` and `act_*` glyphs (no `app_*`, no `act_weather_*` whose colour attrs no pack defines; `act_pause_circled_48`/`act_play_circled_48` hidden for Sticker), drawn in the widget's current style and searchable by name ("air travel"), then the 14 built-in icons ("Built-in home"); the current icon is highlighted. The glyph list is **enumerated at runtime** from Niagara's drawable IDs (IDs of one resource type are dense: walk `0x7fXX0000 + i` with `getResourceEntryName` until 32 misses), so glyphs added by Niagara updates appear without our update. Icons render lazily per visible cell.
- **Bitmap budget**: icon bitmaps are at most **128 px** and together at most **400 KiB per widget update** (`IconRendering.cellSizePx`: `min(128, sqrt(409600 / (4·n)))`): 1–6 Niagara cells → 128 px (6 × 64 KiB = 384 KiB), 12 cells → 92 px (**406,272 bytes measured**), 36 cells (6×6) → **53 px, 404,496 bytes ≈ 39 % of a 1 MB binder buffer**. Measured `RemoteViews` parcel sizes stay small regardless (2,400 B empty 1×4, 2,984 B with 2 bitmaps, 6,952 B with 12): bitmaps above 16 KB are written as ashmem blobs, so what binds us in practice is the platform's widget bitmap limit (1.5 × screen bytes, ~15 MB on this 1080×2400 device); we stay at < 3 % of it.
- **Release build**: R8 + resource shrinking. Nothing of ours is looked up by name (built-in icons via `R.drawable`; Niagara's resources belong to another package, so `getIdentifier` there is unaffected by our shrinking); `kotlinx.serialization` keeps the `AnyconSettings`/`AnyconStyle` serializers through its bundled R8 rules — verified by the release E2E run (style change saved and re-read) and the 0.2.0 → 0.3.0 upgrade below. Release APK: **743,971 bytes** (0.2.0: 498,342; the growth is `:anycons`, androidx.core and the config UI; classes.dex 368 KB → 597 KB), sha256 `3842609b…73ffb9ae`, signer cert `3adccc33…2013d` (unchanged). Release E2E: 13/13 green (12 + ghost pass).
- **Upgrade 0.2.0 → 0.3.0**: verified on the emulator: 0.2.0 (`dist/`) installed, Stožice Stadium shared into a placed widget (built-in stadium icon) → `adb install -r` of the 0.3.0 release → the widget re-rendered on the package-replace update with the same cell (built-in icon, label, 3 empty cells) and a tap started navigation with the stored destination. Its config screen then opened with the default Sticker · Coral style, and Niagara-glyph entries added there rendered in it.

## 5.6 Material icons only as Niagara fallback (v0.3.1)

User report on the Pixel (0.3.0, Niagara installed): the picker listed Niagara glyphs *and* the 14 Material icons, and the name guess picked Material for stadium/pool/parking/mountain, so a plain white cell sat among Sticker cells. Decision: while Niagara's glyphs are readable, no Material icon is shown anywhere.

- **Picker**: Niagara glyphs only while Niagara is readable; only the 14 Material icons (with the existing "Niagara isn't installed" note) when not.
- **Every built-in concept has a Niagara glyph** (checked against all 891 glyphs of 1.16.33 and visually in the picker); the name guess always stores `{source: niagara}`:

  | built-in | Niagara glyph | note |
  |---|---|---|
  | pin | `act_location` | |
  | home | `cat_home` | |
  | work | `cat_office` | |
  | gym | `cat_fitness` | |
  | stadium | `cat_soccer_game` | no stadium glyph; soccer ball |
  | school | `cat_education` | |
  | pool | `act_jellyfish` | no swim/pool glyph; nearest water glyph (alternative `act_anycons_koi`, a mascot) |
  | shop | `cat_shopping` | |
  | restaurant | `cat_food` | |
  | cafe | `act_cup` | |
  | hospital | `cat_health` | |
  | airport | `cat_air_travel` | |
  | parking | `cat_cars` | no parking glyph; car |
  | mountain | `act_sun_horizon` | no mountain glyph; sun over a horizon (landscape) |

  Glyphs are distinct, so the reverse mapping (Niagara glyph → Material when Niagara is missing) stays unambiguous.
- **Stored `builtin` refs (0.2.0 data, or picked while Niagara was missing) are mapped at render time**, not migrated: drawn as the glyph above while Niagara is readable, as their own Material icon otherwise. Render-time mapping keeps the stored data as the user (or 0.2.0) wrote it, needs no migration code or version flag, and still does the right thing when Niagara comes and goes. The picker highlights the mapped glyph for such an entry.
- **Fallback chain per cell**: a `niagara` ref tries its own glyph, then its concept's glyph (a glyph renamed by a Niagara update still shows a Niagara icon); a `builtin` ref tries its glyph, then `act_location`. Only if none is drawable (Niagara missing/unreadable, render failure) the Material icon is used — never a blank cell. In our screens the icon view stays empty until the bitmap arrives (a few ms), so no Material icon flashes up first.
- **Verification**: unit tests (mapping complete/distinct/pickable, glyph files exist in the real APK, guess → Niagara for every concept, fallback both ways); `IconsE2eTest` on the AVD with real Niagara + Maps share (stadium → `cat_soccer_game` stored as `niagara`; picker has no "Built-in …" entry for any of the 14 names; a seeded `builtin:home` / `builtin:stadium` cell renders as Niagara's home / the identical soccer glyph). Niagara uninstalled by hand: picker shows only the 14 Material icons with the note, a guessed `cat_soccer_game` highlights Material "stadium"; Niagara then reinstalled and `e2e/setup-emulator.sh` re-run.
- Debug builds get `DebugStoreReceiver` (`app/src/debug`, not in release): `am broadcast` hooks to dump the store and to seed an icon for E2E preconditions.
