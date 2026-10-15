# Navigation Shortcuts Widget — Spec

## Purpose

A home-screen widget showing a grid of destination shortcuts. Tapping a shortcut launches Google Maps in turn-by-turn driving navigation to that destination immediately, with no intermediate screen.

Primary use: lives in a Niagara launcher widget stack beneath a notes widget. In the car: swipe to reveal, tap, go.

## MVP scope

### Widget

- Grid of cells, **configurable rows × columns** (e.g. 1×4, 2×3).
- Each cell shows the destination's **icon** (primary) with the label underneath. Icon is mandatory; a cell is identified at a glance by its icon.
- Cells without an assigned destination render **empty**, showing the home-screen background through them. Grid size and entry count are independent.
- Tap on a filled cell fires the Google Maps navigation intent:
  `google.navigation:q=<destination>&mode=d`
- Tap on an empty cell opens the config screen for that widget.

### Configuration screen

Opened when the widget is placed (widget config activity), and again from the widget if the user needs to change it.

- Grid size: rows, columns.
- Ordered list of destinations, each with:
  - Icon (mandatory, see *Icons* below)
  - Label (short, fits in a cell)
  - Destination: address text or lat,lng
- Add / edit / remove / reorder entries.
- Entries fill the grid left-to-right, top-to-bottom.

### Icons

Icons must look like they belong to the launcher. The primary target is **Niagara Launcher's "Anycons"** (reference: https://help.niagaralauncher.app/article/149-anycons): hand-crafted icons for well-known apps plus category-based icons for everything else, shipped as nine packs (Material, Sticker, Dots, Outline, Outline Chroma, Watercolor, Marble, Bright, Stellar). The user's pack is **Sticker**. Packs are customizable at runtime (colors, gradients, shape, line thickness), and Anycons are a **Niagara Pro** feature.

Anycons live inside Niagara, not in an installable icon pack, so the icon-pack convention does not reach them. Approach, in priority order:

1. **Niagara resources — measure first.** Inspect the Niagara APK (apktool) and answer, in `docs/decisions.md`: are the Anycon assets present in the APK or downloaded after a Pro purchase? Are they plain drawables/vectors with readable names, or encrypted/obfuscated? Is the Sticker look a static asset or a base vector plus parameters applied at render time? Only if the assets are present, readable and static enough to reproduce the user's current look, load them at runtime via `packageManager.getResourcesForApplication(<niagara package>)` + `getIdentifier`, with a one-time **pack/variant** setting in config. Expected outcome given the Pro gating and runtime customization: this path is not viable for v1, and option 2 is chosen.
2. **Own icon set in Sticker style (likely v1).** Draw the handful of icons needed (home, gym, stadium, school, pool, …) as vectors matching the Sticker pack's look: same stroke weight, sticker outline, and the user's configured colors/gradient. Keep the set small; add icons as destinations need them. Other packs are not supported in v1.
3. **Built-in fallback.** A small bundled set of Material Symbols, used until option 2 icons exist and for any concept not yet drawn.

Generic icon-pack support (`org.adw.launcher.THEMES` / `com.novalauncher.THEME`, reading `appfilter.xml`) is deferred to v2.

Storage: an icon is stored as a reference, not a bitmap — `{source: niagara|own|builtin, name}`. It is resolved to a bitmap when the widget renders.

In the share sheet (Flow 2), the icon defaults to a guess from the place category when Maps provides one (gym, stadium, school), with one tap to open the picker, which shows the available icons in the current style.

### Fixed decisions

- Launcher: **Niagara only**. No other launcher is supported or tested in v1.
- Travel mode: **driving only**.
- Target app: **Google Maps only**.
- No per-shortcut settings beyond label and destination.

## User flows

Goal: adding a destination should take fewer taps than it takes to look up the address.

### Flow 1 — First placement

1. User long-presses the home screen → Widgets → drags the widget onto a screen.
2. Config activity opens automatically (standard widget config). It shows:
   - Grid size (rows × columns), default 1×4.
   - An empty destination list with two ways to add: **"Add manually"** (label + address) and a hint: *"Tip: share any place from Google Maps to this app."*
3. User saves. Widget renders on the home screen.

Saving with zero destinations is allowed; the widget then shows an empty grid, and empty cells act as an entry point (Flow 4).

### Flow 2 — Add a destination by sharing from Google Maps (primary path)

