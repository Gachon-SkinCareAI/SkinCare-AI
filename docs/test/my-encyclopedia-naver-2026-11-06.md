# 마이페이지·백과·네이버 지도 변경 — 최종 테스트 대기

작성일: 2026-11-06

사용자 요청에 따라 이번 단계는 소스 수정과 공식 자료 조사까지 진행했다. 자동 테스트, 브라우저·실기기 테스트, APK 빌드, 서버 업데이트, QR 생성·게시는 실행하지 않았다. 설치된 버전 12와 공개 배포물은 이번 변경을 아직 포함하지 않는다. 아래 항목은 테스트 통과 보고가 아니다.

## 변경 파일과 동작

| 파일 | 변경 |
| --- | --- |
| `mobile/src/screens/AccountScreens.js` | 계정 카드 통합, 아이콘과 구분선을 사용한 설정·문의 메뉴, 하단 로그아웃, 눌렀을 때만 펼쳐지는 탈퇴·자료 삭제 확인 영역 |
| `mobile/src/data/encyclopedia.js` | 기존 7개 항목을 개요·증상/관리·진료 참고 항목으로 확장, 출처별 언어·확인일·확인된 수정일 기록, 본문까지 검색 |
| `mobile/src/screens/EncyclopediaScreen.js` | 짧은 단락과 항목별 카드, 원문 자료 영역, 한국어·영문 링크 구분, 줄바꿈 가능한 분류 필터 |
| `mobile/src/services/maps.js` | 공개 기관명·주소를 URL 인코딩해 네이버 지도 검색 주소 생성 |
| `mobile/src/screens/MedicalScreen.js` | 카카오 지도 링크를 네이버 지도로 교체, 주소 일치 확인 안내 |
| `mobile/tests/guest-flow.browser.cjs` | 바뀐 메뉴·출처 링크 문구에 맞춰 기존 테스트 선택자만 수정. 실행하지 않음 |

가입 동의, 위치 권한, 동의 철회, 문의 작성·초안·답변, 로그아웃, 삭제 확인 입력과 실제 API 호출은 기존 경로를 유지한다. 비회원의 최근 분석·과거 기록 목록을 다시 노출하지 않는다. 분석 위험도와 AI 모델은 변경하지 않았다.

## 공식 자료 조사

확인일은 모두 2026-11-06이다. 원문 수정·검토일은 페이지에서 확인된 값만 저장했다. 앱 설명은 일반적인 의학 사실을 정리한 짧은 편집 문안이며, 원문 전체나 사진·도표를 복제하지 않는다. 사진 분석 클래스와 별개인 교육용 백과이며, 전문가 검수는 아직 받지 않았다.

