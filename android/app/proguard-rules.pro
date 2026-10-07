# 오류 기록 · Play Vitals 에서 줄 번호가 보이게
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 매니페스트가 이름으로 부르는 것 (액티비티 · 서비스 · 리시버 · 위젯) 은 AGP 가 자동으로 지켜요.
# 결제 라이브러리는 자체 consumer 규칙을 갖고 있어요.

# 캡처 · 시험용 intent extra 를 읽는 debug 경로는 release 에서 DEV_TOOLS=false 로 꺼져 있어요.
