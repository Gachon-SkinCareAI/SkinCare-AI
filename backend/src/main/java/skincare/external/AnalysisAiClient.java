package skincare.external;

import java.util.Map;
public interface AnalysisAiClient {
    Map<String, Object> predict(byte[] image, String contentType);
    Map<String, Object> question(Map<String, Object> request);
    Map<String, Object> analyze(Map<String, Object> request);
    Map<String, Object> guide(Map<String, Object> request);
}
