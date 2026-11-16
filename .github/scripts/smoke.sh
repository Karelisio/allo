#!/usr/bin/env bash
# Test de démarrage sur émulateur : installe l'APK release, rôle Téléphone, faux journal,
# parcourt les écrans, simule un appel GSM entrant, et échoue au moindre crash.
set -uo pipefail
PKG=com.karelisio.allo
APK=app/build/outputs/apk/release/app-release.apk
SHOTS=smoke-shots
mkdir -p "$SHOTS"

adb root > /dev/null 2>&1 || true
sleep 3
adb wait-for-device
adb install -r -g "$APK"
adb shell cmd role add-role-holder android.app.role.DIALER "$PKG" 0 || true
for p in CALL_PHONE READ_PHONE_STATE READ_PHONE_NUMBERS ANSWER_PHONE_CALLS READ_CALL_LOG WRITE_CALL_LOG \
         READ_CONTACTS WRITE_CONTACTS SEND_SMS POST_NOTIFICATIONS BLUETOOTH_CONNECT; do
  adb shell pm grant "$PKG" "android.permission.$p" > /dev/null 2>&1 || true
done

# Faux journal d'appels (entrants, sortants, manqués, démarchage).
now=$(($(date +%s) * 1000))
i=0
for entry in "0612345678:3" "0612345678:1" "0162123456:6" "+33700000000:2" "0145454545:1"; do
  i=$((i + 1))
  num="${entry%%:*}"; type="${entry##*:}"
  adb shell content insert --uri content://call_log/calls \
    --bind number:s:"$num" --bind type:i:"$type" --bind date:l:$((now - i * 3600000)) \
    --bind duration:i:$((i * 37)) --bind new:i:1 > /dev/null 2>&1 || true
done

adb logcat -c
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$SHOTS/$1.png" || true; }

adb shell am start -n "$PKG/.MainActivity" --ez allo.skip_onboarding true
shot 1-recents 10
adb shell am start -n "$PKG/.MainActivity" --es tab favorites
shot 2-favoris
adb shell am start -n "$PKG/.MainActivity" --es tab contacts
shot 3-contacts
adb shell am start -a android.intent.action.DIAL -d tel:0612 -n "$PKG/.MainActivity"
shot 4-clavier

# Appel GSM entrant simulé → InCallService → écran d'appel.
adb emu gsm call 0612345678
shot 5-appel-entrant 8
adb emu gsm accept 0612345678
shot 6-appel-en-cours 6
adb emu gsm cancel 0612345678
sleep 6

# Écran verrouillé + appel entrant.
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb emu gsm call 0145454545
shot 7-appel-verrouille 8
adb emu gsm cancel 0145454545
sleep 4

adb logcat -d > "$SHOTS/logcat.txt"
if grep -E "FATAL EXCEPTION|ANR in $PKG" -A 30 "$SHOTS/logcat.txt" | grep -q "$PKG"; then
  echo "::error::Crash détecté pendant le test sur émulateur"
  grep -E "FATAL EXCEPTION|ANR in" -A 40 "$SHOTS/logcat.txt" | head -120
  exit 1
fi
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "Note : le processus n'est plus actif (peut être normal après la fin d'appel)."
fi
echo "Test sur émulateur : aucun crash."
