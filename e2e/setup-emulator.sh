#!/usr/bin/env bash
# Idempotent setup of a BOOTED emulator for Navigation Shortcuts E2E:
#   Niagara installed + default home + onboarding dismissed + widget-bind pre-granted,
#   mock GPS (Ljubljana), Google Maps permissions granted + first-run dialogs dismissed
#   (verified by reaching active navigation once), then Maps force-stopped and Niagara home shown.
#
# Usage: setup-emulator.sh [-s SERIAL] [path/to/niagara.apk]
# Env:   ADB (default $ANDROID_HOME/platform-tools/adb), GEO_LNG/GEO_LAT (default 14.5058 / 46.0569)
#        SETUP_LOOP_SCALE  multiplies the retry counts of the Niagara onboarding, clock long-press and Maps navigation loops
#                          (default 1; CI uses 3 for the slow software-GPU emulator)
# Exit:  0 = ready, non-zero = a step failed (message on stderr).
# Measured on: API 36 google_apis_playstore arm64 (r07), Pixel 8 profile, Niagara 1.16.33,
#              Google Maps 25.11.01 (preinstalled, signed out).
set -u
SERIAL=""
if [ "${1:-}" = "-s" ]; then SERIAL="$2"; shift 2; fi
APK="${1:-$(cd "$(dirname "$0")" && pwd)/../../vendor/niagara.apk}"
[ -f "$APK" ] || APK="$(cd "$(dirname "$0")/.." && pwd)/vendor/niagara.apk"
ADB="${ADB:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb}"
GEO_LNG="${GEO_LNG:-14.5058}"; GEO_LAT="${GEO_LAT:-46.0569}"
SCALE="${SETUP_LOOP_SCALE:-1}"
NIAGARA=bitpit.launcher
NIAGARA_HOME=bitpit.launcher/.ui.HomeActivity
MAPS=com.google.android.apps.maps

a() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }
log() { echo "[setup] $*"; }
die() { echo "[setup] ERROR: $*" >&2; exit 1; }

# uiautomator dump of the focused window (empty string if the UI never goes idle, e.g. Maps navigation)
dump() { a shell rm -f /sdcard/ui.xml >/dev/null 2>&1; a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; a exec-out cat /sdcard/ui.xml 2>/dev/null; }

