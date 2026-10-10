#!/usr/bin/env bash
# 에뮬레이터에서 앱을 열어 장면마다 캡처 (.github/workflows/android-screens.yml).
# 디버그 빌드만 tb.* 실행 옵션을 읽는다 (MainActivity.debugSetup). 장: 0 오늘 · 1 성경 · 2 필사 · 3 기록
set -u
# 일부 장면만 (ONLY="q01|p05"): 이름이 맞지 않는 shot 줄은 빼고 다시 실행 (준비 줄은 그대로)
if [ -n "${ONLY:-}" ] && [ -z "${FILTERED:-}" ]; then
  f=$(mktemp)
  # shot 줄은 이름이 맞을 때만, '# @scene 태그' … '# @end' 묶음 (여는 순간 · 카드처럼 무거운 장면) 은 태그가 맞을 때만
  awk -v re="shot (${ONLY})[a-z0-9_]* " -v only="${ONLY}" '
    /^# @scene / { tag = substr($0, 10); skip = (tag !~ "^(" only ")" && only !~ tag); next }
    /^# @end/ { skip = 0; next }
    skip { next }
    !/shot [a-z0-9_]+ [0-9.]+/ || $0 ~ re' "$0" > "$f"
  FILTERED=1 exec bash "$f" "$@"
fi
P=io.github.graviton94.todaybible
OUT=${1:-shots}
mkdir -p "$OUT"
DAY=2026-10-02
snap() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png" || echo "snap timed out: $1" | tee -a "$OUT/STUCK.txt"; echo "shot $1"; }
shot() { sleep "$2"; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null; snap "$1"; }
open() { adb shell am force-stop $P; timeout 40 adb shell am start -W -n $P/.MainActivity --es tb.today $DAY --ez tb.coach false "$@" >/dev/null || echo "open timed out: $*" | tee -a "$OUT/STUCK.txt"; }
swipe_up() { adb shell input swipe 540 1800 540 700 500; }

adb shell settings put global hide_error_dialogs 1
sleep "${WARM:-30}"; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null

