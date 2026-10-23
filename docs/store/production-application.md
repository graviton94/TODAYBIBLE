# 프로덕션 신청서 답 (Play Console › 테스트 › 프로덕션 액세스 신청)

영어로 붙여 넣기를 권해요 (검토자가 영어권일 수 있음). 한국어로 써도 받아 줘요.
**[ ]** 칸은 실제 숫자 · 사실로 바꿔 주세요. 검토자는 Console 의 테스터 수 · 설치 기록과 맞춰 봐요.
테스터에게 실제로 들은 말이 아닌 것은 "our own review" 쪽으로 옮겨 쓰세요.

---

## 1. 비공개 테스트 정보 (About your closed test)

### 테스터 모집이 얼마나 쉬웠나요? (How easy was it to recruit testers?)
고르기: **[보통 / 약간 어려움]** — 교회 · 지인 중심이면 "Neither easy nor difficult" 가 자연스러움.

### 테스트 중 테스터 참여 (Describe the engagement you received from testers)

> We ran the closed test for [14+] days with [N] testers, mostly church members and friends from Korea and a few English speakers, ages [20s–70s], including several older readers. Before the Play test, [N] people used a sideloaded build for a week with a written test sheet (about 30 minutes of tasks) and sent their results back from the app. During the Play test, testers used the daily features: reading a chapter aloud, typing and handwriting it, listening to narration, prayers and widgets. We shipped [N] test updates (1.2 to 1.7) and testers kept the app installed and updated through them.

### 받은 의견 요약 · 받은 방법 (Summarize the feedback and how you collected it)

> Feedback came through the in-app "Send feedback" (Settings › Help, which can attach the app's own error log on the tester's choice), the written test sheet, and messages and conversations with testers.
> Main points: older readers wanted larger text, stronger contrast and fewer buttons; some were unsure whether the microphone recording leaves the phone; typing rejected letters without saying why; a sermon note was lost when the phone went to sleep; narration downloads used mobile data; people using both a phone and a tablet wanted one combined record; reinstalling from Play over the test APK lost records; and testers asked for prayers with narration and a way to pick between reading aloud, typing and handwriting.

---

## 2. 앱 정보 (About your app)

### 대상 사용자 (Who is the intended audience?)

> Christians who want a daily Bible habit, especially those who practice copying Scripture by hand (a widespread devotional practice in Korean churches), and older readers who need large text and a simple screen. The app is in Korean and English, rated for everyone, and is not designed for children.

### 사용자에게 주는 가치 (How does your app provide value to users?)

> Bible by Hand turns one chapter a day into something you actually do: read it aloud (speech recognition follows along), type it letter by letter into manuscript squares, or write it by hand over a faint guide. Each copy keeps its own progress, and finished books become a printable PDF, an audiobook in your own voice and a certificate of completion. Around the text are Doré and Schnorr engravings with their history, related verses, a Bible dictionary, narrated prayers, church-year devotions and widgets. Large text and Simple mode suit older readers. There is no account, no ads and no analytics; records stay on the phone, and backups go only where the user chooses.

### 첫해 예상 설치 수 (Expected installs in the first year)
고르기: **[1만 미만 (0–10K)]** 가 현실적 (교회 소개 · 입소문 기준). 광고를 할 계획이면 한 단계 위.

---

## 3. 프로덕션 준비 (Production readiness)

### 테스트로 배운 것을 바탕으로 바꾼 것 (What changes did you make based on the closed test?)

> - Accessibility: very large text, high contrast, a short guide on every screen, and Simple mode (today's chapter and three big buttons).
> - Microphone: the wording now says exactly what happens. The phone's own speech recognition service processes speech, and the app sends no audio anywhere. The same wording is in the privacy policy.
> - Typing explains a rejected letter; sermon notes and reflections save automatically.
> - Narration downloads only on Wi-Fi (checked during the download), retry once, and say when the phone voice stands in.
> - Backup and restore now merge records, so a phone and a tablet can share one record and a reinstall loses nothing.
> - Three separate copies (aloud, typed, handwritten) with their own progress, and new prayers with narration.
> - Stability: we removed main-thread work found by a watchdog during automated runs, and fixed widgets redrawing on every verse.

### 프로덕션 준비가 됐다고 판단한 근거 (How did you decide the app is ready?)

> - Testers have used every main flow (aloud, typed, handwritten, listening, prayers, backup and restore, widgets, purchases with test accounts) and the issues they raised are fixed.
> - Every build runs unit tests and automated emulator runs: screenshots of each screen in Korean and English, a release-build smoke test with random input, and a watchdog for stalls. The latest release build had no crashes or ANRs.
> - [Play Console › Android vitals shows no crash / ANR clusters for the test track.]
> - Store listing, data safety (no data collected or shared), privacy policy, content rating and foreground service declarations are complete and match the app.
> - We will roll out in stages (20% → 50% → 100%), watch Android vitals daily in the first week, and keep the in-app feedback channel open.

---

## 제출 전 확인
- [ ] 테스터 12명 이상이 **14일 연속** 옵트인 상태였는지 (Console › 비공개 테스트 › 테스터 수)
- [ ] 위 [ ] 칸 채우기 · 실제로 듣지 않은 의견 빼기
- [ ] 포그라운드 서비스 신고 둘 (미디어 재생 · 데이터 동기화) · 데이터 보안 · 콘텐츠 등급 완료
- [ ] 스토어 그림 (`docs/store/graphics/phone_*`) · 홍보 동영상 링크 (`docs/store/promo/`) 올림
