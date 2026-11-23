# 타임그래퍼 노트 (Timegrapher Note)

휴대폰 마이크로 기계식 시계의 틱 소리를 듣고 하루 오차(s/d), 진폭(°), 비트 에러(ms)를 계산하는 안드로이드 앱.
개발자는 비전문가(Play Console·Android Studio 처음). 설명은 한국어로, 단계별로, 용어에는 짧은 설명을 붙일 것.

## 구조
- 이 폴더 = Android Studio 프로젝트 (Java, AndroidX 없음, 단일 Activity + WebView)
- `app/src/main/assets/index.html` 한 파일이 앱 화면과 분석 엔진 전체 (HTML/CSS/JS)
  - `TG` 객체: 분석 엔진 (`analyze` 개별 틱, `analyzeFold` 약한 신호 폴딩, `measure` 자동 선택, `synth` 가상 신호)
  - 화면 로직: 시계 목록, 측정, 기록, 프로 잠금
- `MainActivity.java`: WebView, `https://appassets.androidplatform.net/` 로 assets 제공, 마이크 권한, 파일 선택, edge-to-edge 여백
- `NativeRec.java`: AudioRecord로 48 kHz PCM 원음 수집 → 100 ms마다 base64로 `window.__recData()` 호출 (JS 이름 `AndroidRec`)
  - 소스 우선순위: UNPROCESSED(9) → VOICE_RECOGNITION(6) → MIC(1). 입력 장치는 내장 마이크로 고정(`setPreferredDevice`) — 갤럭시 버즈 연결 시 이어폰 마이크로 녹음돼 틱을 못 찾던 문제(2026-11-24)
  - 웹 오디오(getUserMedia) 경로는 입력/출력 클럭 불일치로 샘플이 늘거나 줄어 오차가 수천 s/d로 튀었음 → 네이티브 녹음으로 해결
- `Billing.java`: Play Billing Library 8.0.0, 일회성 상품 `pro_unlock` (JS 이름 `AndroidBilling`: `isPro()`, `price()`, `refresh()`, `buy()` → 결과는 `window.__billing({pro, price, msg, err})`)

## 빌드 정보
- 패키지 ID `app.tgnote.timegrapher` (Play 업로드 후 변경 불가)
- minSdk 24 / targetSdk 36 / compileSdk 36, AGP 8.10.1, Gradle 8.11.1, Java 17
- 업로드 키: `C:\keys\timegrapher-upload.jks`, alias `upload` (백업 완료. 절대 저장소에 넣지 말 것)
- 릴리스: Build > Generate Signed App Bundle → `app/release/app-release.aab`
- 새 버전마다 `app/build.gradle.kts` 의 `versionCode` +1

## 무료 / 프로
| 기능 | 무료 | 프로 (`pro_unlock`, 제안가 ₩3,900) |
|---|---|---|
| 실시간 측정(개별 틱), 파일 분석, 오차·진폭·비트 에러·트레이스, 보정값 | O | O |
| 약한 신호 모드(폴딩) | X | O |
| 시계 등록 | 1개 | 무제한 |
| 측정 기록 | 최근 5개 표시(저장은 전부, 업그레이드하면 모두 보임) | 무제한 + 자세별 평균 |
- `AndroidBilling` 이 없는 환경(웹 미리보기)과 Android Studio ▶ 실행(디버그 빌드)에서는 모든 기능 열림. 서명된 릴리스 AAB는 영향 없음. 결제 시험은 Play 테스트 트랙으로 받은 앱에서
- 결제 상태는 SharedPreferences에 캐시, 앱 resume마다 Play에 재확인 (환불 시 해제)

## 분석 엔진 요점
- 개별 틱: 2 kHz 하이패스 2회 → 피크 추종 포락선 → 임계 교차로 틱 검출 → 박자 번호 할당(±0.2T 벗어나면 버림, 4번 연속 미스면 재기준) → `refine`: 짝·홀 틱별 평균 틱 모양에 맞춰 각 틱 시각 재조정(±4 ms, 첫 기준은 잔차가 가장 몰린 0.5 ms 묶음) 후 평균 틱의 30% 상승점으로 통일 — 한 틱 안의 여러 소리 중 임계 교차가 앞소리·뒷소리를 오가 트레이스가 두 줄로 갈라지고 오차가 2 s/d 넘게 치우치던 문제(2026-11-24 카키 필드 H-50 실측) → `t = a + P·n + c·odd` 강건 회귀 → 오차 = (T/P − 1)·86400, 비트 에러 = |c|
- 폴딩: 4 kHz 에너지 포락선 → ±400 s/d 그리드 탐색 → 3초 구간별 leave-one-out 템플릿 상호상관 3회 반복. SNR < 6 이면 '불안정'
- 진폭: 평균 틱 파형에서 가장 큰 소리(드롭)와 그 앞 15 ms 안의 언락 반높이 시점 사이 시간 Δt → A = LA / (2·sin(π·Δt / 2T))
- 가상 신호 테스트: 오차 ±0.1 s/d, 진폭 ±2% 이내. **실제 시계·여러 폰 기종 검증은 아직 부족**
- 휴대폰 ADC 클럭 오차(하루 1~3초 가능)는 사용자 '보정값'으로 처리

## 출시 진행 상황 (2026-11-24 기준)
- [x] Play Console 개인 개발자 계정 생성 (개발자 이름 LEESS9), 판매자 상태 선언
- [ ] 본인·주소 인증 → 구글 확인 중. 결과는 whereisjg@icloud.com 으로 옴
- [ ] 전화번호 인증, Android 기기 인증(Play Console 모바일 앱)
- [x] 서명된 AAB 빌드 성공
- [x] 개인정보처리방침 공개 페이지: https://claude.ai/artifact/JaLhzRRS3HtuCX1e4V5The
- [x] 스토어 이미지(아이콘 512, 그래픽 1024×500, 스크린샷 4장)와 소개문 준비
- [ ] 앱 만들기 → 스토어 등록정보·앱 콘텐츠 설문 입력
- [ ] 인앱 상품 `pro_unlock` 생성 (AAB 업로드 후 가능)
- [ ] 비공개 테스트: 테스터 12명 이상 × 14일 연속 (목표 15~20명, 구글 그룹 사용). 개인 계정은 앱마다 필요
- [ ] 라이선스 테스트 계정으로 결제 시험
- [ ] 프로덕션 출시 신청
- [ ] 사업자등록·통신판매업 신고 여부 확인(세무서/시청)

## 다음에 손볼 것 (후보)
- 실제 폰·시계 실측으로 검출 임계값과 진폭 검출 조정
- 스크린샷을 실제 측정 화면으로 교체
- 장기 측정 기록(며칠간 실측 누적 → 하루 평균) 기능
- 영어 지원(strings, 스토어 문구)
