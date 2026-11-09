package skincare.external;

import java.util.Map;

// AiStatusClient 외부 서비스 연동
public interface AiStatusClient {
    Map<String, Object> status();
}
