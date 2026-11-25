# Easy Health Build History

| 날짜 | 버전 | 브랜치 | 결과 | 요약 |
|---|---|---|---|---|
| 2026-11-25 | 1.38 production | `feature/v1.38-vitals-device-registration` | 차단 | 기존 production 서명키 캐시를 복원하지 못해 안전장치에서 중단. 기존 1.37 앱의 업데이트 호환성을 위해 production signing guard 유지. 상세: [2026-11-25-v1.38-signing-key-cache-miss.md](2026-11-25-v1.38-signing-key-cache-miss.md) |
| 2026-11-25 | 1.38 TEST | `feature/v1.38-vitals-device-registration` | 성공 | 별도 package ID `com.ryoo.easyhealth.test` 테스트 APK 생성·단위 테스트·패키지/버전/서명 검증·아티팩트 업로드 완료. run 45552681894, artifact 14994975137. 상세: [2026-11-25-v1.38-feature-test-build.md](2026-11-25-v1.38-feature-test-build.md) |

## 재개 시 우선 확인
- 1.38 source and UI changes are preserved on the feature branch.
- The production package `com.ryoo.easyhealth` and installed 1.37 app remain untouched.
- The separate test package `com.ryoo.easyhealth.test` is used for 1.38 validation; it does not share app data with 1.37.
- Do not replace the production app with a differently signed APK.

## 2026-11-25 — Signing-key recovery investigation
- Confirmed the original keystore was generated in Actions run 43773225589 and persisted only via the Actions cache.
- The cache was successfully restored through 2026-11-17 12:34 UTC, then was missing in the 2026-11-24 23:38 UTC build. The interval exceeds GitHub's default seven-day cache inactivity retention.
- No keystore file exists in the checked repository tree; available workflow artifacts are APK packages only.
- Production build remains blocked intentionally. Do not generate a new signing key or replace the user's installed 1.37 app. Recover the original private key from a separate backup before attempting a production update.
- Workflow ignores documentation-only pushes and pull requests to avoid repeated, pointless Android build attempts from log/guideline edits.

| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 성공 | 최종 CI run 45562672552 성공. 단위 테스트·APK 빌드·패키지/버전·테스트 서명 검증 및 artifact 업로드 완료. artifact 14998463498. 상세: [2026-11-25-v1.39-test-build.md](2026-11-25-v1.39-test-build.md) |
| 2026-11-25 | 1.40 TEST | `feature/v1.40-vitals-device-registration` | 기록 확인 필요 | versionName 1.40 / versionCode 40 / test package `com.ryoo.easyhealth.test140`. 상세: [2026-11-25-v1.40-test-build.md](2026-11-25-v1.40-test-build.md) |
| 2026-11-25 | 1.41 TEST | `feature/v1.41-health-schedule-diary` | 빌드 대기 | 일정·일기 초기 화면은 날짜별 박스(최신순), 날짜 선택 시 해당 날짜 입력 영역과 기록을 표시하며 기록은 시간 오름차순. 버전 1.41 / versionCode 41 / 테스트 package `com.ryoo.easyhealth.test141`. |

| 2026-11-26 | 1.42 TEST | `feature/v1.41-health-schedule-diary` | 실패 | CI run 45716862348: `app/build.gradle.kts` 23행의 문자열 보간 escape 오류로 Gradle Kotlin DSL 컴파일 실패. 단위 테스트 실행 단계에서 중단, APK 생성 전 실패. 버전 1.42 소비 처리. 상세 원인: run 45716862348. |
| 2026-11-26 | 1.43 TEST | `feature/v1.41-health-schedule-diary` | CI 시작 대기 | 1.42 실패 버전을 재사용하지 않고 versionName 1.43 / versionCode 43 / 테스트 package `com.ryoo.easyhealth.test143`으로 증가. Gradle 설정은 직전 정상 소스 기반으로 복구하고 문자열 보간 문법을 보존. 정식 앱과 데이터는 변경하지 않음. |
