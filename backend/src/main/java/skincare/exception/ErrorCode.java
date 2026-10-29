package skincare.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 애플리케이션 내 에러 코드 및 메세지 정의 Enum
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common (공통)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "올바르지 않은 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 HTTP 메소드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "존재하지 않는 리소스입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C005", "올바르지 않은 데이터 타입입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "C006", "접근 권한이 없습니다."),

    // Auth & User (인증 및 회원)
    DUPLICATE_USER_ID(HttpStatus.CONFLICT, "U001", "이미 존재하는 사용자 ID입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "존재하지 않는 사용자 계정입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "U003", "비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "U004", "유효하지 않거나 만료된 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "U005", "만료된 JWT 토큰입니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "U006", "지원하지 않는 토큰 형식입니다."),
    DUPLICATE_ADMIN_ID(HttpStatus.CONFLICT, "U007", "이미 존재하는 관리자 ID입니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "U008", "존재하지 않는 관리자 계정입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "U009", "이미 탈퇴 처리된 계정입니다."),

    // Diagnosis (AI 피부 진단)
    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "존재하지 않는 진단 기록입니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "D002", "유효하지 않거나 손상된 이미지 파일입니다."),
    UNSUPPORTED_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "D003", "지원하지 않는 이미지 확장자입니다. (JPG, PNG 가능)"),
    AI_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "D004", "AI 추론 엔진 분석 중 오류가 발생했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "D005", "AI 분석 서버 응답 시간이 초과되었습니다."),

    // Recovery (회복 경과)
    RECOVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "존재하지 않는 회복 경과 기록입니다."),

    // Inquiry (1:1 문의)
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "I001", "존재하지 않는 문의 내역입니다."),
    INQUIRY_ALREADY_ANSWERED(HttpStatus.BAD_REQUEST, "I002", "이미 답변이 완료된 문의입니다."),
    INQUIRY_ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "I003", "존재하지 않거나 삭제된 답변 내역입니다."),

    // Hospital & OpenAPI (의료기관)
    OPENAPI_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "H001", "공공데이터 OpenAPI 수집/동기화에 실패했습니다."),
    HOSPITAL_NOT_FOUND(HttpStatus.NOT_FOUND, "H002", "존재하지 않는 의료기관 정보입니다."),

    // MLOps (파이프라인)
    MLOPS_PIPELINE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "M001", "MLOps 재학습 파이프라인 수행 중 오류가 발생했습니다.");

    private final HttpStatus status; // HTTP 상태 코드
    private final String code;       // 서비스 커스텀 에러 코드
    private final String message;    // 에러 메세지
}