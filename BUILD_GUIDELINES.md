# BUILD_GUIDELINES.md

## 목적
개발의도는 류재상의의 혈당, 혈압등의 바이탈을 통합해서 관리하기 위해서이다.
이 문서는 Easy Health의 개발·빌드·배포 전에 반드시 확인하는 최상위 기준 문서입니다. 빌드 전에 이 문서와 `build-logs/`의 과거 기록을 먼저 읽습니다.
모든 코딩과 빌드를 최피디가 담당한다. 삼촌인 Ryoo Jaesang는 최초의 앱아이콘 제작은 관여하는데 코딩과 빌드는 전혀 하지 않아서 파일을 가지고 있지 않다.

최초 사용자 등록은 - 이름, 성별, 나이, 키 등을 입력 받아 건강상태를 확인하게 한다.
각 탭에서의 화면 설명
기록탭
최근 측정한 바이탈 값을 박스형태로 좌에서 우로 열거한다.
측정기록 추가
측정값 자동으로 가져오기 - 등록한 기기의 측정값을 가저온다.
데이터 보내기 - 블루투스로 연결된 다른 스마트 기기로 측정값을 내보낸다.
데이터 가져오기 - 블루투스로 연결된 다른 스마트 기기에서 측정값을 가져온다.
측정값들의 나열
각종 바이탈 측정값들을 최근 측정값 순으로 나열한다.

리포트 탭
등록한 바이탈기기 탭이 나열
각 탭을 누르면 1주일 1개월 3개월 전체 탭이 있고
각기간을 누르면 기간에 해당하는 리포트를 보여준다.

일정 일기탭
달력을 보여준다
널쩌룰 길게 누르면 날짜에 해당하는 병원 예약정보와 건강일지 간단메모등을 입력하게 한다.
입력했던 값이 있으면 입력란 밑으로 박스형태의 입력값을 시간순으로 보여주며
박스를 누르면 수정을 할수 있게 한다.

기기 설정탭
등록한 기기들을 나열한다. 최초에는 등록한 기기가 없으므로 등록하라는 알림글
기기추가버튼을 누르면 블루투스 기기를 추가할 수잇다.


## 1. 버전 번호 일치
- 앱에 표시되는 버전, 실행 파일의 버전, APK/AAB 등 배포 파일의 버전, 배포 ZIP 파일명의 버전을 동일하게 유지합니다.
- 예: 앱 1.38 / `Easy-Health-1.38.apk` / `Easy-Health-1.38.zip`
- Android의 versionName과 versionCode를 함께 확인합니다. versionName은 표시 버전과 일치해야 하며 versionCode는 기존보다 증가해야 합니다.
- 버전·파일명·패키지 메타데이터가 일치하지 않으면 배포하지 않습니다.

## 2. 앱 아이콘 통일
- 같은 대표 앱 아이콘 디자인을 메인 화면/창, 제목 표시줄(제목 표시줄이 있는 플랫폼), 앱 정보창, Windows 실행 파일 또는 Android APK의 아이콘에 일관되게 적용합니다.
- 플랫폼별 파일 형식과 해상도는 달라도 원본 디자인과 식별성은 유지합니다.
- 기존에 승인·제공된 아이콘이 있으면 임의로 새 디자인으로 교체하지 않습니다.

## 3. 앱 정보창 표시 순서
1. 첫째 줄: 앱 아이콘 + 앱 이름 + 버전
2. 둘째 줄: 개발 날짜
3. 셋째 줄: 사용자와 ChatGPT의 아이콘(build_standard 레포지토리의 js_logo.png) + 이름
- 공동 제작자 표기는 정확히 `Ryoo Jaesang & 최피디`로 표시합니다.
- 기존에 제공된 공동 아이콘이 있으면 그대로 사용합니다. 별도 지시 없이 새 아이콘을 만들거나 대체하지 않습니다.

## 4. 빌드 전 필수 확인
- 이 문서와 `build-logs/BUILD_HISTORY.md`, 해당 버전의 상세 로그를 읽습니다.
- 마지막 성공 빌드, 최근 실패 원인, 미해결 항목을 확인합니다.
- 브랜치, 커밋, 변경 파일, 앱 ID, 버전, SDK/Java/Gradle 환경을 확인합니다.
- 기존 설치본의 업데이트 호환성에 영향을 주는 applicationId, 서명키, 서명 인증서를 임의로 변경하지 않습니다.
- 기존 서명키를 복원·검증할 수 없으면 새 키로 대체하지 말고 빌드를 중단한 뒤 원인을 기록합니다.

## 5. 빌드 후 검증
- 빌드 성공 여부와 테스트 결과를 실제 실행 결과로 확인합니다.
- 앱 ID, versionCode, versionName, 서명 인증서 지문, 파일명, 파일 크기를 검증합니다.
- 설치·업데이트·실행 검증 여부를 구분해 기록합니다. 검증하지 않은 항목을 성공으로 표시하지 않습니다.
- APK 생성, 아티팩트 업로드, 기기 설치는 각각 별도 결과로 기록합니다.

## 6. 성공·실패 로그
- 모든 빌드 시도는 성공과 실패를 모두 기록합니다.
- 날짜, 버전, 브랜치, 커밋, 환경, 변경 파일, 실행 명령/워크플로, 테스트, 결과, 정확한 오류, 원인(확정/추정 구분), 해결 과정, 다음 조치를 남깁니다.
- 같은 오류를 반복하지 않도록 이전 실패 로그를 먼저 확인합니다.
- 로그는 `build-logs/`에 버전·날짜별로 누적하고 `BUILD_HISTORY.md`에 요약을 추가합니다.
- 토큰, 비밀번호, 개인키, keystore 원문 등 비밀정보는 절대 로그에 기록하지 않습니다.

## 7. 재개 절차
새 대화나 장기간 중단 후에는 “빌드 가이드와 빌드로그 숙지해”라는 요청을 기준으로 이 문서와 저장소 로그를 먼저 확인하고, 마지막 진행 상황과 미해결 문제부터 이어갑니다.


## Android signing-key backup rule

- GitHub Actions cache is temporary and must never be the only copy of an Android signing key.
- Keep the established keystore in a separately protected backup and provision CI from an encrypted GitHub Actions secret or equivalent secure secret store.
- Never generate a replacement key automatically when the established key is unavailable. Stop the build and preserve in-place update compatibility.
- Before every release build, verify the signing certificate SHA-256 against the established fingerprint.
