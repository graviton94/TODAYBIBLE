#!/usr/bin/env bash
# 에뮬레이터에서 앱을 열어 장면마다 캡처 (.github/workflows/android-screens.yml).
# 디버그 빌드만 tb.* 실행 옵션을 읽는다 (MainActivity.debugSetup).
set -u
P=io.github.graviton94.todaybible
OUT=${1:-shots}
mkdir -p "$OUT"
DAY=2026-10-02
shot() { sleep "$2"; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
open() { adb shell am force-stop $P; adb shell am start -W -n $P/.MainActivity --es tb.today $DAY "$@" >/dev/null; }
swipe_up() { adb shell input swipe 540 1800 540 600 500; }
swipe_left() { adb shell input swipe 900 1200 150 1200 350; }

adb shell settings put global hide_error_dialogs 1
sleep 30; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null

# 처음 켬 (기록 없음) → 시험 기록이 쌓인 모습
open --ez tb.reset true --es tb.theme LIGHT; sleep 15
open --ez tb.reset true --es tb.theme LIGHT;                       shot k01_first_launch 6
open --ez tb.seed true --es tb.theme LIGHT;                        shot k02_library 6
swipe_up;                                                          shot k03_library_books 2
open --ei tb.picker 41;                                            shot k04_chapter_grid 4
open --ei tb.page 1;                                               shot k05_copy_type 5
adb shell input tap 540 1650; adb shell input text "taecho";       shot k06_copy_typing 3
open --ei tb.page 1; adb shell input tap 540 520;                  shot k07_copy_paper 3
open --ei tb.page 1; adb shell input tap 900 520;                  shot k08_copy_aloud 3
adb shell input tap 540 1150;                                      shot k09_copy_aloud_reading 6
open --ei tb.page 2;                                               shot k10_record 5
swipe_up;                                                          shot k11_record_plates 2
swipe_up; swipe_up;                                                shot k12_record_milestones 2
open --ez tb.settings true;                                        shot k13_settings 4
swipe_up;                                                          shot k14_settings_more 2
open --es tb.finished 1:7;                                         shot k15_finished_veil 5
adb shell input tap 540 1810;                                      shot k16_finished_lifting 1
                                                                   shot k17_finished_plate 3
open --es tb.finished 41:2;                                        shot k18_finished_plain 4
open --es tb.award OLIVE;                                          shot k19_award 4
open --ei tb.page 1 --ei tb.toast 35;                               shot k21_toast 1.5
# 평생권 · 잠금 · 나누기 · 위젯/카드 그림
open --ez tb.lock true --es tb.price ₩29,900;                       shot p01_library_locked 5
swipe_up;                                                          shot p02_library_locked_list 2
open --ez tb.purchase true --es tb.price ₩29,900;                  shot p03_purchase 4
open --ez tb.purchase true;                                        shot p04_purchase_not_ready 4
open --ez tb.purchase true --ez tb.owned true;                     shot p05_purchase_owned 4
open --ei tb.page 1 --ei tb.share 5;                               shot p06_share_sheet 5
open --ei tb.page 1; swipe_up; swipe_up;                           shot p07_chapter_lines 2
open --ez tb.cardShots true
C=/sdcard/Android/data/$P/files/cards
for i in $(seq 1 45); do adb shell ls $C/done >/dev/null 2>&1 && break; sleep 2; done
mkdir -p "$OUT/cards"; adb pull $C/. "$OUT/cards/" >/dev/null 2>&1; rm -f "$OUT/cards/done"; ls "$OUT/cards"
# 책장 넘김 도중
open --ei tb.page 0; adb shell input swipe 900 1200 450 1200 1200 & sleep 0.8; adb exec-out screencap -p > "$OUT/k20_turning.png"; wait; echo "shot k20_turning"
# 다크 · 영어(KJV) · 큰 글자 · 작은 화면
open --es tb.theme DARK;                                           shot d01_library 5
open --ei tb.page 1;                                               shot d02_copy 4
open --ei tb.page 2;                                               shot d03_record 4
open --ez tb.settings true;                                        shot d04_settings 4
open --es tb.finished 1:7;                                         shot d05_finished 4
open --es tb.award STAR;                                           shot d06_award 4
open --ei tb.picker 1;                                             shot d07_picker 4
open --ei tb.page 1 --ei tb.toast 3;                                shot d08_toast 1.5
adb shell cmd locale set-app-locales $P --locales en-US 2>/dev/null
open --ez tb.seed true --es tb.theme LIGHT --es tb.tr KJV;         shot e01_library 6
open --ei tb.page 1;                                               shot e02_copy 4
open --ei tb.page 1; adb shell input tap 900 520;                  shot e03_aloud 3
open --ei tb.page 2;                                               shot e04_record 4
swipe_up; swipe_up; swipe_up;                                      shot e05_milestones 2
open --ez tb.settings true;                                        shot e06_settings 4
open --es tb.finished 42:15;                                       shot e07_finished 4
open --es tb.award LAMP;                                           shot e08_award 4
open --ez tb.purchase true --es tb.price "'\$29.99'";                 shot e09_purchase 4
open --ez tb.cardShots true; sleep 12; mkdir -p "$OUT/cards_en"; adb pull /sdcard/Android/data/$P/files/cards/. "$OUT/cards_en/" >/dev/null 2>&1
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null
open --es tb.tr KRV --es tb.scale 1.3 --ei tb.page 1;              shot x01_large_copy 5
open --ei tb.page 0;                                               shot x02_large_library 4
open --es tb.scale 1.0
adb shell settings put system font_scale 1.3; open --ei tb.page 2; shot x03_fontscale_record 5
open --ez tb.settings true;                                        shot x04_fontscale_settings 4
adb shell settings put system font_scale 1.0
adb shell wm size 720x1280; adb shell wm density 320; open --ei tb.page 0; shot x05_small_library 5
open --ei tb.page 1;                                               shot x06_small_copy 4
open --es tb.finished 1:7;                                         shot x07_small_finished 4
adb shell wm size reset; adb shell wm density reset

adb logcat -d -s AndroidRuntime:E > "$OUT/logcat.txt" || true
echo "app ANR: $(adb logcat -d | grep -c "ANR in $P")" > "$OUT/anr.txt"; cat "$OUT/anr.txt"
ls -la "$OUT"