# Prints "x y" centre of first node whose text, content-desc or resource-id matches the regex.
find_center() { # $1 = xml, $2 = regex
  python3 -I -c '
import re,sys
s=sys.stdin.read(); pat=re.compile(sys.argv[1])
for m in re.finditer(r"<node ([^>]*)",s):
    at=dict(re.findall(r"(\S+)=\"([^\"]*)\"",m.group(1)))
    if any(pat.search(at.get(k,"")) for k in ("text","content-desc","resource-id")):
        x1,y1,x2,y2=map(int,re.findall(r"\d+",at["bounds"])); print((x1+x2)//2,(y1+y2)//2); break
' "$2" <<<"$1"
}
tap_if() { # $1 xml, $2 regex, $3 label -> 0 if tapped
  local c; c=$(find_center "$1" "$2"); [ -n "$c" ] || return 1
  log "tap: $3 ($c)"; a shell input tap $c; return 0
}
top_activity() { a shell dumpsys activity activities | grep -m1 -E 'topResumedActivity' | sed 's/.*u0 //; s/ t[0-9]*}.*//'; }
maps_nav_visible() {
  a shell dumpsys activity top | awk '/ACTIVITY com.google.android.apps.maps/{m=1} m' \
    | grep -E 'app:id/(navigation_time_remaining_label|step_instruction_content)' | grep -qE '\{[0-9a-f]+ V'
}

# ---------------------------------------------------------------- device basics
a wait-for-device
for i in $(seq 1 180); do [ "$(a shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 1; done
[ "$(a shell getprop sys.boot_completed | tr -d '\r')" = 1 ] || die "device not booted"
log "booted: $(a shell getprop ro.build.fingerprint | tr -d '\r')"
# Play Store off as early as possible: its self-update (~3 min after first boot) restarts GMS, kills Maps and
# grows the AVD's userdata (decisions 1.2). Idempotent; Maps keeps working.
if a shell pm disable-user --user 0 com.android.vending >/dev/null 2>&1; then log "Play Store disabled"; else log "could not disable the Play Store"; fi
a shell settings put global auto_update 0
a shell settings put secure immersive_mode_confirmations confirmed   # suppress "Viewing full screen" cling
a shell svc power stayon true
a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard >/dev/null 2>&1 || true

# ---------------------------------------------------------------- Niagara
if ! a shell pm list packages "$NIAGARA" | grep -q "package:$NIAGARA\$"; then
  [ -f "$APK" ] || die "Niagara APK not found: $APK"
  log "installing $APK"; a install -r "$APK" >/dev/null || die "install failed"
fi
a shell cmd package set-home-activity "$NIAGARA_HOME" >/dev/null || die "set-home-activity failed"
a shell appwidget grantbind --package "$NIAGARA" --user 0 >/dev/null 2>&1 || true   # no "Create widget and allow access?" dialog
resolved=$(a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')
[ "$resolved" = "$NIAGARA_HOME" ] || die "default home is '$resolved'"
log "default home: $resolved"

a shell input keyevent KEYCODE_HOME; sleep 2
for i in $(seq 1 $((15 * SCALE))); do
  x=$(dump)
  if tap_if "$x" 'com.android.systemui:id/ok' 'system cling Got it'; then sleep 2; continue; fi
  if tap_if "$x" '^bitpit.launcher:id/button_positive$' 'Agree & Continue'; then sleep 3; continue; fi
  if grep -q 'text="Your favorites"' <<<"$x"; then
    # "Done" does nothing with zero favorites selected: pick Maps once, then Done.
    if [ -z "${FAV_PICKED:-}" ]; then tap_if "$x" '^Maps$' 'favorite Maps' && FAV_PICKED=1; sleep 1; x=$(dump); fi
    tap_if "$x" '^bitpit.launcher:id/large_fab_extended$' 'Favorites Done' && { sleep 3; continue; }
  fi
  if tap_if "$x" '^Get started$' 'Get started'; then sleep 3; continue; fi
  if tap_if "$x" '^bitpit.launcher:id/floating_tip_button$' 'tip Next'; then sleep 2; continue; fi
  if grep -q 'resource-id="bitpit.launcher:id/toolbar_text"' <<<"$x"; then log "closing search"; a shell input keyevent KEYCODE_BACK; sleep 1; continue; fi
  if grep -q 'resource-id="bitpit.launcher:id/clock"' <<<"$x" && ! grep -q 'dismiss_view\|floating_tip' <<<"$x"; then
    log "Niagara home ready"; break; fi
  if [ -z "$x" ] || ! grep -q 'package="bitpit.launcher"' <<<"$x"; then a shell input keyevent KEYCODE_HOME; fi
  sleep 2
done
grep -q 'resource-id="bitpit.launcher:id/clock"' <<<"$(dump)" || die "Niagara home not reached"

# Fresh Niagara shows an animated "swipe the alphabet" hint; until the user swipes the alphabet
# bar once, long-press on the clock (the entry to "Add Custom Widget") does nothing.
# Swipe it once (harmless when already done), then go back to home.
a shell input swipe 1000 1100 1000 1900 800; sleep 2
a shell input keyevent KEYCODE_BACK; sleep 2
grep -q 'resource-id="bitpit.launcher:id/toolbar_text"' <<<"$(dump)" && { a shell input keyevent KEYCODE_BACK; sleep 1; }
# Verify the widget entry point works: long-press clock -> "Niagara widget" sheet with "Add Custom Widget".
c=$(find_center "$(dump)" '^bitpit.launcher:id/clock$'); [ -n "$c" ] || die "clock not found"
ok=0
for i in $(seq 1 $((3 * SCALE))); do
  a shell input swipe $c $c 1500; sleep 1.5
  if grep -q 'text="Add Custom Widget"' <<<"$(dump)"; then ok=1; break; fi
  a shell input keyevent KEYCODE_BACK; sleep 1
done
[ "$ok" = 1 ] || die "long-press on clock did not open the Niagara widget sheet"
log "clock long-press -> Niagara widget sheet OK"
a shell input keyevent KEYCODE_BACK; sleep 1

# ---------------------------------------------------------------- mock location
a emu geo fix "$GEO_LNG" "$GEO_LAT" >/dev/null || die "geo fix failed"
log "geo fix lng=$GEO_LNG lat=$GEO_LAT"

# ---------------------------------------------------------------- Google Maps
a shell pm list packages "$MAPS" | grep -q "package:$MAPS" || die "Google Maps not installed (need a Google Play system image)"
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do
  a shell pm grant "$MAPS" "android.permission.$p" >/dev/null 2>&1 || true
done
a shell am force-stop "$MAPS"
t0=$(date +%s)
a shell am start -a android.intent.action.VIEW -d "google.navigation:q=Maribor%2C%20Slovenija\&mode=d" -p "$MAPS" >/dev/null
ok=0
for i in $(seq 1 $((30 * SCALE))); do
  a emu geo fix "$GEO_LNG" "$GEO_LAT" >/dev/null
  if maps_nav_visible; then ok=1; break; fi
  x=$(dump)
  if [ -n "$x" ]; then
    tap_if "$x" '^Skip$' 'Maps sign-in Skip' && { sleep 2; continue; }
    if grep -q 'navigation_welcome_title_text' <<<"$x"; then tap_if "$x" '^android:id/button1$' 'Maps nav disclaimer Got it' && { sleep 2; continue; }; fi
    tap_if "$x" 'com.google.android.apps.maps:id/dialog_positive_button' 'Maps nav-data Dismiss' && { sleep 2; continue; }
    tap_if "$x" 'permission_allow_foreground_only_button' 'location While using the app' && { sleep 2; continue; }
    tap_if "$x" 'permission_allow_button' 'notifications Allow' && { sleep 2; continue; }
  fi
  sleep 1
done
[ "$ok" = 1 ] || die "Maps navigation UI not reached (top: $(top_activity))"
log "Maps navigation reached in $(( $(date +%s) - t0 ))s"
a shell am force-stop "$MAPS"; sleep 2
# NOTE: KEYCODE_HOME while Niagara home is already in front opens Niagara's app search; close it.
case "$(top_activity)" in bitpit.launcher/.ui.HomeActivity*) ;; *) a shell input keyevent KEYCODE_HOME; sleep 2;; esac
grep -q 'resource-id="bitpit.launcher:id/toolbar_text"' <<<"$(dump)" && { a shell input keyevent KEYCODE_BACK; sleep 1; a shell input keyevent KEYCODE_BACK; sleep 1; }
grep -q 'resource-id="bitpit.launcher:id/clock"' <<<"$(dump)" || die "Niagara home not visible at the end"
log "top: $(top_activity)"
log "DONE"
