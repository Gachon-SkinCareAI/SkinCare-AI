package skincare.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 전역 예외 처리를 담당하는 어드바이스 클래스
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 로직 상 발생하는 커스텀 예외 처리
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.warn("CustomException : {} - {}", e.getErrorCode().getCode(), e.getMessage());
        return toResponse(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    // DTO 유효성 검증(@Valid) 실패 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotValidException : {}", e.getMessage());
        String detail = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, detail));
    }

    // 필수 multipart 파트 누락 예외 처리 (이미지 파트 누락 시 DIAGNOSIS-001)
    @ExceptionHandler(MissingServletRequestPartException.class)
    protected ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        log.warn("MissingServletRequestPartException : {}", e.getMessage());
        String partName = e.getRequestPartName();
        if ("image".equals(partName) || partName.endsWith("_image")) {
            return toResponse(ErrorResponse.of(ErrorCode.IMAGE_REQUIRED));
        }
        return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, null, "필수 항목 누락: " + partName));
    }

    // 필수 요청 파라미터 누락 예외 처리
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException : {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, null, "필수 파라미터 누락: " + e.getParameterName()));
    }

    // 요청 본문 JSON 형식 오류 또는 Enum 등 값 변환 실패 예외 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException : {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, null, "요청 본문의 형식 또는 값이 올바르지 않습니다."));
    }

    // 쿼리 파라미터/경로 변수 타입 변환 실패 예외 처리 (ex. page=abc, status=UNKNOWN_VALUE)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException : {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.INVALID_TYPE_VALUE, null, "올바르지 않은 값: " + e.getName()));
    }

    // 지원하지 않는 HTTP 메소드 호출 시 발생 예외
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException : {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    // 존재하지 않는 URL 요청 예외 처리 (404)
    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("NoResourceFoundException : {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.ENTITY_NOT_FOUND, "요청한 API 경로가 존재하지 않습니다."));
    }

    // 업로드 파일 용량 초과 예외 처리 (spring.servlet.multipart.max-file-size 초과)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("MaxUploadSizeExceededException : {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.IMAGE_TOO_LARGE));
    }

    // 접근 권한 부족 예외 처리
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("AccessDeniedException : {}", e.getMessage());
        return toResponse(ErrorResponse.of(ErrorCode.ACCESS_DENIED));
    }

    // 알 수 없는 기타 모든 서버 예외 처리 (500)
    // 내부 예외 메세지(SQL, 스택 정보 등)는 클라이언트에 노출하지 않고 서버 로그에만 기록
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled Internal Exception : ", e);
        return toResponse(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // ErrorResponse를 해당 HTTP 상태 코드의 응답으로 변환
    private ResponseEntity<ErrorResponse> toResponse(ErrorResponse response) {
        return ResponseEntity.status(response.getResultCode()).body(response);
    }
}
