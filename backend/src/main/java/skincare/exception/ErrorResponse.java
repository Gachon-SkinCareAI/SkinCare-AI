package skincare.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 클라이언트에게 전달할 에러 응답 DTO
// 성공 응답(ApiResponseDto)과 같은 공통 필드(content_type, result_code, result_msg)를 사용하여
// 클라이언트가 성공/실패를 동일한 방식으로 처리할 수 있도록 구성
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"content_type", "result_code", "result_msg", "error_code", "details", "timestamp"})
public class ErrorResponse {

    @Builder.Default
    private final String contentType = "json"; // 응답 형식
    private final int resultCode;              // HTTP 상태 코드 숫자 (401, 404, 500 등)
    private final String resultMsg;            // 에러 메세지 (사용자 표시용)
    private final String errorCode;            // 서비스 커스텀 에러 코드 (설계서 8.4 기준, ex. AUTH-001)
    private final String details;              // 상세 에러 정보 (필드 검증 실패 내용 등, 없으면 생략)
    private final LocalDateTime timestamp;     // 예외 발생 시각

    // ErrorCode 기본 메세지로 ErrorResponse 객체 생성
    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage(), null);
    }

    // ErrorCode와 상황별 메세지로 ErrorResponse 객체 생성 (메세지가 없으면 기본 메세지 사용)
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, null);
    }

    // ErrorCode, 메세지 및 상세 정보를 포함한 ErrorResponse 객체 생성
    public static ErrorResponse of(ErrorCode errorCode, String message, String details) {
        return ErrorResponse.builder()
                .resultCode(errorCode.getStatus().value())
                .resultMsg(message != null ? message : errorCode.getMessage())
                .errorCode(errorCode.getCode())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
