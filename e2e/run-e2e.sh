#!/usr/bin/env bash
# Single entry point for the E2E suite (local and CI):
#   1. reuses a running emulator, or boots the AVD headless (cold boot, no snapshot writes);
#   2. provisions it with e2e/setup-emulator.sh when needed (Niagara missing / not default home / never set up);
#   3. pins the mock location, uninstalls the app (clears Niagara ghost widget IDs), installs app and test APKs,
#      force-stops Niagara, runs the :e2e suite, then GhostWidgetE2eTest last; prints merged results.
#
# Usage: e2e/run-e2e.sh [extra gradle args...]                     debug build of this checkout (normal dev run)
#        APP_APK=/path/to/app.apk e2e/run-e2e.sh [extra gradle args...]   a given app APK, e.g. the release APK to tag:
#          APP_APK=dist/navigation-shortcuts-0.4.1.apk e2e/run-e2e.sh  (release gate, docs/release-procedure.md)
#        APP_APK=release e2e/run-e2e.sh                             builds :app:assembleRelease of this checkout
#                      (needs the signing properties, docs/release-procedure.md) and tests that APK
# Env:   APP_APK       app APK to test instead of the debug build (see Usage). The installed app is uninstalled first,
#                      so a release-signed APK replaces a debug one and vice versa. The test APK (:e2e, debug-signed,
#                      self-instrumenting) needs no matching signature. With APP_APK the suite runs through
#                      `am instrument` (e2e/instrument-junit.py writes the JUnit XML), because Gradle's connected task
#                      would re-install the debug app. Against a release APK, IconsE2eTest's builtin-icon test needs
#                      debug-only store hooks and is reported as skipped (exactly 1 skip; decisions 6.2).
#        NIAGARA_APK   Niagara APK for provisioning (default: vendor/niagara.apk of this checkout, else of the
#                      main checkout when run from a git worktree under .claude/worktrees/)
#        AVD           AVD to boot when no device is connected (default navshort-e2e)
#        ANDROID_SERIAL  device to use when several are connected (also honoured by Gradle)
#        FORCE_SETUP=1 always run setup-emulator.sh
#        GEO_LNG/GEO_LAT mock location (default Ljubljana 14.5058 / 46.0569, same as setup-emulator.sh)
#        ANDROID_HOME  SDK (else ANDROID_SDK_ROOT, else ~/Library/Android/sdk on macOS, ~/Android/Sdk elsewhere)
#        E2E_TIMEOUT   overall limit in seconds for the Gradle part (build/install + both test passes); default 2700
#        SETUP_TIMEOUT limit in seconds for setup-emulator.sh (provisioning); default 1800
#        E2E_WAIT_SCALE  multiplier (1-10) for the suite's standard UI waits (5 s / 20 s); default 1
#        E2E_ARTIFACTS_DIR  if set: logcat of the whole run is written there, and on failure also a final screenshot,
#                      a uiautomator dump, `dumpsys activity activities`, the suite's own screenshots and the
#                      emulator log (when this script booted the emulator)
# Exit:  0 = all tests passed; non-zero = boot/setup/build/test failure (124 = E2E_TIMEOUT or SETUP_TIMEOUT hit).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
case "$(uname -s)" in Darwin) DEFAULT_SDK="$HOME/Library/Android/sdk" ;; *) DEFAULT_SDK="$HOME/Android/Sdk" ;; esac
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$DEFAULT_SDK}}"
export ANDROID_HOME
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD="${AVD:-navshort-e2e}"
GEO_LNG="${GEO_LNG:-14.5058}"; GEO_LAT="${GEO_LAT:-46.0569}"
SETUP_MARKER=/data/local/tmp/navshort-e2e-setup-done
E2E_TIMEOUT="${E2E_TIMEOUT:-2700}"
SETUP_TIMEOUT="${SETUP_TIMEOUT:-1800}"
E2E_WAIT_SCALE="${E2E_WAIT_SCALE:-1}"
ARTIFACTS="${E2E_ARTIFACTS_DIR:-}"
EMULATOR_LOG="${TMPDIR:-/tmp}/navshort-emulator.log"
E2E_PACKAGE=io.github.dreadboy.navigationshortcuts.e2e

