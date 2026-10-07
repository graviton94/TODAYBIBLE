# Play Console 등록 준비 (비공개 테스트)

상품 · 가격(구독 `monthly` · `lifetime` · `lifetime_member`)은 따로 정해서 넣어요. 그 밖의 칸은 아래대로 채우면 돼요.

## 0. 순서

1. 앱 만들기 → 2. 앱 콘텐츠 (아래 3~10) → 3. 스토어 등록정보 (11) → 4. 비공개 테스트 트랙 · 테스터 (12) → 5. AAB 올리기 (13) → 검토 제출

## 1. 앱 만들기

| 칸 | 값 |
|---|---|
| 앱 이름 | Bible by Hand (등록정보: Bible by Hand: Read & Copy) |
| 기본 언어 | 영어(미국) – en-US, 한국어 번역 추가 |
| 앱 또는 게임 | 앱 |
| 유료 또는 무료 | 무료 (앱 안에서 구독 · 평생권) |
| 패키지 이름 | `io.github.graviton94.todaybible` (AAB 를 올리면 정해져요) |

## 2. 기본 정보

| 칸 | 값 |
|---|---|
| 카테고리 | 도서 및 참고자료 |
| 태그 | 성경, 책, 참고자료 (Play 가 고르게 하는 목록에서 비슷한 것) |
| 이메일 | ruahn49@gmail.com |
| 웹사이트 | https://graviton94.github.io/todaybible/ |
| 개인정보처리방침 | https://graviton94.github.io/todaybible/privacy/ |

## 3. 앱 액세스 권한

- **모든 기능을 특별한 액세스 권한 없이 사용할 수 있음** (로그인 · 계정 없음)
- 평생권 기능은 결제로 열리지만 로그인은 필요 없어요. 검토자 안내가 필요하면: "Free books (Genesis, Psalms, Proverbs, Mark, John) are fully usable without purchase. Other books open with the monthly subscription or Lifetime via Google Play Billing."

## 4. 광고

- **아니요, 광고가 없습니다**

## 5. 콘텐츠 등급 (IARC 설문)

- 카테고리: **참고 자료, 뉴스 또는 교육**
- 폭력 · 성적 콘텐츠 · 욕설 · 약물 · 도박 묻는 항목: 모두 **아니요**
- 사용자 간 소통 · 콘텐츠 공유 (앱 안에서 다른 사용자와 대화 · 게시): **아니요** (내보내기는 사용자가 고른 다른 앱으로 넘기는 것)
- 위치 공유: **아니요** · 디지털 상품 구매: **예** (구독 · 평생권)
- 예상 등급: 전체이용가 (Everyone · PEGI 3)

## 6. 타겟층 및 콘텐츠

- 타겟 연령: **13~15세 · 16~17세 · 18세 이상**. 13세 미만은 고르지 않아요 → 가족 정책 대상이 아님
- 어린이에게 의도치 않게 매력적인가: **아니요**

## 7. 뉴스 앱 · 코로나19 · 정부 · 금융 · 건강

- 모두 **아니요 / 해당 없음**

## 8. 데이터 보안 (Data safety)

앱에 계정 · 광고 · 분석 도구가 없고, 기록 · 손글씨 · 녹음은 폰 안에만 있어요.

| 질문 | 답 |
|---|---|
| 필수 사용자 데이터 유형을 수집하거나 공유하나요? | **아니요** |
| (위가 '아니요'면 나머지 칸은 없음) | |

판단 근거 (검토에서 물으면):
- 낭독 음원 받기: 장 파일만 받아요. 사용자 식별 정보를 보내지 않아요.
- 마이크: 폰의 음성 인식에 맡기고 기기 안 처리를 요청해요. 녹음 파일은 폰 안에만 저장돼요.
- 결제: Google Play 결제가 처리해요 (개발자가 결제 정보를 받지 않음).
- 백업 · 내보내기 · 의견 보내기: 사용자가 고른 앱(메일 · 드라이브)이 보내요. 앱이 서버로 보내는 것이 아니에요.
- 오류 기록: 폰에 남고, 사용자가 '의견 보내기'에서 직접 메일에 붙일 때만 나가요.

보수적으로 적고 싶다면: '앱 정보 및 성능 › 비정상 종료 로그'를 **수집 · 선택 사항 · 앱 기능 · 전송 중 암호화(메일)** 로 적을 수 있어요. 지금 방식에서는 '수집 안 함'이 맞다고 봐요.

