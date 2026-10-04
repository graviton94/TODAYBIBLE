# 서명 키

- `sideload.jks`: 내 폰에 직접 설치하는 시험용 APK 의 고정 키 (비밀번호 `android`). 비밀이 아닌 시험 키라 저장소에 둠.
  늘 같은 키로 서명되므로 새 APK 를 받아 그대로 덮어 설치할 수 있어요.
- Play 업로드 키는 이것과 다른 키로, GitHub Secrets 에만 둬요 (출시 준비 때 만듦).
- Play 올리기 (출시 AAB): `upload.jks.gpg` — ‘Android signing key’ 워크플로가 GitHub 안에서 만들고 Secrets 의 `ANDROID_UPLOAD_PASSPHRASE`
  (16자 이상) 로 잠가 둔 키. `android-release.yml` 이 같은 암호로 풀어 서명해요 (별칭 upload). 풀린 키 · 암호는 저장소 · 대화에 남지 않아요.
  Play App Signing 을 쓰므로 이 키를 잃어도 Play Console 에서 올리기 키를 바꿀 수 있어요.
