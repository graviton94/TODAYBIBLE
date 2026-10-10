# 1.2.0 에 모으는 것

1.1.0 (versionCode 1004) 다음 릴리즈. 쌓아 두었다가 한 번에 AAB 로.

## 들어간 것
- 앱 안 업데이트: 새 버전이 있으면 아래 한 줄 → 뒤에서 받기 → 다시 시작 (Play 설치본에서만)
- 새로 바뀐 것 화면: 업데이트 뒤 처음 한 번 · 설정 › 도움에서 다시 보기

- 새 앱 아이콘: 도레 「빛이 있으라」 판화 위 흰 선 라틴 십자 (정중앙) · 시작 화면 어둠 바탕 (`scripts/app_icon.py`)

## 낼 때 할 일
- `design/strings.json` 의 `news_body` (ko · en) 를 1.2 에 들어간 것으로 맞추기 (한 줄에 하나)
- `MainActivity.NEWS` 가 "1.2" 인지 확인
- Play 출시 노트 ko-KR · en-US 를 news_body 와 같게
- android-release 를 version 1.2.0 으로
