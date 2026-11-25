# Easy Health Build History

| 날짜 | 버전 | 브랜치 | 결과 | 요약 |
|---|---|---|---|---|
| 2026-11-25 | 1.38 | `feature/v1.38-vitals-device-registration` | 실패 | 기존 Android 서명키 캐시를 복원하지 못해 안전장치에서 빌드 중단. 마지막 접근 후 기본 7일 만료 정책에 걸렸을 가능성이 높음. 상세: [2026-11-25-v1.38-signing-key-cache-miss.md](2026-11-25-v1.38-signing-key-cache-miss.md) |

## 재개 시 우선 확인
- 1.38 소스 변경은 feature 브랜치에 보존되어 있습니다.
- 기존 설치본(1.37)의 업데이트 호환성을 지키기 위해 기존 서명키 복구가 선행되어야 합니다.
- 서명키가 확인되기 전에는 새 키로 빌드하거나 기존 앱을 교체하지 않습니다.


## 2026-11-25 — Signing-key recovery investigation

- Confirmed the original keystore was generated in Actions run 43773225589 and persisted only via the Actions cache.
- The cache was successfully restored through 2026-11-17 12:34 UTC, then was missing in the 2026-11-24 23:38 UTC build. The interval exceeds GitHub's default seven-day cache inactivity retention.
- No keystore file exists in the checked repository tree; available workflow artifacts are APK packages only.
- Build remains blocked intentionally. Do not generate a new signing key or replace the user's installed 1.37 app. Recover the original private key from a separate backup before attempting 1.38.
- Workflow now ignores documentation-only pushes and pull requests to avoid repeated, pointless Android build attempts from log/guideline edits.
