package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

// 엔티티의 JSON 문자열 컬럼(케어 카드, 문진 내용 등) 변환 컴포넌트
// Spring Boot 기본 JsonMapper(snake_case 설정)를 사용하여 API 응답과 동일한 필드명으로 저장/복원
@Slf4j
@Component
@RequiredArgsConstructor
// JsonConverter 비즈니스 로직 처리
public class JsonConverter {

    private final JsonMapper jsonMapper; // Spring Boot 기본 Jackson 설정 공유

    // 객체를 JSON 문자열로 변환 (null이면 null)
    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return jsonMapper.writeValueAsString(value);
    }

    // JSON 문자열을 지정 타입 객체로 변환 (값이 없거나 형식이 깨진 경우 null)
    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("[Json] JSON 변환 실패 ({}): {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    // JSON 문자열을 Map으로 변환 (값이 없거나 형식이 깨진 경우 빈 Map)
    public Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Json] JSON Map 변환 실패: {}", e.getMessage());
            return Map.of();
        }
    }
}
