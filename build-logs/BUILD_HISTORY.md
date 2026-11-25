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
- Build remains blocked intentionally. Do not generate a new signing key or replace the user's installed 1.37 app. Recover the original private key from a separate backup before attempting 1.38.
- Workflow now ignores documentation-only pushes and pull requests to avoid repeated, pointless Android build attempts from log/guideline edits.

| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 진행 중 | 1.39/versionCode 39로 승격, 앱 정보창을 가이드 3행 구조로 수정하고 build_standard의 실제 공동 아이콘 `js_logo.png`를 CI에서 포함하도록 구성. 상세: [2026-11-25-v1.39-test-build.md](2026-11-25-v1.39-test-build.md) |

| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 진행 중 | 리포트 당화혈색소 추정 기간을 최근 3개월로 고정하고, 일정·일기에서 날짜 길게 누르기 입력 메뉴 및 시간순 통합 기록 박스를 구현. 상세: [2026-11-25-v1.39-test-build.md](2026-11-25-v1.39-test-build.md) |

| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 진행 중 | 날짜 길게 누르기 입력 메뉴의 세 버튼을 가로 한 줄로 배치하고 라벨을 '병원 예약', '건강일기', '간단메모'로 정리. |

| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 수정·빌드 대기 | 일정·일기 기록을 날짜별 그룹으로 통합하고 최신 날짜순(내림차순) 표시. 같은 날짜의 병원 예약·건강일기·간단메모는 시간순으로 나열하며 기존 수정 기능 유지. 직전 run 45561957926의 Kotlin 컴파일 실패(일정·일기 when 분기 닫는 중괄호 누락)도 함께 수정. source commit: 2aaf1146. |

| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 빌드 대기 | 리포트의 당화혈색소 추정치 카드에 "혈당측정을 많이 할수록 당화혈색소 추정치가 정확합니다." 안내 문구 추가. source commit: 761ef284. |

| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 수정·빌드 대기 | 당화혈색소 추정치 카드에 혈당 측정 횟수 안내 문구 추가. 이전 날짜 타임라인 빌드 실패 원인인 `combinedClickable` 실험 API 미옵트인도 `HospitalCalendar`에 명시적 `@OptIn`으로 수정. source commits: 761ef284, 78feeca4. |


| 2026-11-25 | 1.39 TEST | `feature/v1.39-vitals-device-registration` | 성공 | 최종 CI run 45562672552 성공. 단위 테스트·APK 빌드·패키지/버전·테스트 서명 검증 및 artifact 업로드 완료. artifact 14998463498. 상세: [2026-11-25-v1.39-test-build.md](2026-11-25-v1.39-test-build.md) |

| 2026-11-25 | 1.40 TEST | `feature/v1.40-vitals-device-registration` | 빌드 대기 | versionName 1.40 / versionCode 40 / test package `com.ryoo.easyhealth.test140`. 날짜 한 번 누르기로 입력 액션 표시, 세 버튼 라벨 공백 제거 및 글꼴 축소. 당화혈색소 8개 시간대·최근 30일 개인별 식사 패턴 보완은 설계 제안으로 기록하며, 검증 전 계산 로직에는 반영하지 않음. 상세: [2026-11-25-v1.40-test-build.md](2026-11-25-v1.40-test-build.md) |
