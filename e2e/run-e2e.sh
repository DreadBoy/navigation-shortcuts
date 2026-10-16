#!/usr/bin/env bash
# Single entry point for the E2E suite (local and CI):
#   1. reuses a running emulator, or boots the AVD headless (cold boot, no snapshot writes);
#   2. provisions it with e2e/setup-emulator.sh when needed (Niagara missing / not default home / never set up);
#   3. pins the mock location, then builds + installs app and test APKs and runs the :e2e suite.
#
# Usage: e2e/run-e2e.sh [extra gradle args...]
# Env:   NIAGARA_APK   Niagara APK for provisioning (default: vendor/niagara.apk of this checkout, else of the
#                      main checkout when run from a git worktree under .claude/worktrees/)
#        AVD           AVD to boot when no device is connected (default navshort-e2e)
#        ANDROID_SERIAL  device to use when several are connected (also honoured by Gradle)
#        FORCE_SETUP=1 always run setup-emulator.sh
#        GEO_LNG/GEO_LAT mock location (default Ljubljana 14.5058 / 46.0569, same as setup-emulator.sh)
#        ANDROID_HOME  SDK (default ~/Library/Android/sdk)
# Exit:  0 = all tests passed; non-zero = boot/setup/build/test failure.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD="${AVD:-navshort-e2e}"
GEO_LNG="${GEO_LNG:-14.5058}"; GEO_LAT="${GEO_LAT:-46.0569}"
SETUP_MARKER=/data/local/tmp/navshort-e2e-setup-done

log() { echo "[run-e2e] $*"; }
die() { echo "[run-e2e] ERROR: $*" >&2; exit 1; }

if [ -z "${NIAGARA_APK:-}" ]; then
  NIAGARA_APK="$ROOT/vendor/niagara.apk"
  case "$ROOT" in
    */.claude/worktrees/*) [ -f "$NIAGARA_APK" ] || NIAGARA_APK="${ROOT%%/.claude/worktrees/*}/vendor/niagara.apk" ;;
  esac
fi

# ---------------------------------------------------------------- device
devices() { "$ADB" devices | awk 'NR>1 && $2=="device" {print $1}'; }
if [ -z "${ANDROID_SERIAL:-}" ]; then
  n=$(devices | wc -l | tr -d ' ')
  if [ "$n" -gt 1 ]; then die "several devices connected ($(devices | tr '\n' ' ')); set ANDROID_SERIAL"; fi
  if [ "$n" -eq 0 ]; then
    log "no device: booting AVD '$AVD' headless"
    nohup "$EMULATOR" -avd "$AVD" -no-window -no-boot-anim -no-snapshot -no-audio \
      > "${TMPDIR:-/tmp}/navshort-emulator.log" 2>&1 &
    t0=$(date +%s)
    until [ -n "$(devices)" ]; do
      [ $(( $(date +%s) - t0 )) -lt 180 ] || die "emulator did not come online in 180 s (log: ${TMPDIR:-/tmp}/navshort-emulator.log)"
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
a shell svc power stayon true
a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard >/dev/null 2>&1 || true

# ---------------------------------------------------------------- provisioning
needs_setup=""
[ "${FORCE_SETUP:-}" = 1 ] && needs_setup="forced"
a shell pm list packages bitpit.launcher | grep -q 'package:bitpit.launcher$' || needs_setup="${needs_setup:-Niagara not installed}"
home=$(a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')
[ "$home" = "bitpit.launcher/.ui.HomeActivity" ] || needs_setup="${needs_setup:-default home is '$home'}"
a shell ls "$SETUP_MARKER" >/dev/null 2>&1 || needs_setup="${needs_setup:-never provisioned}"
if [ -n "$needs_setup" ]; then
  log "provisioning ($needs_setup) with Niagara APK $NIAGARA_APK"
  bash "$HERE/setup-emulator.sh" -s "$ANDROID_SERIAL" "$NIAGARA_APK" || die "setup-emulator.sh failed"
  a shell touch "$SETUP_MARKER"
else
  log "already provisioned (Niagara is default home)"
fi

# ---------------------------------------------------------------- run
a emu geo fix "$GEO_LNG" "$GEO_LAT" >/dev/null || die "geo fix failed (not an emulator?)"
log "mock location lng=$GEO_LNG lat=$GEO_LAT"

cd "$ROOT"
# Drop results of earlier runs so a build failure can't print a stale summary.
rm -rf "$ROOT/e2e/build/outputs/androidTest-results/connected"
start=$(date +%s)
status=0
# Keep the APKs installed afterwards so the widget and app stay usable for manual checks.
./gradlew :e2e:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "$@" || status=$?

results=$(ls "$ROOT"/e2e/build/outputs/androidTest-results/connected/debug/*.xml 2>/dev/null | head -1 || true)
if [ -n "$results" ]; then
  python3 -I - "$results" <<'EOF'
import html, re, sys
s = open(sys.argv[1]).read()
suite = re.search(r'<testsuites? [^>]*tests="(\d+)" failures="(\d+)" errors="(\d+)" skipped="(\d+)"', s)
if suite:
    print("[run-e2e] tests=%s failures=%s errors=%s skipped=%s" % suite.groups())
for m in re.finditer(r'<testcase name="([^"]*)"[^>]*time="([^"]*)"', s):
    print("[run-e2e]   %-80s %7ss" % (m.group(1), m.group(2)))
# Failing tests with the first line of their failure message.
for m in re.finditer(r'<testcase name="([^"]*)"[^>]*?(?<!/)>(.*?)</testcase>', s, re.S):
    f = re.search(r'<(failure|error)[^>]*>(.*?)</\1>', m.group(2), re.S)
    if f:
        first = next((l.strip() for l in f.group(2).splitlines() if l.strip()), "")
        print("[run-e2e] FAILED %s: %s" % (m.group(1), html.unescape(first)))
EOF
else
  log "no test results (build or install failed before the tests ran)"
fi
log "suite finished in $(( $(date +%s) - start ))s, exit $status"
exit $status
