package skincare.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

// 국립중앙의료원 응급의료기관 OpenAPI(E-Gen) 연동 외부 클라이언트
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalApiClient {

    // 공공데이터포털 OpenAPI 서비스 인증키
    @Value("${emergency.openapi.service-key:}")
    private String emergencyServiceKey;

    // 최신 E-Gen 응급의료기관 목록 조회 URL로 변경
    @Value("${emergency.openapi.url:http://apis.data.go.kr/B552657/ErmctInfoInqireService/getEgytListInfoInqire}")
    private String emergencyUrl;

    private final RestTemplate restTemplate; // OpenAPI 호출용 RestTemplate (AppConfig의 타임아웃 설정 공유)
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 응답 파싱용 ObjectMapper

    /**
     * 국립중앙의료원 OpenAPI를 호출하여 전국의 응급실 보유 병원 명칭 목록(Set)을 수집
     */
    public Set<String> fetchEmergencyHospitalNames() {
        Set<String> emergencyHospitals = new HashSet<>();

        // 서비스 인증키 미설정 시 API 호출 생략 (CSV 세부정보 기준 응급실 여부만 반영)
        String serviceKey = sanitizeServiceKey(emergencyServiceKey);
        if (serviceKey.isEmpty()) {
            log.warn("[Emergency API] API_SERVICE_KEY가 설정되지 않아 응급의료기관 OpenAPI 호출을 생략합니다.");
            return emergencyHospitals;
        }

        int pageNo = 1;
        int numOfRows = 100;
        int totalCount = 0;

        try {
            do {
                StringBuilder urlBuilder = new StringBuilder(emergencyUrl);
                urlBuilder.append("?serviceKey=").append(serviceKey);
                urlBuilder.append("&pageNo=").append(pageNo);
                urlBuilder.append("&numOfRows=").append(numOfRows);
                urlBuilder.append("&_type=json");

                URI uri = new URI(urlBuilder.toString());
                String rawJson = restTemplate.getForObject(uri, String.class);

                if (rawJson == null || rawJson.isEmpty()) {
                    break;
                }

                JsonNode root = objectMapper.readTree(rawJson);
                JsonNode bodyNode = root.path("response").path("body");
                totalCount = bodyNode.path("totalCount").asInt(0);

                JsonNode itemsNode = bodyNode.path("items").path("item");

                if (itemsNode.isArray()) {
                    for (JsonNode elem : itemsNode) {
                        // 국립중앙의료원 E-Gen API의 병원명 필드: dutyName
                        String dutyName = elem.path("dutyName").asText("");
                        if (!dutyName.isEmpty()) {
                            emergencyHospitals.add(dutyName.trim());
                        }
                    }
                } else if (itemsNode.isObject()) {
                    String dutyName = itemsNode.path("dutyName").asText("");
                    if (!dutyName.isEmpty()) {
                        emergencyHospitals.add(dutyName.trim());
                    }
                }

                pageNo++;
            } while ((pageNo - 1) * numOfRows < totalCount);

            log.info("[Emergency API] 총 {}개 응급의료기관 정보 수집 완료", emergencyHospitals.size());

        } catch (Exception e) {
            log.error("[Emergency API] 응급의료기관 수집 중 오류 발생: {}", e.getMessage());
            // 응급실 API에서 에러가 나더라도 전체 동기화 프로세스가 중단되지 않도록 empty set 반환
        }

        return emergencyHospitals;
    }

    // 인증키 값 정리 (.env에서 따옴표로 감싸거나 URL 파라미터 구분자 '&'가 함께 입력된 경우 제거)
    private String sanitizeServiceKey(String rawKey) {
        if (rawKey == null) {
            return "";
        }
        String key = rawKey.trim();
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
            key = key.substring(1, key.length() - 1).trim();
        }
        while (key.endsWith("&")) {
            key = key.substring(0, key.length() - 1);
        }
        return key;
    }
}