log() { echo "[run-e2e] $*"; }
die() { echo "[run-e2e] ERROR: $*" >&2; exit 1; }
case "$E2E_WAIT_SCALE" in ''|*[!0-9]*|0*) die "E2E_WAIT_SCALE must be an integer 1-10 (got '$E2E_WAIT_SCALE')" ;; esac
[ "$E2E_WAIT_SCALE" -le 10 ] || die "E2E_WAIT_SCALE must be an integer 1-10 (got '$E2E_WAIT_SCALE')"

# A relative APP_APK is relative to the caller's directory (the script cds to the repo root later).
case "${APP_APK:-}" in
  ''|release|/*) ;;
  *) APP_APK="$PWD/$APP_APK" ;;
esac

if [ -z "${NIAGARA_APK:-}" ]; then
  NIAGARA_APK="$ROOT/vendor/niagara.apk"
  case "$ROOT" in
    */.claude/worktrees/*) [ -f "$NIAGARA_APK" ] || NIAGARA_APK="${ROOT%%/.claude/worktrees/*}/vendor/niagara.apk" ;;
  esac
fi

# ---------------------------------------------------------------- diagnostics (E2E_ARTIFACTS_DIR)
LOGCAT_PID=""
TOP_PID=""
BOOTED_HERE=""
# shellcheck disable=SC2329  # invoked by the EXIT trap
collect() {
  local rc=$?
  set +eu
  [ -n "$LOGCAT_PID" ] && { kill "$LOGCAT_PID" 2>/dev/null; wait "$LOGCAT_PID" 2>/dev/null; }
  [ -n "$TOP_PID" ] && { kill "$TOP_PID" 2>/dev/null; wait "$TOP_PID" 2>/dev/null; }
  if [ -n "$ARTIFACTS" ] && [ "$rc" != 0 ]; then
    mkdir -p "$ARTIFACTS"
    log "exit $rc: collecting diagnostics into $ARTIFACTS"
    [ -n "$BOOTED_HERE" ] && [ -f "$EMULATOR_LOG" ] && cp "$EMULATOR_LOG" "$ARTIFACTS/"
    "$ADB" devices -l > "$ARTIFACTS/adb-devices.txt" 2>&1 # is the device still there (offline / gone)?
    if [ -n "$ANDROID_SERIAL" ]; then
      a exec-out screencap -p > "$ARTIFACTS/final-screen.png" 2>/dev/null
      a shell uiautomator dump /sdcard/navshort-final-ui.xml >/dev/null 2>&1 \
        && a pull /sdcard/navshort-final-ui.xml "$ARTIFACTS/final-ui.xml" >/dev/null 2>&1
      a shell dumpsys activity activities > "$ARTIFACTS/dumpsys-activities.txt" 2>/dev/null
      # Screenshots the suite saves itself (ConfigE2eTest.saveScreenshot -> the test APK's external files dir).
      a pull "/sdcard/Android/data/$E2E_PACKAGE/files/screenshots" "$ARTIFACTS/test-screenshots" >/dev/null 2>&1
    fi
  fi
  return "$rc"
}
trap collect EXIT

# ---------------------------------------------------------------- device
devices() { "$ADB" devices | awk 'NR>1 && $2=="device" {print $1}'; }
if [ -z "${ANDROID_SERIAL:-}" ]; then
  n=$(devices | wc -l | tr -d ' ')
  if [ "$n" -gt 1 ]; then die "several devices connected ($(devices | tr '\n' ' ')); set ANDROID_SERIAL"; fi
  if [ "$n" -eq 0 ]; then
    log "no device: booting AVD '$AVD' headless"
    nohup "$EMULATOR" -avd "$AVD" -no-window -no-boot-anim -no-snapshot -no-audio \
      > "$EMULATOR_LOG" 2>&1 &
    BOOTED_HERE=1
    t0=$(date +%s)
    until [ -n "$(devices)" ]; do
      [ $(( $(date +%s) - t0 )) -lt 180 ] || die "emulator did not come online in 180 s (log: $EMULATOR_LOG)"
      sleep 2
    done
  fi
  ANDROID_SERIAL="$(devices | head -1)"
fi
export ANDROID_SERIAL
a() { "$ADB" -s "$ANDROID_SERIAL" "$@"; }

t0=$(date +%s)
until [ "$(a shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  [ $(( $(date +%s) - t0 )) -lt 300 ] || die "device $ANDROID_SERIAL did not finish booting in 300 s"
  sleep 2
done
log "device $ANDROID_SERIAL booted"
if [ -n "$ARTIFACTS" ]; then
  mkdir -p "$ARTIFACTS"
  # adb itself in the background (not the `a` function, whose subshell would leave adb running when killed).
  "$ADB" -s "$ANDROID_SERIAL" logcat -v threadtime > "$ARTIFACTS/logcat.txt" 2>&1 &
  LOGCAT_PID=$!
  log "logcat -> $ARTIFACTS/logcat.txt"
  # Emulator features in effect: host video decoders (qemu.hwcodec.*) and guest Vulkan. With CI's
  # -feature -HardwareDecoder / -feature -Vulkan there should be no hwcodec props and no ranchu Vulkan.
  props=$(a shell getprop 2>/dev/null | tr -d '\r' | grep -i -E 'hwcodec|vulkan' | tr '\n' ' ' || true)
  log "emulator media/vulkan props: ${props:-(none)}"
  # Guest load every 10 s, so a guest stall shows what was running (and when the device stopped answering).
  ( set +e
    tb=$(command -v timeout || command -v gtimeout)
    while :; do
      echo "== $(date -u +%T)"
      ${tb:+"$tb" 8} "$ADB" -s "$ANDROID_SERIAL" shell top -b -n 1 -m 12 2>&1 | tr -d '\r' | head -25
      sleep 10
    done ) > "$ARTIFACTS/guest-top.log" 2>&1 &
  TOP_PID=$!
  log "guest top every 10 s -> $ARTIFACTS/guest-top.log"
fi
# Play Store self-updates kill Maps mid-run and grow the AVD (decisions 1.2); idempotent, also in setup-emulator.sh.
a shell pm disable-user --user 0 com.android.vending >/dev/null 2>&1 || log "could not disable the Play Store"
a shell settings put global auto_update 0 >/dev/null 2>&1 || true
a shell svc power stayon true
a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard >/dev/null 2>&1 || true

# Runs a command until DEADLINE (epoch seconds; BUDGET names the limit in messages): GNU timeout/gtimeout when
# present, else a watchdog. Used for provisioning (SETUP_TIMEOUT) and for the Gradle part (E2E_TIMEOUT).
with_budget() {
  local left rc=0 t pid
  left=$(( DEADLINE - $(date +%s) ))
  if [ "$left" -le 0 ]; then log "ERROR: $BUDGET used up, not running: $*"; return 124; fi
  t=$(command -v timeout || command -v gtimeout || true)
  if [ -n "$t" ]; then
    # --foreground: stay in the terminal's process group so Ctrl-C reaches Gradle.
    "$t" --foreground -k 30 "$left" "$@" || rc=$?
  else
    local old_int old_term interrupted=""
    old_int=$(trap -p INT); old_term=$(trap -p TERM)
    "$@" &
    pid=$!
    # Ctrl-C / TERM: a background job ignores SIGINT in a script, so stop it explicitly instead of orphaning it.
    trap 'interrupted=1; kill "$pid" 2>/dev/null' INT TERM
    ( while kill -0 "$pid" 2>/dev/null; do
        if [ "$(date +%s)" -ge "$DEADLINE" ]; then
          kill -TERM "$pid" 2>/dev/null
          for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || exit 0; sleep 1; done
          kill -KILL "$pid" 2>/dev/null; exit 0
        fi
        sleep 2
      done ) &
    # wait returns early (>128) when a trapped signal arrives; wait again for the command to exit.
    wait "$pid" || rc=$?
    while kill -0 "$pid" 2>/dev/null; do wait "$pid" || rc=$?; done
    trap - INT TERM
    eval "$old_int"; eval "$old_term"
    [ -n "$interrupted" ] && { log "interrupted: $*"; exit 130; }
    [ "$rc" != 0 ] && [ "$(date +%s)" -ge "$DEADLINE" ] && rc=124
  fi
  [ "$rc" = 124 ] && log "ERROR: timed out after $BUDGET: $*"
  return "$rc"
}

# ---------------------------------------------------------------- provisioning
needs_setup=""
[ "${FORCE_SETUP:-}" = 1 ] && needs_setup="forced"
a shell pm list packages bitpit.launcher | grep -q 'package:bitpit.launcher$' || needs_setup="${needs_setup:-Niagara not installed}"
home=$(a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')
[ "$home" = "bitpit.launcher/.ui.HomeActivity" ] || needs_setup="${needs_setup:-default home is '$home'}"
a shell ls "$SETUP_MARKER" >/dev/null 2>&1 || needs_setup="${needs_setup:-never provisioned}"
if [ -n "$needs_setup" ]; then
  log "provisioning ($needs_setup) with Niagara APK $NIAGARA_APK"
  DEADLINE=$(( $(date +%s) + SETUP_TIMEOUT )); BUDGET="SETUP_TIMEOUT (${SETUP_TIMEOUT}s)"
  rc=0
  with_budget bash "$HERE/setup-emulator.sh" -s "$ANDROID_SERIAL" "$NIAGARA_APK" || rc=$?
  [ "$rc" = 0 ] || { echo "[run-e2e] ERROR: setup-emulator.sh failed (exit $rc)" >&2; exit "$rc"; }
  a shell touch "$SETUP_MARKER"
else
  log "already provisioned (Niagara is default home)"
fi

# ---------------------------------------------------------------- run
a emu geo fix "$GEO_LNG" "$GEO_LAT" >/dev/null || die "geo fix failed (not an emulator?)"
log "mock location lng=$GEO_LNG lat=$GEO_LAT"

cd "$ROOT"
DEADLINE=$(( $(date +%s) + E2E_TIMEOUT )); BUDGET="E2E_TIMEOUT (${E2E_TIMEOUT}s)"

RESULTS_DIR="$ROOT/e2e/build/outputs/androidTest-results/connected"
MERGED="$ROOT/e2e/build/outputs/run-e2e-results"
# Drop results of earlier runs so a build failure can't print a stale summary.
rm -rf "$RESULTS_DIR" "$MERGED"
mkdir -p "$MERGED"
start=$(date +%s)
status=0
APP_ID=io.github.dreadboy.navigationshortcuts
GHOST_CLASS=io.github.dreadboy.navigationshortcuts.e2e.GhostWidgetE2eTest
# Uninstall first: Niagara's "Replace widget" leaves widget IDs bound to our provider ("ghosts", decisions 3.9) that
# only an uninstall clears; GhostWidgetE2eTest creates them on purpose. Then install and restart Niagara: after our
# app is (re)installed, a running Niagara can keep a stale provider cache and show "Problem loading widget" for a
# newly placed widget (PLAN backlog). The test task below re-installs the same APKs with `install -r`, which is fine.
# The uninstall also lets a release-signed APP_APK replace a debug build and vice versa (different signing keys).
# Between tests, each setUp resets the app's data with `pm clear` (AppStore.reset, decisions 6.2).
a uninstall "$APP_ID" >/dev/null 2>&1 && log "uninstalled $APP_ID (clears ghost widget IDs)" || true
# Maps-stall recoveries the suite made (GoogleMaps.recoverMapsStall, decisions 6.2); printed after the summary.
STALL_LOG="/sdcard/Android/data/$E2E_PACKAGE/files/maps-stall.log"
a shell rm -f "$STALL_LOG" >/dev/null 2>&1 || true
if [ -n "${APP_APK:-}" ]; then
  if [ "$APP_APK" = release ]; then
    with_budget ./gradlew :app:assembleRelease "$@" || status=$?
    APP_APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
  fi
  if [ "$status" = 0 ]; then
    [ -f "$APP_APK" ] || die "APP_APK '$APP_APK' not found"
    log "app under test: $APP_APK (sha256 $(shasum -a 256 "$APP_APK" | cut -d' ' -f1))"
    a install "$APP_APK" >/dev/null || status=$?
  fi
  if [ "$status" = 0 ]; then
    with_budget ./gradlew :e2e:assembleDebug "$@" || status=$?
  fi
  if [ "$status" = 0 ]; then
    a install -r -t -g "$ROOT/e2e/build/outputs/apk/debug/e2e-debug.apk" >/dev/null || status=$?
  fi
  if [ "$status" = 0 ]; then
    log "installed app $(a shell dumpsys package "$APP_ID" | tr -d '\r' | grep -m1 -o 'versionName=.*' || true) and the test APK"
    a shell am force-stop bitpit.launcher
    log "Niagara force-stopped after install"
    RUNNER="$E2E_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
    # Same two passes as the Gradle path below. `am instrument` leaves the APKs installed.
    with_budget python3 -I "$HERE/instrument-junit.py" "$MERGED/1-TEST-instrument.xml" "$E2E_PACKAGE" \
      "$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -r -e waitScale "$E2E_WAIT_SCALE" \
      -e notClass "$GHOST_CLASS" "$RUNNER" || status=$?
    with_budget python3 -I "$HERE/instrument-junit.py" "$MERGED/2-TEST-instrument.xml" "$E2E_PACKAGE" \
      "$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -r -e waitScale "$E2E_WAIT_SCALE" \
      -e class "$GHOST_CLASS" "$RUNNER" || status=$?
  fi
else
  with_budget ./gradlew :app:installDebug :e2e:installDebug "$@" || status=$?
fi
if [ -z "${APP_APK:-}" ] && [ "$status" = 0 ]; then
  a shell am force-stop bitpit.launcher
  log "Niagara force-stopped after install"
  # Keep the APKs installed afterwards so the widget and app stay usable for manual checks.
  # Pass 1: everything except the ghost test; pass 2: the ghost test, last (it leaves ghost IDs behind).
  with_budget ./gradlew :e2e:connectedDebugAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.waitScale="$E2E_WAIT_SCALE" \
    -Pandroid.testInstrumentationRunnerArguments.notClass="$GHOST_CLASS" "$@" || status=$?
  for f in "$RESULTS_DIR"/debug/*.xml; do [ -f "$f" ] && cp "$f" "$MERGED/1-$(basename "$f")"; done
  with_budget ./gradlew :e2e:connectedDebugAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.waitScale="$E2E_WAIT_SCALE" \
    -Pandroid.testInstrumentationRunnerArguments.class="$GHOST_CLASS" "$@" || status=$?
  for f in "$RESULTS_DIR"/debug/*.xml; do [ -f "$f" ] && cp "$f" "$MERGED/2-$(basename "$f")"; done
fi

if ls "$MERGED"/*.xml >/dev/null 2>&1; then
  python3 -I - "$MERGED"/*.xml <<'EOF'
import html, re, sys
totals = [0, 0, 0, 0]
cases, failed = [], []
for path in sys.argv[1:]:
    s = open(path).read()
    suite = re.search(r'<testsuites? [^>]*tests="(\d+)" failures="(\d+)" errors="(\d+)" skipped="(\d+)"', s)
    if suite:
        totals = [t + int(v) for t, v in zip(totals, suite.groups())]
    for m in re.finditer(r'<testcase name="([^"]*)"[^>]*time="([^"]*)"', s):
        cases.append((m.group(1), m.group(2)))
    # Failing tests with the first line of their failure message.
    for m in re.finditer(r'<testcase name="([^"]*)"[^>]*?(?<!/)>(.*?)</testcase>', s, re.S):
        f = re.search(r'<(failure|error)[^>]*>(.*?)</\1>', m.group(2), re.S)
        if f:
            first = next((l.strip() for l in f.group(2).splitlines() if l.strip()), "")
            failed.append((m.group(1), html.unescape(first)))
print("[run-e2e] tests=%d failures=%d errors=%d skipped=%d" % tuple(totals))
for name, t in cases:
    print("[run-e2e]   %-80s %7ss" % (name, t))
for name, msg in failed:
    print("[run-e2e] FAILED %s: %s" % (name, msg))
for path in sys.argv[1:]:
    for m in re.finditer(r'<testcase name="([^"]*)"[^>]*?(?<!/)>(.*?)</testcase>', open(path).read(), re.S):
        if "<skipped" in m.group(2):
            print("[run-e2e] SKIPPED %s" % m.group(1))
EOF
else
  log "no test results (build or install failed before the tests ran)"
fi
stalls=$(a shell cat "$STALL_LOG" 2>/dev/null | tr -d '\r' | grep '^\[maps-stall\]' || true)
log "maps-stall recoveries: $(printf '%s' "$stalls" | grep -c . || true)"
[ -n "$stalls" ] && printf '%s\n' "$stalls" | sed 's/^/[run-e2e]   /'
log "suite finished in $(( $(date +%s) - start ))s, exit $status"
exit $status