1. In Google Maps, user opens a place → **Share** → picks this app from the share sheet.
2. The app receives `ACTION_SEND` with text like `"<Place name>\nhttps://maps.app.goo.gl/…"`.
3. A small **"Add to widget"** bottom sheet opens (no full activity, no navigation inside the app):
   - **Label** — pre-filled from the shared place name, editable.
   - **Destination** — resolved from the link (see *Link resolution* below); shows the resolved address or coordinates. If resolution fails, falls back to the place name as the query and says so.
   - **Widget** — which widget to add it to:
     - Exactly one widget placed → preselected, control hidden.
     - Several widgets → a chip row of widgets, identified by a thumbnail of their current labels (e.g. "Home · Gym · Stadium"), not by ID.
     - No widget placed yet → the sheet offers **"Place new widget"**. On launchers that support pinning (`AppWidgetManager.requestPinAppWidget`) this prompts the launcher to add one with this destination already in it. On launchers that don't, the destination is saved to a pending list and the sheet explains how to add the widget; Flow 1 then shows the pending destinations pre-filled.
4. User taps **Add**. The sheet closes and returns to Google Maps. The widget updates immediately.

If the chosen widget's grid is full, the sheet says so and offers to grow the grid by one column (or row) in one tap, or to open the full config.

### Flow 3 — Edit an existing widget

Three entry points, all leading to the same config screen for that widget:

- **Long-press the widget** on the home screen → "Reconfigure" (Android 12+, `widgetFeatures="reconfigurable"`). Launcher-dependent; Niagara to be verified.
- **Tap an empty cell** on the widget → opens config for that widget.
- **Open the app** → a list of all placed widgets (same thumbnail-of-labels identification) → tap one.

The config screen shows grid size and the destination list with: add manually, reorder (drag), edit label/destination, delete. Changes apply on save.

### Flow 4 — Empty cell

Tapping an empty cell opens the config screen for that widget (replaces the earlier "does nothing"). This is the cheapest way to make the widget self-explanatory: an empty grid is itself the invitation to fill it.

### Link resolution

Google Maps shares a short link (`maps.app.goo.gl/…`). To navigate reliably, the app resolves it once at add time:

1. Follow the redirect (HTTP HEAD/GET, no body needed) to the full `google.com/maps/...` URL.
2. Parse coordinates from the URL (`/@lat,lng,` or `!3dlat!4dlng` segments) and the place name from the path.
3. Store `lat,lng` as the destination; the label stays the shared place name.
4. If resolution fails (offline, unexpected URL shape), store the place name text as the destination query. `google.navigation:q=<place name>` still works, just slightly less precisely.

Resolution needs the INTERNET permission and happens only on add, never on tap.

### Share target registration

The app declares an activity (styled as a bottom-sheet dialog) with an intent filter for `ACTION_SEND` / `text/plain`. Share-sheet label: "Add to navigation widget". This shows up next to other share targets without any further setup.

### Why per-widget lists (and when to revisit)

Each widget instance keeps its own list, keyed by widget ID, as decided for MVP. This is what forces the "which widget?" step in Flow 2 and the widget list in Flow 3. The v2 global-list design removes both (every widget renders the same list) and fixes backup at the same time. If the "which widget?" step turns out annoying in practice, pull v2 forward.

## Release

Not on Google Play for now; distributed as a sideloaded APK.

- **Build:** GitHub Actions workflow on every push to `main` runs the E2E suite on an emulator, then `./gradlew assembleRelease`.
- **Signing:** release keystore stored as a GitHub Actions secret (base64). The same key must be used for every release, or Android refuses to update the installed app. Keep an offline copy of the keystore.
- **Publish:** on a version tag (`v1.0.0`), the workflow creates a GitHub Release with the signed APK attached. `versionCode` increments every release.
- **Install:** open the release page on the phone, download the APK, allow installs from the browser once.
- **Updates:** manual for now (download the new APK, it installs over the old one and keeps config). Auto-update via Obtainium or an in-app updater is deferred.

## Inputs only the user can provide

Everything else is for the agent to research, measure and decide. These need the user's phone or accounts:

- **Niagara APK.** Not needed from the user: the agent downloads it from a public APK mirror (APKMirror, APKPure or similar; find the package name first), stores it as `vendor/niagara.apk` (git-ignored, never committed or redistributed; it is inspected for resource names only). This is a measure-and-decide step: inspect the APK with apktool, confirm drawable names are readable, record the names and flavours in `docs/decisions.md`, then wire the loader in. If the icons turn out obfuscated or generated at runtime, decide for option 2 (own icon set) and record why. If the download is blocked from the agent's environment, implement icons with the built-in fallback and ask the user to drop the APK into `vendor/`.
- **Real device.** Not required for functional verification; the E2E suite runs against Niagara on the emulator. The one thing the emulator can't show is Anycons (Pro-only), so the user compares the widget's icons against the launcher's on the Pixel 10 once per version and reports mismatches, which go to the backlog.
- **Signing keystore.** Generated by the agent, but the user stores the offline copy and adds it to GitHub Actions secrets.

