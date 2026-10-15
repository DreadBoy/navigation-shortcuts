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

## 1.3 Niagara on the emulator

Setup: `adb install -r vendor/niagara.apk`; `cmd package set-home-activity bitpit.launcher/.ui.HomeActivity`; `appwidget grantbind --package bitpit.launcher --user 0`; onboarding `button_positive` → pick a favourite + `large_fab_extended` → `Get started` → `floating_tip_button` → one alphabet-bar swipe + BACK. Scripted in `e2e/setup-emulator.sh` (82 s from clean).

Adding a widget: long-press `bitpit.launcher:id/clock` → `Add Custom Widget` → picker `bitpit.launcher:id/recycler_view` (search field finds apps, not widgets; scroll to our label) → app row → preview (`AppWidgetHostView`, desc = widget label). Placed widget appears under the clock in `container`. HOME on Niagara opens app search; close with BACK.

**Widget stacks are Niagara Pro-only** (second custom widget → "Create Widget Stack?" with only "Unlock with Niagara Pro" / "Replace widget"). Not re-verified by the second agent yet. Impact: the stack parts of scenarios 1 and 6 can't run on the free-tier emulator — see open user decision in PLAN.md.

## 1.4 Niagara widget capabilities

- (a) Long-press reconfigure: **yes** — "Change widget settings" appears for widgets with a configure activity + `widgetFeatures=reconfigurable`.
- (b) `requestPinAppWidget`: Niagara declares `CONFIRM_PIN_APPWIDGET` (`bitpit.launcher.shortcut.AddItemActivity`); must be exercised with our app. Keep the pending-list fallback until proven.
- (c) Config activity on placement: **yes**. (d) Placement without a config activity: **yes**.
- Slot: one custom widget slot, ~313×192 dp; "Move and resize" changes height only.
- "Replace widget" left the old widget IDs bound, so `onDeleted` may not run: cleanup must prune stored configs against `getAppWidgetIds()`.
- Items (a), (c), slot size: second-agent re-check pending (disk); re-test with our app in 1.8/2.2.

## 1.5 Google Maps share format

- Maps 25.11 (signed out) shares only the URL `https://maps.app.goo.gl/<id>?g_st=a` — no name or address. The phone's Maps may share "Name\nAddress\nURL"; the parser accepts both.
- Resolve by reading the first `Location` header with a non-browser user agent (single 302 to `https://www.google.com/maps/place/<segment>/data=…`). A browser user agent gets an `intent://` redirect instead — never send one.
- Named places (9 of 12 samples): `data=!4m2!3m1!1s<ftid>`, **no place coordinates** in the URL. Coordinates in the page HTML are the requester's IP viewport — never parse them. Long-press / coordinate search / dropped pin (3 of 12): `!3d<lat>!4d<lng>`.
- **Decision for 3.4:** `!3d!4d` present → destination `lat,lng`; otherwise destination = URL-decoded `/place/` segment as a text query (this is the normal case for named places, not a fallback — the UI must not call it one). Label = segment up to the first comma; for coordinates-only, "Dropped pin" (editable).
- Fixtures: `app/src/test/resources/maps-share-fixtures.json` (12 samples with `parser_rules`).
