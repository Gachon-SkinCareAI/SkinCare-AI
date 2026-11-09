package skincare.external;

import java.util.Map;

// AnalysisAiClient 외부 서비스 연동
public interface AnalysisAiClient {
    Map<String, Object> predict(byte[] image, String contentType);

    Map<String, Object> question(Map<String, Object> request);

    Map<String, Object> analyze(Map<String, Object> request);

    Map<String, Object> guide(Map<String, Object> request);
}
