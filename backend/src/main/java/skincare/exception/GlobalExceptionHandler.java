package skincare.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 전역 예외 처리를 담당하는 어드바이스 클래스
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 로직 상 발생하는 커스텀 예외 처리
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.warn("CustomException : {} - {}", e.getErrorCode().getCode(), e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, e.getMessage());
        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    // DTO 유효성 검증(@Valid) 실패 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotValidException : {}", e.getMessage());
        String detail = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, detail);
        return new ResponseEntity<>(response, ErrorCode.INVALID_INPUT_VALUE.getStatus());
    }

    // 지원하지 않는 HTTP 메소드 호출 시 발생 예외
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException : {}", e.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
        return new ResponseEntity<>(response, ErrorCode.METHOD_NOT_ALLOWED.getStatus());
    }

    // 접근 권한 부족 예외 처리
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("AccessDeniedException : {}", e.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.ACCESS_DENIED);
        return new ResponseEntity<>(response, ErrorCode.ACCESS_DENIED.getStatus());
    }

    // 알 수 없는 기타 모든 서버 예외 처리 (500)
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled Internal Exception : ", e);
        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getStatus());
    }
}