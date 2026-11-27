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
| 2026-11-26 | 1.43 TEST | `feature/v1.41-health-schedule-diary` | 성공 | CI run 45719784375 성공. 단위 테스트, APK 빌드, 패키지/버전 메타데이터, 테스트 서명 검증 및 artifact 업로드 완료. artifact 15071522480. 물리 기기 설치·화면 동작은 미검증. 상세: [2026-11-26-v1.43-test-build.md](2026-11-26-v1.43-test-build.md) |
| 2026-11-26 | 1.44 TEST | `feature/v1.41-health-schedule-diary` | 빌드 시작 대기 | versionName 1.44 / versionCode 44. 테스트 package `com.ryoo.easyhealth.test143` 유지로 1.43 TEST 앱 업데이트 호환성 확보. 시작 화면 2초 표시, 월별/분기별 A1c, 일정·일기 탭 편의성, 기기 목록 상단 간격 수정. |

| 2026-11-26 | 1.44 TEST | `feature/v1.41-health-schedule-diary` | 실패 | CI run 45747595098: `MainActivity.kt` 전체 기간 당화혈색소 3개월 구간 계산부에서 Kotlin nullable `Long?` 타입 오류로 `:app:compileDebugKotlin` 실패. 단위 테스트 컴파일 단계에서 중단, APK 미생성. 버전 1.44 소비 처리. 상세: [2026-11-26-v1.44-test-build.md](2026-11-26-v1.44-test-build.md) |
| 2026-11-26 | 1.45 TEST | `feature/v1.41-health-schedule-diary` | 성공·설치 확인 | 사용자가 1.45 빌드 완료 및 휴대폰 설치, 기존 데이터 이전 성공을 확인. versionName 1.45 / versionCode 45. 테스트 package `com.ryoo.easyhealth.test143` 유지. CI run/artifact 식별자는 후속 기록에서 확인 가능한 경우에만 추가. |


| 2026-11-26 | 1.46 TEST | `feature/v1.41-health-schedule-diary` | 성공 | CI run 45910097653 성공. 단위 테스트, APK 생성, package/version 검증, 기존 테스트 서명키 지문 검증, artifact 업로드 완료. Artifact `Easy-Health-1.46-Test` (15158714829). Package `com.ryoo.easyhealth.test143`, versionCode 46. 실기기 설치·화면 동작은 별도 미검증. 상세: `2026-11-26-v1.46-test-build.md`. |


| 2026-11-27 | 1.47 TEST | `build/v1.47-feedback-implementation` → `feature/v1.41-health-schedule-diary` | CI 성공 | CI run 45932784839 성공. 단위 테스트·APK 생성·package/version 검증·기존 테스트 서명 검증·artifact 업로드 모두 통과. Artifact `Easy-Health-1.47-Test` (15169187095, 17,659,596 bytes). Package `com.ryoo.easyhealth.test143`, versionName 1.47 / versionCode 47. 실기기 설치·화면 동작·데이터 보존은 사용자 확인 대기. 상세: `2026-11-27-v1.47-test-build.md`. |
| 2026-11-27 | 1.48 TEST | `feature/v1.41-health-schedule-diary` | 빌드·테스트 성공, APK 업로드 누락 | CI run 45953948703: 단위 테스트, APK 빌드, 패키지/버전 및 테스트 서명 검증 성공. 단, 업로드 단계가 `Easy-Health-1.47-Test/*`를 찾도록 잘못 설정되어 실제 생성 폴더 `Easy-Health-1.48-Test/`와 불일치, “No files were found” 경고로 다운로드 아티팩트 미생성. 상세: [2026-11-27-v1.48-test-build.md](2026-11-27-v1.48-test-build.md). |

| 2026-11-27 | 1.49 TEST | `feature/v1.41-health-schedule-diary` | CI 성공·APK 아티팩트 확인 | run 45966169260 성공. 단위 테스트, APK 빌드, package/version (`com.ryoo.easyhealth.test143`, 1.49/49), 기존 테스트 서명 지문 검증, 아티팩트 업로드 및 다운로드 확인 완료. Artifact `Easy-Health-1.49-Test` (15183736009, 17,661,050 bytes). 설치 안내문 버전 표기 오타는 배포용 ZIP에서 1.49로 정정. 실기기 설치·화면 동작·데이터 보존은 사용자 확인 대기. 상세: [2026-11-27-v1.49-test-build.md](2026-11-27-v1.49-test-build.md). |


| 2026-11-27 | 1.50 TEST | `feature/v1.41-health-schedule-diary` | CI 성공 | FB-003 반영. CI run 45977770006 성공: 단위 테스트, APK 빌드, package/version 및 기존 테스트 서명 검증, 아티팩트 업로드 완료. versionName 1.50 / versionCode 50 / package `com.ryoo.easyhealth.test143`. 실기기 설치 검증은 사용자 확인 대기. 상세: `2026-11-27-v1.50-test-build.md`. |


| 2026-11-27 | 1.51 TEST | `feature/v1.41-health-schedule-diary` | 빌드 시작 대기 | 1.50 성공 이후 다음 버전. versionName 1.51 / versionCode 51 / package `com.ryoo.easyhealth.test143`. 기존 UI·기능 소스를 유지한 버전업 TEST 빌드이며, HbA1c 계산 엔진의 신규 기능 구현은 이번 버전에 포함되지 않음. 상세: `2026-11-27-v1.51-test-build.md`. |

| 2026-11-27 | 1.52 TEST | `feature/v1.41-health-schedule-diary` | 빌드 진행 | 1.50 기록 탭 시간 가중 HbA1c 계산 엔진을 리포트 탭의 공통 계산 기준으로 사용. 기준일은 마지막 혈당 입력일. 1개월은 7일 간격 4회, 3개월은 월 간격 3회, 전체는 90일 간격 N회로 최신순 표시. 각 기준일 이전 90일 혈당 데이터로 계산. 평균혈당·실측횟수·HbA1c를 기간별 표시. versionName 1.52 / versionCode 52. |


| 2026-11-27 | 1.54 TEST | `feature/v1.41-health-schedule-diary` | 성공 | CI run 45998604337 성공. 단위 테스트, APK 빌드, package/version 및 격리 테스트 서명 검증, artifact 업로드 완료. Artifact `Easy-Health-1.54-Test` (15197566422, 17,662,104 bytes). Package `com.ryoo.easyhealth.test143`, versionCode 54. 실기기 설치 여부는 별도 확인. |
| 2026-11-27 | 1.55 TEST | `feature/v1.41-health-schedule-diary` | **성공** | CI run 46005185116 성공. 단위 테스트, APK 빌드, package/version(1.55/55), 테스트 서명키 지문, artifact 업로드 모두 통과. Artifact `Easy-Health-1.55-Test` (15200952150, 17,671,661 bytes). Package `com.ryoo.easyhealth.test143`. 실기기 설치·화면 동작은 미검증. 상세: [2026-11-27-v1.55-test-build.md](2026-11-27-v1.55-test-build.md). |
