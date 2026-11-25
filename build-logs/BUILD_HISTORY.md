# Easy Health Build History

| 날짜 | 버전 | 브랜치 | 결과 | 요약 |
|---|---|---|---|---|
| 2026-11-25 | 1.38 production | `feature/v1.38-vitals-device-registration` | 차단 | 기존 production 서명키 캐시를 복원하지 못해 안전장치에서 중단. 기존 1.37 앱의 업데이트 호환성을 위해 production signing guard 유지. 상세: [2026-11-25-v1.38-signing-key-cache-miss.md](2026-11-25-v1.38-signing-key-cache-miss.md) |
| 2026-11-25 | 1.38 TEST | `feature/v1.38-vitals-device-registration` | 성공 | 최신 날짜별 박스 UI 포함. 단위 테스트·APK·패키지/버전/서명 검증·아티팩트 업로드 완료. run 45576038105, artifact 15003943709. 상세: [2026-11-25-v1.38-feature-test-build.md](2026-11-25-v1.38-feature-test-build.md) |

| 2026-11-25 | 1.38 production check (temporary main validation) | `main` | 차단 후 원복 | 기존 production 서명키 캐시 미복원으로 run 45575777221이 안전장치에서 중단. APK 없음. `main`은 원래 1.37 commit `ebf9e2826083eca4a5d326d60799953a5f6a35fe`로 복원. 상세: [2026-11-25-v1.38-signing-key-cache-miss.md](2026-11-25-v1.38-signing-key-cache-miss.md) |\n\n## 재개 시 우선 확인
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


## 2026-11-25 — Latest date-grouped schedule/diary test build
- Source branch: `feature/v1.38-vitals-device-registration`
- Source commit: `7289104e308a5428b1c29e893bb21d68af36cedd`
- Workflow run: `45576038105` — success
- Unit tests: success; isolated test APK assembly: success; package/version verification: success; test signing verification: success; artifact upload: success.
- Test package ID: `com.ryoo.easyhealth.test`; versionCode `38`; versionName `1.38`.
- Artifact: `Easy-Health-1.38-Test`, ID `15003943709`, 16,186,433 bytes.
- Artifact SHA-256: `48c8867f26cc936871ddc96c65b631c12f6170073e74ac693c95133b951afdcc`.
- Test certificate SHA-256: `90184a6a49b25f3b0e638fc0b54cdfc4cdc4dd17ebbfca034ed23ebdd3157e58`.
- Download: https://github.com/ryoobach/Easy-Health/actions/runs/45576038105/artifacts/15003943709
