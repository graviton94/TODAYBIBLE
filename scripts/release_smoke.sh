#!/usr/bin/env bash
# R8 로 줄인 release APK 가 실제로 도는지: 설치 → 켜기 → 화면 아무 데나 1500번 눌러 보기 (monkey) → 닫힘 · 오류 확인.
# 쓰는 곳: android-screens.yml ([shots:rel] 일 때). 결과: $1/release/ (화면 · 로그 · RESULT.txt)
set -u
OUT="${1:-shots}/release"; mkdir -p "$OUT"
PKG=io.github.graviton94.todaybible
APK=android/app/build/outputs/apk/release/app-release.apk
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r "$APK" || { echo "install failed" > "$OUT/RESULT.txt"; exit 0; }
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 8
adb exec-out screencap -p > "$OUT/launch.png"
adb shell monkey -p "$PKG" -s 7 --throttle 250 --pct-syskeys 0 --pct-appswitch 5 --ignore-security-exceptions -v 1500 > "$OUT/monkey.txt" 2>&1 || true
sleep 2; adb exec-out screencap -p > "$OUT/after.png"
adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" || grep -q "// CRASH" "$OUT/monkey.txt"; then
  { echo "CRASH"; grep -A30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60; } > "$OUT/RESULT.txt"
elif grep -q "ANR in $PKG" "$OUT/logcat.txt"; then
  { echo "ANR"; grep -A10 "ANR in $PKG" "$OUT/logcat.txt" | head -20; } > "$OUT/RESULT.txt"
else
  echo "OK · $(grep -c ':Sending' "$OUT/monkey.txt") events" > "$OUT/RESULT.txt"
fi
cat "$OUT/RESULT.txt"
