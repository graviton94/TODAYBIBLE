#!/usr/bin/env bash
# 에뮬레이터에서 앱을 열어 장면마다 캡처 (.github/workflows/android-screens.yml).
# 디버그 빌드만 tb.* 실행 옵션을 읽는다 (MainActivity.debugSetup). 장: 0 오늘 · 1 필사 · 2 서재 · 3 기록
set -u
P=io.github.graviton94.todaybible
OUT=${1:-shots}
mkdir -p "$OUT"
DAY=2026-10-02
snap() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
shot() { sleep "$2"; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null; snap "$1"; }
open() { adb shell am force-stop $P; adb shell am start -W -n $P/.MainActivity --es tb.today $DAY "$@" >/dev/null; }
swipe_up() { adb shell input swipe 540 1800 540 700 500; }

adb shell settings put global hide_error_dialogs 1
sleep 30; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null

# 처음 한 번: 소개 여섯 장
open --ez tb.reset true --es tb.theme LIGHT; sleep 15
for i in 0 1 2 3 4 5; do open --ez tb.reset true --es tb.theme LIGHT --ei tb.welcomeStep $i; shot w0${i}_welcome 4; done
# 여는 순간: 금박 테 → 빛 → 표지 넘김 → 속표지
open --ez tb.seed true --es tb.theme LIGHT; sleep 4
adb shell am force-stop $P; adb shell am start -n $P/.MainActivity --es tb.today $DAY --ez tb.opening true >/dev/null
sleep 0.9; snap o01_cover; sleep 0.6; snap o02_shine; sleep 0.6; snap o03_turning; sleep 0.9; snap o04_title_page
# 오늘 · 필사 (책 · 노트) · 서재 · 기록
open;                                                              shot h01_today 5
swipe_up;                                                          shot h02_today_more 2
open --ei tb.page 1 --ez tb.notebook false;                        shot k01_copy_book 5
adb shell input tap 540 1200; sleep 2;                             shot k02_copy_keyboard 1
open --ei tb.page 1 --ez tb.notebook true;                         shot k03_copy_notebook 5
open --ei tb.page 1 --ez tb.notebook false
open --ei tb.page 2;                                               shot l01_library 5
open --ei tb.picker 1;                                             shot l02_chapters_illuminated 4
open --ei tb.page 3;                                               shot r01_record 5
open --es tb.plate noah;                                           shot r02_plate_full 4
swipe_up;                                                          shot r03_plate_text 2
open --es tb.plate moses_sea;                                      shot r04_plate_partial 4
open --es tb.finished 1:7;                                         shot f01_finished_veil 4
adb shell input tap 540 1810;                                      shot f02_finished_lifted 3
swipe_up;                                                          shot f03_finished_text 2
open --es tb.award OLIVE;                                          shot a01_award 4
open --ez tb.lock true --es tb.price ₩29,900 --ez tb.purchase true; shot e01_purchase_compare 4
open --ez tb.lock true --es tb.price ₩29,900 --ei tb.peek 43;      shot e02_purchase_peek 4
open --ez tb.settings true;                                        shot s01_settings 4
swipe_up;                                                          shot s02_settings_cover 2
open --ez tb.planSheet true;                                       shot p01_plan_sheet 4
open --es tb.plan mark30;                                          shot p02_today_plan 5
open --es tb.cover navy --es tb.owner 김은혜 --ez tb.opening true; sleep 3.2; snap p03_cover_navy_name
open --es tb.plan none --es tb.cover burgundy --es tb.owner ""
open --ei tb.page 1; adb shell input tap 790 550;                  shot p04_aloud_mic 3
open --ei tb.page 3;                                               shot r00_record_stats 5
open --ei tb.page 0 --ei tb.toast 5;                               shot t01_toast 1.5
open --ez tb.cardShots true
C=/sdcard/Android/data/$P/files/cards
for i in $(seq 1 45); do adb shell ls $C/done >/dev/null 2>&1 && break; sleep 2; done
mkdir -p "$OUT/cards"; adb pull $C/. "$OUT/cards/" >/dev/null 2>&1; rm -f "$OUT/cards/done"; ls "$OUT/cards"
# 다크
open --es tb.theme DARK;                                           shot d01_today 5
open --ei tb.page 1;                                               shot d02_copy_book 4
open --ei tb.page 1 --ez tb.notebook true;                         shot d03_copy_notebook 4
open --ei tb.page 1 --ez tb.notebook false
open --es tb.plate noah;                                           shot d04_plate 4
adb shell am force-stop $P; adb shell am start -n $P/.MainActivity --es tb.today $DAY --ez tb.opening true >/dev/null
sleep 1.4; snap d05_opening; sleep 1.6; snap d06_opening_title
open --es tb.theme LIGHT
# 영어 (KJV): 실제로 쳐 보기
adb shell cmd locale set-app-locales $P --locales en-US 2>/dev/null
open --ez tb.seed true --es tb.theme LIGHT --es tb.tr KJV;         shot e1_today 6
open --ei tb.page 1;                                               sleep 3
adb shell input tap 540 1200; sleep 2; adb shell input text "And%she%sgoeth%sup%sinto%sa%smountian"; shot e2_copy_typing 2
open --ei tb.page 1 --ez tb.notebook true;                         shot e3_notebook 4
open --ei tb.page 1 --ez tb.notebook false
open --ez tb.reset true --ei tb.welcomeStep 3;                     shot e4_welcome_goal 4
open --ez tb.seed true --es tb.tr KJV --es tb.plate prodigal;      shot e5_plate 5
open --ez tb.lock true --es tb.price "'\$29.99'" --ez tb.purchase true; shot e6_purchase 4
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null
# 큰 글자 · 작은 화면
open --ez tb.seed true --es tb.tr KRV --es tb.scale 1.3 --ei tb.page 1; shot x01_large_copy 5
open --ei tb.page 0;                                               shot x02_large_today 4
open --es tb.scale 1.0
adb shell wm size 720x1280; adb shell wm density 320
open --ei tb.page 0;                                               shot x03_small_today 5
open --ez tb.reset true --ei tb.welcomeStep 3;                     shot x04_small_welcome 4
adb shell wm size reset; adb shell wm density reset

adb logcat -d -s AndroidRuntime:E > "$OUT/logcat.txt" || true
echo "app ANR: $(adb logcat -d | grep -c "ANR in $P")" > "$OUT/anr.txt"; cat "$OUT/anr.txt"
ls -la "$OUT"
