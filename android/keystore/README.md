# 서명 키

- `sideload.jks`: 내 폰에 직접 설치하는 시험용 APK 의 고정 키 (비밀번호 `android`). 비밀이 아닌 시험 키라 저장소에 둠.
  늘 같은 키로 서명되므로 새 APK 를 받아 그대로 덮어 설치할 수 있어요.
- Play 업로드 키는 이것과 다른 키로, GitHub Secrets 에만 둬요 (출시 준비 때 만듦).
- Play 올리기 (출시 AAB): `.github/workflows/android-release.yml` 이 Secrets 의 `PLAY_UPLOAD_KEYSTORE_B64` (keystore 를 base64 로),
  `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`, `PLAY_KEY_PASSWORD` 로 서명해요. 키 파일 · 비밀번호는 저장소 · 대화에 두지 않아요.
  Play App Signing 을 쓰므로 이 키를 잃어도 Play Console 에서 올리기 키를 바꿀 수 있어요.