## Non-goals (MVP)

- Any launcher other than Niagara.

- Walking / transit / cycling modes.
- Other map apps.
- Theming beyond what the icon pack provides.

## Version 2

- **Backup & restore.** Widget instance IDs change after a device-to-device restore, so config keyed by widget ID is orphaned (observed with Tasks widget on Niagara). Plan: store a single global destination list in app preferences with Auto Backup enabled, and have every widget instance render that list rather than per-instance config.
- Multiple independent widgets with different lists (only if the global-list approach above turns out too limiting).
- Other launchers: stock Pixel launcher first, then generic icon-pack support, as a test-matrix entry rather than per-launcher code.

## Testing

### Principle

Only true end-to-end tests count as proof that the widget works. Widgets render in the launcher's process, so Espresso cannot see them, and Robolectric does not render or launch anything. Unit and integration tests are welcome, but they do not replace the E2E suite below, and a green build without the E2E suite passing does not mean the app works.

### Tooling

- **UI Automator 2.3+** (`androidx.test.uiautomator:uiautomator`), run as instrumented tests via `./gradlew connectedAndroidTest`.
- Emulator: Pixel profile, **Google Play system image** (Google Maps must be installed for the navigation intent to resolve). Run headless in CI with `-no-window -no-boot-anim`, and wait for `sys.boot_completed` before starting tests.
- **Launcher under test is Niagara, and it is the only supported launcher in v1.** The test setup sideloads `vendor/niagara.apk` onto the emulator, sets it as the default launcher (`cmd package set-home-activity`), and dismisses its onboarding once. The emulator runs the free tier (no Pro licence), which is fine for everything except Anycons; icon appearance is checked by the user on the real phone, everything else by the suite. Every E2E scenario runs against Niagara's home screen and widget picker. Niagara's exact picker and widget-stack UI is a measure-and-decide step: explore it once, record the selectors in `docs/decisions.md`. Other launchers (stock Pixel included) are a v2 test-matrix entry, not v1 work.

### E2E scenarios (all through the real UI, no mocks)

1. **Place widget and configure**
   - Long-press Niagara's home screen → add widget → find the app's widget → place it in the widget stack below the notes widget.
   - Config activity opens. Set grid to 1×4, add three destinations with label and address, save.
   - Assert: widget on home screen shows exactly three labelled cells and one empty cell.

2. **Tap launches navigation**
   - Tap the first cell.
   - Assert: Google Maps (`com.google.android.apps.maps`) is the foreground package within a timeout, and the navigation UI is showing (e.g. the "Start"/route banner or active navigation chrome is visible, rather than the search screen).
   - Press back/home, repeat for each filled cell.

3. **Empty cell opens config**
   - Tap the empty cell.
   - Assert: the app's config screen for this widget is in the foreground, showing the three existing destinations.

3b. **Add via share from Google Maps**
   - Open Google Maps, search a place, tap Share, pick "Add to navigation widget" from the share sheet.
   - In the sheet, assert the label is pre-filled with the place name. Tap Add.
   - Assert: back in Google Maps; the widget on the home screen now shows a fourth labelled cell; tapping it launches navigation.

4. **Reconfigure**
   - Reopen config from the widget, change grid to 2×2, remove one destination, save.
   - Assert: widget re-renders as 2×2 with two filled cells and two empty cells, and tapping a filled cell still opens Maps navigation.

5. **Survives process death**
   - Force-stop the app (`am force-stop`), return to home screen.
   - Assert: widget still renders its cells and a tap still launches Maps.

6. **In-car flow**
   - From the home screen with the notes widget on top of the stack, swipe to reveal the navigation widget, tap a cell.
   - Assert: Google Maps navigation is in the foreground. This is the flow the whole app exists for.

7. **Long-press → Reconfigure** and **share with no widget placed → Place new widget**: measure on Niagara. If Niagara doesn't support one of them, record it in `docs/decisions.md`, drop that entry point from v1, and show the alternative entry points (empty cell, in-app list, pending list) work.

### Rules

- Tests drive the device the way a user would: real launcher, real widget placement, real Google Maps. No injecting config through shared preferences, no inspecting RemoteViews or PendingIntents in place of tapping.
- The E2E suite runs in CI on every change. It is the gate for "done".
- Flakiness is fixed by waiting on real UI conditions (`wait(Until.hasObject(...))`), not by retries or skipping assertions.
