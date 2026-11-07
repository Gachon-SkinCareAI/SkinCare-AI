

export const TYPES = { DERMATOLOGY: '피부과', GENERAL_HOSPITAL: '종합병원', PHARMACY: '약국', OTHER: '기타 의료기관' };

export const ANALYSIS_STATES = { CREATED: '생성됨', QUESTIONNAIRE: '문진 진행 중', COMPLETED: '결과 저장 완료', RETRY_REQUIRED: '재연결 필요' };

export const REVIEW_STATES = { DRAFT: '초안', SUBMITTED: '검수 요청', APPROVED: '승인 기록', REJECTED: '반려' };

export const REVIEW_KINDS = { CARE_GUIDE: '케어 가이드', QUESTION_BANK: '문진 질문', RISK_RULE: '위험 신호 규칙', POLICY: '정책 문서' };

export const AUDIT_ACTIONS = { ADMIN_CREATE: '관리자 계정 생성', ADMIN_BOOTSTRAP: '초기 관리자 생성', ADMIN_LOGIN: '관리자 로그인', ADMIN_LOGOUT: '관리자 로그아웃', ADMIN_PASSWORD_CHANGE: '관리자 비밀번호 변경', HOSPITAL_CREATE: '기관 등록', HOSPITAL_UPDATE: '기관 변경', INQUIRY_READ: '문의 상세 열람', INQUIRY_REPLY: '문의 답변', REVIEW_CREATE: '검수 초안 작성', REVIEW_SUBMIT: '검수 요청', REVIEW_APPROVE: '검수 승인 기록', REVIEW_REJECT: '검수 반려' };
