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
