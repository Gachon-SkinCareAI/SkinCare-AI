package skincare.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 클라이언트에게 전달할 에러 응답 DTO
@Getter
@Builder
public class ErrorResponse {

    private final LocalDateTime timestamp; // 예외 발생 시각
    private final int status;            // HTTP 상태 코드 숫자
    private final String code;           // 서비스 커스텀 에러 코드
    private final String message;        // 에러 메세지
    private final String details;        // 상세 에러 정보 또는 필드 예외 메세지

    // ErrorCode 기반 ErrorResponse 객체 생성
    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .details(null)
                .build();
    }

    // ErrorCode 및 상세 정보를 포함한 ErrorResponse 객체 생성
    public static ErrorResponse of(ErrorCode errorCode, String details) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .details(details)
                .build();
    }
}