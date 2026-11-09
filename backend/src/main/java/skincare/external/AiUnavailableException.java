package skincare.external;

// AiUnavailableException 외부 서비스 연동
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() {
        super("AI service unavailable");
    }
}
