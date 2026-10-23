# 프로덕션 신청서 답 (Play Console › 테스트 › 프로덕션 액세스 신청)

영어로 붙여 넣기를 권해요 (검토자가 영어권일 수 있음). 한국어로 써도 받아 줘요.
**[ ]** 칸은 실제 숫자 · 사실로 바꿔 주세요. 검토자는 Console 의 테스터 수 · 설치 기록과 맞춰 봐요.
테스터에게 실제로 들은 말이 아닌 것은 "our own review" 쪽으로 옮겨 쓰세요.

---

## 1. 비공개 테스트 정보 (About your closed test)

### 테스터 모집이 얼마나 쉬웠나요? (How easy was it to recruit testers?)
고르기: **쉬움 (Easy)** — 교회 · 지인 중심으로 약 30명 (필요한 12명의 두 배 넘게).

### 테스트 중 테스터 참여 (Describe the engagement you received from testers) — 300자 이하 (261)

> About 30 testers, mostly church members of all ages (several older readers), used the app for about three weeks. They read chapters aloud, typed and handwrote them, listened to narration and prayed, and stayed installed through each test update from 1.2 to 1.7.

### 받은 의견 요약 (Summary of the feedback) — 300자 이하 (276)

> Older readers wanted larger text and fewer buttons. Some asked if mic audio leaves the phone. Typing gave no reason for rejected letters, a sermon note was lost, narration used mobile data, and phone and tablet users wanted one record. Testers also asked for narrated prayers.

### 의견을 받은 방법 (How you collected feedback) — 300자 이하 (208)

> Through the in-app Send feedback (Settings > Help, with an optional error log), a written test sheet that testers filled in and sent from the app, and direct messages and conversations with testers at church.

---

## 2. 앱 정보 (About your app)

### 대상 사용자 (Who is the intended audience?)

> Christians who want a daily Bible habit, especially those who practice copying Scripture by hand (a widespread devotional practice in Korean churches), and older readers who need large text and a simple screen. The app is in Korean and English, rated for everyone, and is not designed for children.

### 사용자에게 주는 가치 (How does your app provide value to users?) — 300자 이하 (278)

> A daily Bible habit you do, not just read: one chapter a day read aloud, typed or handwritten, with separate progress. Finished books become a PDF, your-voice audiobook and certificate. Doré engravings, related verses, narrated prayers, large text. No account, ads or analytics.

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
