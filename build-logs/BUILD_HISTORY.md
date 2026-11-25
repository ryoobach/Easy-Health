# Easy Health Build History

| 날짜 | 버전 | 브랜치 | 결과 | 요약 |
|---|---|---|---|---|
| 2026-11-24 | 1.38 | `feature/v1.38-vitals-device-registration` | 실패 | 기존 Android 서명키 캐시를 복원하지 못해 안전장치에서 빌드 중단. 상세: [2026-11-24-v1.38-signing-key-cache-miss.md](2026-11-24-v1.38-signing-key-cache-miss.md) |

## 재개 시 우선 확인
- 1.38 소스 변경은 feature 브랜치에 보존되어 있습니다.
- 기존 설치본(1.37)의 업데이트 호환성을 지키기 위해 기존 서명키 복구가 선행되어야 합니다.
- 서명키가 확인되기 전에는 새 키로 빌드하거나 기존 앱을 교체하지 않습니다.