## 9. 권한 신고

### 포그라운드 서비스 (필수)

`FOREGROUND_SERVICE_MEDIA_PLAYBACK` 을 써서 Play Console › 앱 콘텐츠 › **포그라운드 서비스 권한** 신고가 필요해요.

- 유형: **미디어 재생**
- 설명 (한국어): "사용자가 '듣기'를 누르면 성경 낭독을 이어서 재생합니다. 화면을 끄거나 다른 앱으로 가도 낭독이 이어지고, 알림에서 멈출 수 있습니다."
- 설명 (영어): "When the user taps Listen, the app plays Bible narration continuously. Playback continues with the screen off or in other apps, and can be stopped from the notification."
- 동영상 링크: `graphics/fgs_demo.mp4` (38초, 영어 화면 · 소리 없음: 시편 23편 듣기 시작 → 홈으로 나가도 재생 → 알림판의 "Listening · Psalms 23" → 앱으로 돌아오면 계속 재생 중). YouTube 에 **일부 공개** 로 올리거나 Google 드라이브 공유 링크로 붙여요.

### 마이크 (`RECORD_AUDIO`)

별도 신고서는 없어요. 앱 안에서 쓰기 전에 이유를 먼저 보여 주고 허락을 받아요. 개인정보처리방침에 적혀 있어요.

### 알림 · 부팅 후 다시 맞추기

`POST_NOTIFICATIONS` · `RECEIVE_BOOT_COMPLETED`: 신고 없음 (기도 · 읽기 알림).

## 10. 광고 ID

- 광고 ID 를 쓰지 않음 → **아니요**

## 11. 스토어 등록정보

- 글: [listing.md](listing.md). 기본은 영어(미국), 한국어는 '번역 관리 › 번역 추가 › 한국어 ko-KR'.
- 앱 아이콘 512×512: `graphics/icon_512.png`
- 그래픽 이미지 1024×500: `graphics/feature_ko.png` · `graphics/feature_en.png`
- 휴대전화 스크린샷 (9:16, 1080×1920): `graphics/phone_ko_1~7.png` · `graphics/phone_en_1~7.png`
- 태블릿 스크린샷은 비공개 테스트에서는 없어도 돼요.

## 12. 비공개 테스트

- 트랙: 테스트 › **비공개 테스트** › 트랙 만들기 (예: "교인 시험")
- 테스터: 이메일 목록 또는 Google 그룹. 2023년 11월 이후 만든 개인 개발자 계정이면 **12명 이상 · 14일 이상** 비공개 테스트를 해야 프로덕션 신청이 열려요.
- 국가: 대한민국 · 미국 · 캐나다 · 호주 · 뉴질랜드 · 아일랜드 등. **영국 제외** (KJV 왕실 특허).
- 의견 보낼 곳: ruahn49@gmail.com

## 13. 버전 올리기

- AAB: GitHub Actions › **Android release (Play)** › 실행 (버전 이름 예: 0.9.0) → 그 실행의 Artifacts 에서 `app-release-0.9.0` 받기 (14일 보관)
- 버전 코드: 1000 + 실행 번호 (시험 APK 와 겹치지 않음)
- 출시 노트 (한국어):

```
<ko-KR>
첫 비공개 테스트예요.
· 하루 한 장: 낭독 · 타자 · 손글씨로 옮겨 쓰기
· 교독: 인도 목소리와 한 절씩 주고받기
· 아침 · 낮 · 저녁 · 밤 기도문과 기도마다 알림
· 개역한글 · World English Bible · KJV, 한영 대조
의견은 ruahn49@gmail.com 으로 보내 주세요.
</ko-KR>
<en-US>
First closed test.
· One chapter a day: read aloud, type, or write by hand
· Responsive reading with a narrator
· Morning, midday, evening and night prayers with reminders
· World English Bible, KJV and Korean side by side
Send feedback to ruahn49@gmail.com.
</en-US>
```

## 14. 직접 하실 것 (상품 · 가격)

- 수익 창출 › 제품 › 인앱 상품: `lifetime` · `lifetime_member` (한 번 결제)
- 수익 창출 › 제품 › 구독: `monthly` (기본 요금제 매월)
- 결제 프로필 · 세금 정보 (판매자 계정)
