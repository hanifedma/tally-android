#!/usr/bin/env bash
# ============================================================
#  Screenshots of the Android app, into docs/screenshots/.
#
#      ./tools/screenshots.sh            # first connected device
#      ./tools/screenshots.sh <serial>   # a particular one
#
#  It drives device-only mode, so it needs no Supabase project and no Google
#  account — the app is fully usable without either, which is what makes this
#  runnable on a machine that has never been set up.
#
#  The ledger it photographs comes from the web repo's demo data, converted
#  to the Kotlin cache format by ../tally/tools/demo-cache.mjs, so the phone
#  and the browser are photographed holding exactly the same money and the
#  two sets of screenshots can be compared row by row.
#
#  Theme and language are set by seeding that ledger rather than by tapping
#  the toggles. A tap can land while a bottom sheet is still dismissing and
#  be swallowed, and a run that silently photographs the wrong state is worse
#  than one that fails.
# ============================================================
set -uo pipefail
# adb paths are device paths; MSYS would rewrite /sdcard into C:/... .
export MSYS_NO_PATHCONV=1

PKG=com.hanifedma.tally
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/screenshots"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"

ADB_BIN="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}/platform-tools/adb"
SERIAL="${1:-}"
if [ -n "$SERIAL" ]; then ADB="$ADB_BIN -s $SERIAL"; else ADB="$ADB_BIN"; fi

if ! $ADB get-state >/dev/null 2>&1; then
  echo "No device. Plug a phone in with USB debugging on, pair one over" >&2
  echo "wi-fi, or start an emulator:" >&2
  echo "  \$ANDROID_HOME/emulator/emulator -avd <name> -no-boot-anim" >&2
  exit 1
fi

say()  { printf '\n== %s\n' "$1"; }
shot() { sleep "${2:-3}"; $ADB exec-out screencap -p > "$OUT/$1.png"; echo "  $1.png"; }
ui()   { $ADB shell uiautomator dump /sdcard/tally-ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/tally-ui.xml; }

# Tap the centre of the first node whose text contains $1 — read from the
# hierarchy rather than hard-coded, so this survives a different screen size
# or a change of wording.
tap_text() {
  local b
  b=$(ui | tr '>' '\n' | grep -F "$1" \
      | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 | grep -oE '[0-9]+')
  if [ -z "$b" ]; then echo "  !! nothing matching: $1" >&2; return 1; fi
  set -- $b
  $ADB shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  sleep 3
}

# Every clickable node's bounds, one "x1 y1 x2 y2" per line, in tree order.
clickables() {
  ui | tr '>' '\n' | grep 'clickable="true"' \
     | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
     | grep -oE '[0-9]+' | paste - - - -
}

# The two buttons with neither text nor a content description: the menu, top
# right, and the add button, bottom right. Compose gives them no semantics to
# search for, so they are found by where they are — but "where" is read from
# the hierarchy, not guessed from a percentage of the screen, which is how
# this used to miss the button and photograph the screen behind it.
tap_corner() {
  local want="$1" line best bx by
  best=""
  while read -r x1 y1 x2 y2; do
    if [ "$want" = "top" ] && [ "$y1" -lt 300 ]; then
      if [ -z "$best" ] || [ "$x1" -gt "$bx" ]; then best="$x1 $y1 $x2 $y2"; bx=$x1; fi
    elif [ "$want" = "bottom" ] && [ "$y1" -gt 1000 ]; then
      if [ -z "$best" ] || [ "$y1" -gt "$by" ]; then best="$x1 $y1 $x2 $y2"; by=$y1; fi
    fi
  done <<EOF
$(clickables)
EOF
  if [ -z "$best" ]; then echo "  !! no $want-corner button" >&2; return 1; fi
  set -- $best
  $ADB shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  sleep 3
}

# Replace the device ledger and restart the app on it.
seed() {
  $ADB shell am force-stop $PKG
  sleep 1
  $ADB shell "run-as $PKG sh -c 'mkdir -p files/tally; cat > files/tally/cache-local.json'" < "$1"
  $ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
  sleep 8
}

say "Building and installing the debug build"
( cd "$ROOT" && ./gradlew --quiet assembleDebug ) || exit 1
$ADB install -r -g "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null || exit 1

say "Generating the demo ledger"
( cd "$ROOT/../tally" && node tools/demo-cache.mjs "$WORK/cache.json" ) || exit 1
# One variant, not two. Light and Korean are each one screen's worth of
# difference on a screen the gallery already shows twice; photographed
# together they cost one picture instead of two and say the same thing.
sed -e 's/"theme": "dark"/"theme": "light"/' -e 's/"lang": "en"/"lang": "ko"/' \
    "$WORK/cache.json" > "$WORK/cache-ko-light.json"

say "First run, nothing stored"
$ADB shell pm clear $PKG >/dev/null
$ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
# The sign-in screen on a configured build; the short setup screen on one
# built without supabase.properties. Either way it is the first thing anyone
# sees, which is what the README calls it.
shot "login" 8

say "One tap in, with no account"
tap_text "Use without an account" || tap_text "Use it on this device only"
sleep 2

say "A month of transactions"
seed "$WORK/cache.json"
shot "log-dark" 2

say "Insights and accounts"
tap_text "Insights" && shot "insights" 3
tap_text "Accounts" && shot "accounts" 3
tap_text "Log"      && sleep 2

say "The editor"
tap_corner bottom
shot "editor" 5
$ADB shell input keyevent KEYCODE_BACK
sleep 3

say "Settings"
tap_corner top
shot "settings" 4
$ADB shell input keyevent KEYCODE_BACK
sleep 3

say "Korean, in light"
seed "$WORK/cache-ko-light.json"
shot "log-korean-light" 3

say "Leaving the demo ledger in place"
seed "$WORK/cache.json"
$ADB shell rm -f /sdcard/tally-ui.xml >/dev/null 2>&1

echo
echo "Done — $OUT"
