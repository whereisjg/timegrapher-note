# 타임그래퍼 노트 – Android 프로젝트

- 패키지 ID: `app.tgnote.timegrapher` (첫 업로드 후 변경 불가)
- minSdk 24 (Android 7.0) / targetSdk 36 (Android 16)
- 인앱 상품: `pro_unlock` (일회성 상품, Play Billing Library 8.0.0)
- 앱 코드는 인터넷을 쓰지 않음 (결제 라이브러리만 구글 플레이와 통신). 직접 요청하는 권한은 마이크(RECORD_AUDIO)뿐

## 빌드 순서
1. Android Studio 최신 버전 설치 → **Open** → 이 폴더 선택 → Gradle 동기화가 끝날 때까지 대기
   - "Android Gradle Plugin을 업그레이드하라"는 안내가 나오면 그대로 진행해도 됩니다.
2. 폰을 USB로 연결(개발자 옵션 > USB 디버깅 켜기) → ▶ 실행으로 동작 확인
3. **Build > Generate Signed App Bundle or APK > Android App Bundle**
   - 처음이면 **Create new** 로 업로드 키(keystore)를 만듭니다.
   - **keystore 파일과 비밀번호는 절대 잃어버리면 안 됩니다.** 클라우드와 USB 두 곳에 백업하세요.
   - `release` 선택 → 완료되면 `app/release/app-release.aab` 생성
4. 새 버전을 올릴 때마다 `app/build.gradle.kts` 의 `versionCode` 를 1씩 올립니다.

## 화면 코드
`app/src/main/assets/index.html` 한 파일이 앱 화면 전체입니다(웹 버전 '타임그래퍼 노트'와 같은 코드).

## 결제 동작
- 무료: 개별 틱 측정, 시계 1개, 기록 최근 5개
- 프로(`pro_unlock`): 약한 신호 모드, 시계 무제한, 기록 무제한과 자세별 평균
- 결제 상태는 앱을 열 때마다 구글 플레이에 다시 확인합니다(환불 시 자동 해제).
- 결제 테스트는 앱이 테스트 트랙에 올라간 뒤, Play Console > 설정 > 라이선스 테스트에 등록한 계정으로 할 수 있습니다.
