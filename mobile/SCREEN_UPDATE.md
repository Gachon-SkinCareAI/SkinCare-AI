# 분석 결과 · 위험도/케어 · 기록 · 경과 상세 업데이트

2026-11-03 / React Native + Expo / Gachon-SkinCareAI/SkinCare-AI develop/mobile 구조 기준.

## 적용
이 ZIP은 **변경 파일만** 포함합니다. 기존 앱이 필요하며 ZIP만으로 새 프로젝트를 실행할 수 없습니다.
압축 안의 mobile 폴더 내용을 기존 SkinCare-AI/mobile에 병합하세요. mobile 폴더 전체를 교체하거나 삭제하지 마세요.
App.js, package.json, package-lock.json, 로그인/홈/카메라 화면은 기존 파일을 유지합니다. 신규 의존성은 없습니다.

1. 기존 저장소에서 `git status`로 미커밋 작업을 확인하고 먼저 보관하세요.
2. 기존 develop을 쓰는 경우 `git switch develop`, `git pull --rebase origin develop`을 실행하세요.
3. `git switch -c feat/mobile-analysis-records`로 이번 작업 브랜치를 만드세요.
4. ZIP의 mobile 안에 있는 파일들을 기존 mobile 안 같은 경로에 복사하세요. 새 폴더는 추가하고 변경 파일만 덮어쓰세요. 본인이 별도로 수정한 동일 파일은 먼저 비교하세요.
5. 저장소 루트에서 실행하세요.

```bash
cd mobile
npm ci
node --test tests/*.test.mjs
npx expo start
```

## GitHub 업로드
실행 확인 후 저장소 루트(SkinCare-AI)에서 아래 순서대로 실행하세요.

```bash
git status
git diff -- mobile
git add mobile/.gitignore mobile/SCREEN_UPDATE.md mobile/src/AppShell.js mobile/src/components/Common.js mobile/src/screens/RecordScreens.js mobile/src/screens/AnalysisScreens.js mobile/src/screens/ProgressScreen.js mobile/src/data/analysisDemo.js mobile/src/utils/records.js mobile/assets/results mobile/tests/records.test.mjs
git diff --cached --stat
git commit -m "feat(mobile): add analysis results and wound progress screens"
git push -u origin feat/mobile-analysis-records
```

GitHub에서 base: develop, compare: feat/mobile-analysis-records로 Pull Request를 열어 팀원과 확인 후 합치세요.
이 파일 전달 과정에서는 GitHub에 커밋하거나 푸시하지 않았습니다.
기존 node_modules 등이 이미 추적 중이면 .gitignore만으로 추적 해제되지는 않습니다.

## 확인할 화면 흐름
- 인증 없이 로그인 → 홈 최근 분석 → AI 분석 결과 → 위험도 보기 → 관리 가이드 저장 / 주변 의료기관 찾기.
- 기록 → 전체/진행 중/회복 완료 필터 → 상처 카드 상세.
- 경과 상세 → 기간 선택 → 처음/최근 사진 확대, 날짜별 촬영 기록 선택.
- 회복 완료로 표시 → 기록 목록 완료 수 반영 → 진행 중으로 변경.
- 신규 사진 업로드 → 분석 대기 기록 → AI 분석 결과 보기: 결과 없음 안내.
- 경과 사진 추가 → 해당 상처의 촬영 기록에 추가 (미분석 면적을 임의 생성하지 않음).

## 데이터/연동 범위
- src/data/analysisDemo.js: Figma 예시 분석 결과, 위험도, 케어 문구. 업로드한 실제 사진에는 예시 평가를 적용하지 않습니다.
- src/screens/ProgressScreen.js: 예시 타임라인과 사용자가 추가한 사진을 표시합니다.
- src/utils/records.js: 상태 필터, 마지막 촬영일 기준 기간 필터, 측정 면적 비교 함수.
- 기록, 회복 완료 상태, 가이드 저장은 메모리 상태이며 새로고침/앱 종료/로그아웃하면 초기화됩니다.
- 위험도 카드는 표시 전용이며 사용자가 위험도를 변경하는 버튼이 아닙니다.
- 회복 완료는 사용자 기록 관리 상태이며 의료 판정이 아닙니다.
- 병원 버튼은 기존 병원 탭으로 연결됩니다. 실제 지도/의료기관 조회는 아직 미구현입니다.
- 분석 요약의 분류/신뢰도/케어 문구는 시연 데이터입니다. 실제 서비스는 검토된 백엔드 응답과 문진 결과로 교체해야 합니다.
- Figma의 휴대폰 외곽/가상 상태바는 그리지 않고 실제 기기 시스템 영역을 사용합니다.
- Figma 참고: 31:15, 57:15, 7:1169, 7:1833, 7:2137.

## 검증
자동 테스트: 날짜/사진 입력 검증, 기록 상태 필터, 기간 필터, 미측정/0 면적 처리.
웹 브라우저: 결과/위험도 이동, 가이드 저장, 완료 상태 필터 반영, 사진 확대, 기간 선택, 업로드한 사진의 분석 대기 유지.
Expo web/iOS/Android 번들 확인. 휴대폰 실기기 실행과 실제 카메라 촬영은 별도 확인이 필요합니다.
