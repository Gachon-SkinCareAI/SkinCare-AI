package skincare.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 애플리케이션 내 에러 코드 및 메세지 정의 Enum
// 코드 체계는 설계서 8.4 오류코드 설계(AUTH/USER/DIAGNOSIS/RECOVERY/INQUIRY/HOSPITAL/AI/MLOPS)를 따르며,
// 설계서에 없는 항목은 같은 분류 안에서 이어지는 번호로 추가
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common (공통)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON-001", "올바르지 않은 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-002", "지원하지 않는 HTTP 메소드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-003", "존재하지 않는 리소스입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-004", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "COMMON-005", "올바르지 않은 데이터 타입입니다."),

    // Auth (인증) - 설계서 8.4.1
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-001", "아이디 또는 비밀번호가 일치하지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-002", "로그인 토큰이 만료되었습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-003", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-004", "유효하지 않은 토큰입니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-005", "지원하지 않는 토큰 형식입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH-006", "로그인이 필요한 요청입니다."),

    // User (회원) - 설계서 8.4.1
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "존재하지 않는 사용자 계정입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER-002", "이미 사용 중인 이메일입니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER-003", "이용이 정지된 계정입니다. 고객센터에 문의해 주세요."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "USER-004", "이미 탈퇴 처리된 계정입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "USER-005", "이메일 인증이 완료되지 않았습니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "USER-006", "이용약관 및 개인정보 처리방침에 동의해야 합니다."),

    // Admin (관리자)
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN-001", "존재하지 않는 관리자 계정입니다."),
    DUPLICATE_ADMIN_ID(HttpStatus.CONFLICT, "ADMIN-002", "이미 존재하는 관리자 ID입니다."),
    DUPLICATE_ADMIN_EMAIL(HttpStatus.CONFLICT, "ADMIN-003", "이미 사용 중인 관리자 이메일입니다."),

    // Diagnosis (AI 피부·상처 분석) - 설계서 8.4.2
    IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "DIAGNOSIS-001", "분석에 필요한 이미지가 없습니다."),
    UNSUPPORTED_IMAGE_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DIAGNOSIS-002", "지원하지 않는 이미지 형식입니다. (JPG, PNG, HEIC 가능)"),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "DIAGNOSIS-003", "업로드 가능한 이미지 용량(10MB)을 초과했습니다."),
    IMAGE_QUALITY_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "DIAGNOSIS-004", "이미지 품질 검사에 실패했습니다. 밝은 곳에서 환부가 잘 보이도록 다시 촬영해 주세요."),
    NO_ROI_DETECTED(HttpStatus.UNPROCESSABLE_CONTENT, "DIAGNOSIS-005", "사진에서 환부를 찾지 못했습니다. 환부가 화면 중앙에 오도록 다시 촬영해 주세요."),
    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "DIAGNOSIS-006", "요청한 분석 기록이 없습니다."),
    DIAGNOSIS_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DIAGNOSIS-007", "분석 결과 저장에 실패했습니다."),
    IMAGE_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "DIAGNOSIS-008", "이미지 파일을 찾을 수 없습니다."),

    // Recovery (회복 경과) - 설계서 8.4.3
    RECOVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "RECOVERY-001", "요청한 회복 기록이 없습니다."),
    PARENT_DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "RECOVERY-002", "상위 분석 기록이 없습니다."),
    INVALID_PAIN_SCORE(HttpStatus.BAD_REQUEST, "RECOVERY-003", "통증 점수는 0~10 사이여야 합니다."),
    RECOVERY_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RECOVERY-004", "회복 기록 저장에 실패했습니다."),

    // Inquiry (1:1 문의) - 설계서 8.4.4
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "INQUIRY-001", "요청한 문의가 없습니다."),
    INVALID_INQUIRY_CONTENT(HttpStatus.BAD_REQUEST, "INQUIRY-002", "유효하지 않은 문의 내용입니다."),
    INQUIRY_ALREADY_CLOSED(HttpStatus.CONFLICT, "INQUIRY-003", "이미 답변이 완료된 문의입니다."),
    RELATED_DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "INQUIRY-004", "연관된 분석 기록이 없습니다."),
    ANSWER_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INQUIRY-ANSWER-001", "답변 저장에 실패했습니다."),
    INQUIRY_ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "INQUIRY-ANSWER-002", "존재하지 않거나 삭제된 답변 내역입니다."),

    // Hospital (의료기관) - 설계서 8.4.5
    HOSPITAL_NOT_FOUND(HttpStatus.NOT_FOUND, "HOSPITAL-001", "요청한 병원 정보가 없습니다."),
    PUBLIC_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "HOSPITAL-002", "공공 API 서비스를 이용할 수 없습니다."),
    PUBLIC_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "HOSPITAL-003", "공공 API 응답 시간이 초과되었습니다."),
    HOSPITAL_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "HOSPITAL-004", "병원 정보 동기화에 실패했습니다."),

    // AI (AI 서버 연동) - 설계서 8.4.6
    // ※ 설계서의 HTTP 상태는 AI 서버의 응답 기준이며, 메인 서버는 클라이언트에 아래 상태로 전달
    //   (AI 서버 인증 실패를 401로 전달하면 앱이 사용자 로그인 만료로 오인하므로 502로 변환)
    AI_SERVICE_UNAUTHORIZED(HttpStatus.BAD_GATEWAY, "AI-001", "AI 서비스 인증에 실패했습니다."),
    MODEL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI-002", "AI 모델을 일시적으로 이용할 수 없습니다."),
    MODEL_INFERENCE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI-003", "AI 모델 분석 중 오류가 발생했습니다."),
    AI_SERVER_BUSY(HttpStatus.TOO_MANY_REQUESTS, "AI-004", "AI 분석 요청이 많습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_AI_REQUEST(HttpStatus.UNPROCESSABLE_CONTENT, "AI-005", "유효하지 않은 AI 분석 요청입니다."),
    QUESTION_ENGINE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI-006", "문진 엔진 처리 중 오류가 발생했습니다."),
    RISK_ENGINE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI-007", "위험도 평가 엔진 처리 중 오류가 발생했습니다."),
    GUIDE_RETRIEVAL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI-008", "케어 가이드 조회에 실패했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI-009", "AI 분석 서버 응답 시간이 초과되었습니다."),

    // MLOps (재학습) - 설계서 8.4.7
    RETRAIN_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "MLOPS-001", "재학습 이력이 없습니다."),
    RETRAIN_ALREADY_RUNNING(HttpStatus.CONFLICT, "MLOPS-002", "이미 재학습이 진행 중입니다."),
    TRAINING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MLOPS-003", "모델 학습에 실패했습니다."),
    MODEL_VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "MLOPS-004", "모델 검증에 실패했습니다."),
    DEPLOY_APPROVAL_REQUIRED(HttpStatus.FORBIDDEN, "MLOPS-005", "배포 승인이 필요합니다."),
    MODEL_DEPLOY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MLOPS-006", "모델 배포에 실패했습니다.");

    private final HttpStatus status; // HTTP 상태 코드
    private final String code;       // 서비스 커스텀 에러 코드 (설계서 8.4 기준)
    private final String message;    // 에러 메세지
}
