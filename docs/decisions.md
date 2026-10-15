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