# 처음 한 번: 소개 일곱 장 (일부 장면만 찍을 땐 건너뜀)
# @scene w0
open --ez tb.reset true --es tb.theme LIGHT; sleep 15
for i in 0 1 2; do open --ez tb.reset true --es tb.theme LIGHT --ei tb.welcomeStep $i; shot w0${i}_welcome 4; done
# @end
# 일부 장면만 찍을 때: 앞 장면들이 깔아 두던 기록을 한 번에
[ -n "${FILTERED:-}" ] && { open --ez tb.reset true --es tb.theme LIGHT; sleep 4; open --ez tb.seed true --es tb.tr KRV --es tb.theme LIGHT; sleep 4; }
# 여는 순간: A 금박 새김 (매일) · C 표지 넘김 (하루 첫 열기). 눌러야 들어가요
# @scene o0
open --ez tb.seed true --es tb.theme LIGHT; sleep 4
adb shell am force-stop $P; adb shell am start -n $P/.MainActivity --es tb.today $DAY --es tb.intro A >/dev/null
sleep 5; snap o01_daily_drawing; sleep 3; snap o02_daily_ready
adb shell input tap 540 1200; sleep 1.5; snap o03_daily_entered
adb shell am force-stop $P; adb shell am start -n $P/.MainActivity --es tb.today $DAY --es tb.intro C >/dev/null
sleep 7; snap o04_cover; adb shell input tap 540 1200; sleep 0.7; snap o05_cover_turning; sleep 3.5; snap o06_title_page
adb shell input tap 540 1200; sleep 1.5; snap o07_cover_entered
# @end
# 오늘 · 필사 (책 · 노트) · 서재 · 기록
open --es tb.owner 은혜;                                             shot h01_today 5
swipe_up;                                                          shot h02_today_more 2
open --es tb.owner ""
open --ei tb.page 2;                        shot k01_copy_grid 5
adb shell input tap 540 1200; sleep 2;                             shot k02_copy_keyboard 1
open --ei tb.page 1;                                               shot l01_library 5
open --ei tb.picker 1;                                             shot l02_chapters_illuminated 4
open --ei tb.page 3;                                               shot r01_record 5
open --es tb.plate noah;                                           shot r02_plate_full 4
swipe_up;                                                          shot r03_plate_text 2
open --es tb.plate moses_sea;                                      shot r04_plate_partial 4
open --es tb.finished 1:7;                                         shot f01_finished_veil 4
adb shell input tap 540 1810;                                      shot f02_finished_lifted 3
swipe_up;                                                          shot f03_finished_text 2
open --es tb.award OLIVE;                                          shot a01_award 4
open --ez tb.lock true --es tb.price ₩9,900 --es tb.monthPrice ₩2,900 --es tb.memberPrice ₩6,900 --ez tb.purchase true; shot e01_purchase_compare 4
open --ez tb.lock true --es tb.price ₩9,900 --es tb.monthPrice ₩2,900 --es tb.memberPrice ₩6,900 --ez tb.subscribed true --ez tb.purchase true; shot e03_purchase_subscribed 4
open --ez tb.lock true --es tb.price ₩14,900 --ei tb.peek 43;      shot e02_purchase_peek 4
open --ez tb.settings true;                                        shot s01_settings 4
swipe_up;                                                          shot s02_settings_cover 2
open --ez tb.planSheet true;                                       shot p01_plan_sheet 4
open --es tb.plan mark30;                                          shot p02_today_plan 5
# @scene p03
open --es tb.cover navy --es tb.owner 김은혜 --es tb.intro C; sleep 7; snap p03_cover_navy_name
# @end
open --es tb.plan none --es tb.cover burgundy --es tb.owner ""
open --ei tb.page 2 --ei tb.copyTab 0;                             shot p04_aloud_mic 3
open --ei tb.page 2 --ei tb.copyTab 0 --ez tb.voice true; swipe_up; swipe_up; shot p05_aloud_voice 3
open --ez tb.settings true --es tb.section set_listen;              shot p06_settings_voice 3
# 손글씨: 빈 공책 → 몇 획 그은 공책
open --ei tb.page 2 --ei tb.copyTab 2;                             shot p07_hand_empty 3
for st in "180 1000 300 1000" "240 960 240 1040" "200 1060 290 1080" "340 990 420 990" "380 990 370 1090" "370 1040 430 1050" "470 980 470 1100" "520 1000 620 1000" "570 960 560 1100" "520 1060 610 1060"; do adb shell input swipe $st 180; done
shot p08_hand_written 2
open --ez tb.voice false
# 낭독 세 갈래 · 원고지 · 형광펜 · 한 해 · 밤 · 아주 크게
open --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 0;         shot q01_aloud_responsive 6
open --es tb.listen "41:3";                                        shot q02_listen_reader 4
open --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 2;         shot q03_aloud_alone_big 4
open --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 2 --ez tb.aloudBig false; shot q04_aloud_alone_small 4
open --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 0 --ez tb.aloudBig true
open --ei tb.page 2 --ei tb.copyTab 1;          shot k04_copy_grid 4
open --ei tb.page 2 --ei tb.copyTab 1 --es tb.mark "1,2"; shot k05_marked 4
# 첫 안내 (처음 들어온 것처럼)
open --ez tb.coach true;                                           shot c01_coach_today 5
open --ez tb.coach true --ei tb.page 1;                            shot c02_coach_library 5
open --ez tb.coach true --es tb.listen "41:3";                     shot c03_coach_reader 5
open --ez tb.coach true --ei tb.page 2 --ei tb.copyTab 0;          shot c04_coach_aloud 5
open --ez tb.coach true --ei tb.page 2 --ei tb.copyTab 1;          shot c05_coach_type 5
open --ez tb.coach true --ei tb.page 3;                            shot c06_coach_record 5
# 성경: 읽기 화면에서 절을 누르면 띠
open --es tb.listen "41:3"; adb shell input tap 540 900;           shot l03_reader_verse 2
open --ei tb.page 3 --es tb.mark "1,2";                            shot r05_record_marks 4
swipe_up; swipe_up;                                                shot r06_record_marks_more 2
open --ei tb.page 3 --ez tb.year true;                             shot r07_year_card 4
open --ez tb.settings true; swipe_up; swipe_up; swipe_up;          shot s03_settings_more 2
swipe_up; swipe_up;                                                shot s04_settings_end 2
swipe_up; swipe_up;                                                shot s05_settings_feedback 2
open --ez tb.night true;                                           shot n01_night_today 4
open --ez tb.night true --ei tb.page 2 --ei tb.copyTab 0;          shot n02_night_aloud 4
open --ef tb.scale 1.15 --ez tb.contrast true --ei tb.page 2 --ei tb.copyTab 0; shot x05_huge_contrast 4
open --ef tb.scale 1.0 --ez tb.contrast false
open --ei tb.page 3;                                               shot r00_record_stats 5
open --ei tb.page 0 --ei tb.toast 5;                               shot t01_toast 1.5
# @scene wp
open --ez tb.seed true --ez tb.widgetPreviews true
W=/sdcard/Android/data/$P/files/previews
for i in $(seq 1 30); do adb shell ls $W/done >/dev/null 2>&1 && break; sleep 2; done
mkdir -p "$OUT/previews"; adb pull $W/. "$OUT/previews/" >/dev/null 2>&1; rm -f "$OUT/previews/done"; ls "$OUT/previews"
# @end
# @scene cards
open --ez tb.cardShots true
C=/sdcard/Android/data/$P/files/cards
for i in $(seq 1 45); do adb shell ls $C/done >/dev/null 2>&1 && break; sleep 2; done
mkdir -p "$OUT/cards"; adb pull $C/. "$OUT/cards/" >/dev/null 2>&1; rm -f "$OUT/cards/done"; ls "$OUT/cards"
# @end
# 마지막 다듬기: 낱말 찾기 · 마음에 새기기 · 주일 설교 노트 · 묵상 한 줄 · 서가와 두루마리 · 듣기 타이머
open --ez tb.seed true --es tb.find 목자;                            shot g01_find 4
open --es tb.memory "19:23:1" --ez tb.memoryOpen true;             shot g02_memory 4
open --es tb.memory "19:23:1" --ez tb.memoryOpen true --ei tb.memoryLevel 1; shot g03_memory_initials 4
open --es tb.memory "19:23:1" --ez tb.memoryOpen true --ei tb.memoryLevel 2; shot g04_memory_blank 4
open --es tb.today 2026-10-04 --es tb.sermon "'요 3:16-21|세상을 사랑하신 마음을 다시 생각했어요. 이번 주는 가족에게 먼저 연락하기.'";  shot g05_today_sunday 5
swipe_up;                                                          shot g06_today_sunday_more 2
open --es tb.today 2026-10-04 --ez tb.sermonOpen true;             shot g07_sermon_sheet 4
open --ei tb.page 3;                                               shot g08_record_shelf 5
swipe_up;                                                          shot g09_record_scroll 2
open --es tb.finished 1:7 --es tb.reflect "'물이 걷히고 다시 시작하게 하시는 분'";
adb shell input tap 540 1810; sleep 3; swipe_up;                   shot g10_finished_reflect 2
open --es tb.listen "41:3" --ez tb.listenPlay true;                shot g11_listen_controls 6
adb shell am startservice -a stop -n $P/.data.ListenService >/dev/null 2>&1 || true
# 기도 · 한영 대조 · 낭독 받아 두기 · 태블릿
open --es tb.prayer morning;                                       shot h11_prayer_morning 4
swipe_up;                                                          shot h12_prayer_more 2
open --ez tb.prayers true --ei tb.page 1;                          shot h13_prayer_list 4
open --es tb.prayer lords --ef tb.scale 1.15;                       shot h14_prayer_huge 4
open --ez tb.lock true --es tb.prayerWrite lords;                  shot h19_prayer_write_locked 5
open --es tb.prayer morning --es tb.prayerPlay morning;             sleep 4; adb shell input keyevent 4; shot h20_leave_listening 2
open --es tb.prayerPlay morning --ei tb.page 0;                    shot h21_now_playing_bar 4
open --ez tb.prayers true --es tb.prayerTimes "morning=420,night=1290";  shot h22_prayer_bells 4
open --es tb.prayerTimes morning=420 --es tb.prayer morning --es tb.prayerBell morning; shot h23_prayer_bell_sheet 4
open --ei tb.page 0;                                               shot h24_today_prayer_link 4
open --es tb.listen "43:3" --ez tb.parallel true --ef tb.scale 1.0; shot h15_parallel 4
open --es tb.listen "43:3" --ez tb.parallel false;                 sleep 1
open --ei tb.picker 19;                                            shot h16_keep_narration 4
adb shell wm size 1600x2560; adb shell wm density 320
open --ei tb.page 2 --ei tb.copyTab 2;                             shot h17_tablet_hand 5
open --ei tb.page 0;                                               shot h18_tablet_today 4
adb shell wm size reset; adb shell wm density reset
# 시험 직전 고침: 설정 (접힘 · 갈래) · 언어 묻기 · 평생권 · 타자 틀림
open --ez tb.settings true --es tb.section none;                   shot j01_settings_folded 4
open --ez tb.settings true;                                        shot j02_settings_display 4
open --ez tb.settings true --es tb.section set_alerts;             shot j03_settings_alerts 4
open --ez tb.settings true --es tb.askLang en;                     shot j04_language_confirm 4
open --ez tb.seed true;  swipe_up; swipe_up; swipe_up;             shot j05_today_lifetime 2
open --ei tb.page 1; swipe_up; swipe_up;                           shot j06_library_tags 3
open --ei tb.page 2 --ei tb.copyTab 1; adb shell input tap 540 2150; sleep 2
adb shell input text "zz"; sleep 1.5;                              shot j07_typing_wrong 1
# 아주 크게 (B3): 새 화면들을 큰 글씨로
open --ef tb.scale 1.15 --ez tb.seed true;                          shot x06_huge_today 5
open --ef tb.scale 1.15 --es tb.today 2026-10-04 --ez tb.sermonOpen true; shot x07_huge_sermon 4
open --ef tb.scale 1.15 --es tb.memory "19:23:1" --ez tb.memoryOpen true --ei tb.memoryLevel 1; shot x08_huge_memory 4
open --ef tb.scale 1.15 --ei tb.page 3;                             shot x09_huge_record 5
open --ef tb.scale 1.15 --ei tb.page 1;                             shot x10_huge_library 5
open --ef tb.scale 1.15 --es tb.listen "41:3";                      shot x11_huge_reader 4
open --ef tb.scale 1.15 --ez tb.settings true;                      shot x12_huge_settings 4
open --ef tb.scale 1.15 --ei tb.page 2 --ei tb.copyTab 1;           shot x13_huge_type 4
open --ef tb.scale 1.0
# 다크
open --es tb.theme DARK;                                           shot d01_today 5
open --ei tb.page 2;                                               shot d02_copy_grid 4
open --es tb.plate noah;                                           shot d04_plate 4
# @scene d05|d06
adb shell am force-stop $P; adb shell am start -n $P/.MainActivity --es tb.today $DAY --es tb.intro A >/dev/null
sleep 8; snap d05_daily
adb shell am force-stop $P; adb shell am start -n $P/.MainActivity --es tb.today $DAY --es tb.intro C >/dev/null
sleep 7; adb shell input tap 540 1200; sleep 4.2; snap d06_title_page
# @end
open --es tb.theme LIGHT
# 영어 (KJV): 실제로 쳐 보기
adb shell cmd locale set-app-locales $P --locales en-US 2>/dev/null
open --ez tb.seed true --es tb.theme LIGHT --es tb.tr KJV;         shot e1_today 6
open --ei tb.page 2;                                               sleep 3
adb shell input tap 540 1200; sleep 2; adb shell input text "And%she%sgoeth%sup%sinto%sa%smountian"; shot e2_copy_typing 2
open --ei tb.page 2;                         shot e3_notebook 4
open --ez tb.reset true --ei tb.welcomeStep 2;                     shot e4_welcome_goal 4
open --ez tb.seed true --es tb.tr KJV --es tb.plate prodigal;      shot e5_plate 5
open --ez tb.lock true --es tb.price "'\$6.99'" --es tb.monthPrice "'\$1.99'" --es tb.memberPrice "'\$4.99'" --ez tb.purchase true; shot e6_purchase 4
# 영어 화면 한 바퀴 (en): 오늘 · 성경 · 읽기 · 기도 · 설정 · 기록 · 큰 글씨
open --ez tb.seed true --es tb.theme LIGHT --es tb.tr WEB --ei tb.page 0;  shot en01_today 5
swipe_up;                                                          shot en02_today_more 2
open --ei tb.page 1;                                               shot en03_library 4
swipe_up; swipe_up;                                                shot en04_library_books 2
open --es tb.listen "41:3";                                        shot en05_reader 4
open --es tb.prayer lords --es tb.prayerTimes "morning=420";       shot en06_prayer 4
open --ez tb.prayers true;                                         shot en07_prayers 4
open --ez tb.settings true;                                        shot en08_settings 4
open --ez tb.settings true --es tb.section set_listen;             shot en09_settings_listen 4
open --ei tb.page 3;                                               shot en10_record 5
open --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 2;         shot en11_aloud 4
open --ef tb.scale 1.15 --ei tb.page 0;                             shot en12_huge_today 5
open --ef tb.scale 1.15 --ez tb.lock true --es tb.price "'\$6.99'" --es tb.monthPrice "'\$1.99'" --ez tb.purchase true; shot en13_huge_purchase 4
open --ef tb.scale 1.0
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null
# 큰 글자 · 작은 화면
open --ez tb.seed true --es tb.tr KRV --ef tb.scale 1.15 --ei tb.page 2; shot x01_large_copy 5
open --ei tb.page 0;                                               shot x02_large_today 4
open --ef tb.scale 1.0
adb shell wm size 720x1280; adb shell wm density 320
open --ei tb.page 0;                                               shot x03_small_today 5
open --ez tb.reset true --ei tb.welcomeStep 2;                     shot x04_small_welcome 4
adb shell wm size reset; adb shell wm density reset