| 백과 항목 | 연결한 공식 원문 | 원문 수정·검토일 |
| --- | --- | --- |
| 찰과상·벌어진 상처 | [NHS — Cuts and grazes](https://www.nhs.uk/conditions/cuts-and-grazes/) | 2026-04-02 |
| 타박상 | [미국 국립의학도서관 MedlinePlus — Bruises](https://medlineplus.gov/bruises.html) | 미기록 |
| 화상 | [질병관리청 국가건강정보포털 — 화상](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=6584) | 2026-06-04 |
| 여드름 | [질병관리청 국가건강정보포털 — 여드름](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=3947) | 2026-08-03 |
| 아토피 피부염 | [질병관리청 국가건강정보포털 — 아토피피부염](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=6582) | 2026-05-06 |
| 접촉 피부염 | [질병관리청 — 자극접촉피부염](https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=5689), [NHS — Contact dermatitis](https://www.nhs.uk/conditions/contact-dermatitis/) | 미기록 |

### API 조사 결과

- **질병관리청 OpenAPI**: 공식 안내에 신청자 정보 입력 → 건강정보 선택 → 요청 자료와 이용 목적 검토 → API 정보 확인 절차가 있다. 자동 갱신을 도입할 때 우선 후보로 삼는다. 현재 신청·승인·인증 정보는 확보하지 않았으며 실제 API 호출은 구현하지 않았다. 공개 안내만으로 미확인 엔드포인트나 응답 형식을 만들어 넣지 않았다. [신청 안내](https://health.kdca.go.kr/healthinfo/biz/health/portalUseGuidance/hlthinsReqst/hlthinsReqstMth.do?index=3)
- 질병관리청은 공공누리 제4유형이 표시된 자료에 출처표시·비상업적 이용·변경금지 조건을 안내한다. 향후 API 원문·이미지를 앱에 수록하려면 해당 콘텐츠의 이용 범위와 표시 방식을 확인한다. 이번에는 원문 링크와 짧은 독자적 설명을 사용한다. [콘텐츠 이용 안내](https://health.kdca.go.kr/healthinfo/biz/health/portalUseGuidance/hlthinsReqst/hlthinsReqstMth.do?index=1)
- **MedlinePlus Web Service**: 등록 없이 이용하는 무료 XML 검색 서비스가 있지만 영어·스페인어 자료를 제공한다. 한국어 자료를 직접 제공하는 용도로는 적합하지 않아 이번 앱에 자동 번역·실시간 연동을 추가하지 않았다. [공식 개발자 문서](https://medlineplus.gov/about/developers/webservices/)
- 현재 앱은 동봉된 자료를 표시하므로 백과 열람 때 외부 API 지연·키 오류가 발생하지 않는다. 최신 본문은 공식 원문 링크에서 확인한다. 앱의 출처 확인일은 실시간 갱신일이 아니다.

### 네이버 지도 적용 범위

- 의료기관 데이터는 기존 공공데이터 API를 그대로 사용한다. 네이버가 기관을 추천하거나 실시간 영업을 보증하는 구조가 아니다.
- 외부 검색 주소 `https://map.naver.com/p/search/{인코딩한 기관명·주소}`로 이동한다. 웹 링크이므로 별도 API 키가 필요 없다. 이 URL 형태는 [네이버 웨일 담당자의 공식 안내](https://forum.whale.naver.com/topic/57381/)에서 확인했다.
- 네이버 지도 앱 설치를 전제로 하지 않는다. 브라우저 또는 기기의 링크 처리 설정에 따라 열리며, 앱 강제 실행을 구현한 것은 아니다. 휴대전화에서 실제 전환·복귀는 최종 테스트 대상이다.
- 이름이 같은 기관이 있으므로 주소를 함께 전송한다. 검색 링크에 사용자 현재 좌표, 계정, 사진, 문진을 넣지 않는다. GPS 조회·동의 및 지역명 검색 로직은 그대로다.
- 앱 안에 실제 네이버 지도 SDK를 삽입하거나 `nmap://`로 네이티브 앱을 직접 여는 기능은 이번 범위가 아니다. 필요할 때 [네이버 지도 앱 연동 문서](https://guide.ncloud-docs.com/docs/application-maps-url-scheme-vpc)를 따라 별도 구현한다. 기존 ‘예시 지도’는 계속 가상 예시로 표시한다.

## 최종 확인 시 실행할 항목 — 모두 미실행

1. 비회원 시작 전·비회원 세션·회원 상태에서 마이페이지 표시, 긴 닉네임·이메일 및 큰 글꼴 줄바꿈, 계정 조회 실패 후 재시도.
2. 개인정보 설정 진입·동의 변경·철회, 문의 초안·답변 조회, 로그아웃 후 회원 데이터 미노출.
3. 탈퇴/자료 삭제 영역 기본 접힘, 펼치기·취소, 정확한 확인 단어를 입력해야 삭제 요청 가능. 실제 계정 삭제 없이 합성 테스트부터 진행.
4. 백과 7개 항목 열기, 본문 검색·필터·빈 결과·뒤로 복귀, 원문 출처와 언어·날짜 확인. 의료 문안 전문가 검수는 별도.
5. 병원·약국 검색 후 네이버 지도 열기와 앱 복귀, 같은 이름의 기관 주소 확인, 빈 기관 정보와 외부 링크 실패 처리. 네이버 지도 앱 유무를 나누어 확인.
6. 기존 분석·문진·기록 저장·경과 비교·소셜 로그인 회귀 확인 후 APK 생성 여부 결정.
7. QR 생성·게시와 설치 요청은 사용자가 다시 요청한 뒤 진행.

## 작업 기록

- 기존 파일 백업: 작업 공간 `work/my-encyclopedia-naver-v1/before/`.
- 사용한 작업: PowerShell `Get-Content`, `rg`, 공식 웹 문서 조회, `apply_patch`와 UTF-8 파일 쓰기. 원문 날짜 및 변경 소스는 읽어서 대조했다.
- 테스트 명령·APK 빌드·Docker 명령·게시 명령 실행: 없음.
- 기존 DB·사진·라벨·모델·배포 APK·QR 수정: 없음.
