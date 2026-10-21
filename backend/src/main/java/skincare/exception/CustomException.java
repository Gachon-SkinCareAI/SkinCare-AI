package skincare.exception;

import lombok.Getter;

// 전역 예외 처리를 위한 커스텀 RuntimeException 클래스
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode; // 발생한 에러 코드 정보

    // ErrorCode 기반 예외 생성자
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // Custom 메세지를 포함하는 ErrorCode 기반 예외 생성자
    public CustomException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}