# 1.1 어두운 화면: 화첩 · 장 마침 · 판화
open --ez tb.seed true --es tb.tr KRV --es tb.theme LIGHT --ez tb.gallery true;      shot v01_gallery 5
open --es tb.finished 1:7;                                                            shot v02_finished 5
swipe_up;                                                                             shot v03_finished_more 2
open --es tb.finished 2:3;                                                            shot v04_finished_noplate 5
open --es tb.plate noah;                                                              shot v05_plate 4

# @scene hp
# 도움말: (?) · 기도 · 듣기 첫 안내 · 설정 도움 · 소개 다시 보기 · 교독 안내
open --ez tb.seed true --es tb.tr KRV --es tb.theme LIGHT --ei tb.page 0; shot hp1_topbar 5
open --ez tb.coach true --es tb.prayer morning;                    shot hp2_prayer_coach 6
adb shell input tap 540 1200; sleep 1; adb shell input tap 540 1200; sleep 1; adb shell input tap 540 1200; shot hp3_prayer_last 1
open --ez tb.coach true --es tb.listen "19:23" --ez tb.listenPlay true; shot hp4_listen_coach 8
open --ez tb.coach true --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 0; shot hp5_copy0_coach 7
open --ez tb.settings true --es tb.section set_help;               shot hp6_settings_help 4
swipe_up;                                                          shot hp7_settings_help2 2
open --ez tb.tour true;                                            shot hp8_tour 4
# @end
# @scene fs
# 폰 글자 크기를 키웠을 때 (시스템 글꼴 130% · 200%): 앱 안에서는 ‘크게’(115%) 까지만 커져야 해요
for fs in 1.3 2.0; do
  n=${fs/./}; adb shell settings put system font_scale $fs; sleep 2
  open --ez tb.seed true --es tb.tr KRV --es tb.theme LIGHT --ei tb.page 0; shot fs${n}_today 5
  open --ei tb.page 2 --ei tb.copyTab 1;                              shot fs${n}_type 4
  open --ez tb.lock true --es tb.price ₩9,900 --es tb.monthPrice ₩2,900 --es tb.memberPrice ₩6,900 --ez tb.purchase true; shot fs${n}_purchase 4
  open --es tb.prayerTimes morning=420 --es tb.prayer morning --es tb.prayerBell morning; shot fs${n}_bell 4
  open --ez tb.settings true --es tb.section set_alerts;             shot fs${n}_alerts 4
done
adb shell settings put system font_scale 1.0
# @end
# @scene st
# 스토어 그림 (Play): 상태 표시줄을 깔끔하게 (9:00 · 배터리 가득 · 알림 없음), 알림판은 접고 찍어요
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0900 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
stsnap() { sleep "$2"; adb shell cmd statusbar collapse >/dev/null 2>&1; sleep 1; snap "$1"; }
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null
open --ez tb.seed true --es tb.tr KRV --es tb.theme LIGHT --es tb.owner 은혜 --ei tb.page 0; stsnap st_ko_1_today 6
open --ei tb.page 2 --ei tb.copyTab 1;                             stsnap st_ko_2_type 4
open --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 0;         stsnap st_ko_3_aloud 6
open --es tb.listen "41:3" --ez tb.parallel true;                  stsnap st_ko_4_reader 5
open --es tb.prayer morning;                                       stsnap st_ko_5_prayer 4
open --es tb.plate noah;                                           stsnap st_ko_6_plate 5
open --ez tb.parallel false --ei tb.page 3;                        stsnap st_ko_7_record 5
adb shell cmd locale set-app-locales $P --locales en-US 2>/dev/null
open --ez tb.seed true --es tb.tr WEB --es tb.theme LIGHT --es tb.owner "''" --ei tb.page 0; stsnap st_en_1_today 6
open --ei tb.page 2 --ei tb.copyTab 1; sleep 3; adb shell input tap 540 2150; sleep 2; adb shell input text "He%swent%sup%sinto%sthe%smountain"; stsnap st_en_2_type 2
open --ei tb.page 2 --ei tb.copyTab 0 --ei tb.aloudMode 0;         stsnap st_en_3_aloud 6
open --es tb.listen "41:3" --ez tb.parallel true;                  stsnap st_en_4_reader 5
open --es tb.prayer lords;                                         stsnap st_en_5_prayer 4
open --es tb.plate prodigal;                                       stsnap st_en_6_plate 5
open --ez tb.parallel false --ei tb.page 3;                        stsnap st_en_7_record 5
adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null
# @end
# @scene fgs
# Play 포그라운드 서비스 신고용 영상 (영어): 듣기 시작 → 홈으로 나가도 재생 → 알림판의 재생 알림 → 앱으로 돌아오기
adb shell cmd locale set-app-locales $P --locales en-US 2>/dev/null
# 재생 알림이 보이게 알림 허락을 주고, 알림판은 접은 채로 시작
adb shell pm grant $P android.permission.POST_NOTIFICATIONS 2>/dev/null
open --ez tb.seed true --es tb.tr WEB --es tb.theme LIGHT --es tb.listen "19:23"; sleep 5
adb shell cmd statusbar collapse; sleep 1
adb shell screenrecord --time-limit 38 --size 720x1600 /sdcard/fgs.mp4 &
sleep 2
open --es tb.listen "19:23" --ez tb.listenPlay true; sleep 9
adb shell input keyevent 3; sleep 5
adb shell cmd statusbar expand-notifications; sleep 7
adb shell cmd statusbar collapse; sleep 1
adb shell am start -n $P/.MainActivity --ez listening true >/dev/null; sleep 6
wait; sleep 2
adb pull /sdcard/fgs.mp4 "$OUT/fgs_demo.mp4" >/dev/null 2>&1; ls -la "$OUT/fgs_demo.mp4"
adb shell cmd locale set-app-locales $P --locales ko-KR 2>/dev/null
# @end
adb logcat -d -s AndroidRuntime:E > "$OUT/logcat.txt" || true
echo "app ANR: $(adb logcat -d | grep -c "ANR in $P")" > "$OUT/anr.txt"; cat "$OUT/anr.txt"
ls -la "$OUT"
