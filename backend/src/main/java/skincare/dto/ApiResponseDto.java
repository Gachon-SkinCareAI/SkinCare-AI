package skincare.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

// 공통 API 응답 래퍼 DTO (설계서 7.3 서비스 REST API 응답 형식)
// {
//   "content_type": "json", "result_code": 200, "result_msg": "...",
//   "<API별 응답 키>": ...   ← with()로 추가 (ex. user_info, diagnoses_list, total_count)
// }
@Getter
@JsonPropertyOrder({"content_type", "result_code", "result_msg"})
public class ApiResponseDto {

    private final String contentType = "json"; // 응답 형식
    private final int resultCode;              // 처리결과 코드 (200: 성공, 201: 생성 완료)
    private final String resultMsg;            // 처리결과 메세지

    // API별 응답 데이터 (키 이름을 설계서와 동일하게 지정하기 위해 Map 사용, 응답 시 최상위 필드로 펼쳐짐)
    private final Map<String, Object> payload = new LinkedHashMap<>();

    private ApiResponseDto(HttpStatus status, String resultMsg) {
        this.resultCode = status.value();
        this.resultMsg = resultMsg;
    }

    // 200 OK 응답 생성
    public static ApiResponseDto ok(String resultMsg) {
        return new ApiResponseDto(HttpStatus.OK, resultMsg);
    }

    // 201 Created 응답 생성 (회원가입, 분석 요청, 문의 등록 등 신규 리소스 생성)
    public static ApiResponseDto created(String resultMsg) {
        return new ApiResponseDto(HttpStatus.CREATED, resultMsg);
    }

    // 응답 데이터 추가 (key는 설계서 응답 필드명 그대로 snake_case로 지정)
    public ApiResponseDto with(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }

    // 페이지 조회 결과 추가 (total_count, total_pages, current_page, 목록 키)
    // current_page는 설계서 기준 1부터 시작
    public ApiResponseDto withPage(String listKey, Page<?> page) {
        this.payload.put("total_count", page.getTotalElements());
        this.payload.put("total_pages", page.getTotalPages());
        this.payload.put("current_page", page.getNumber() + 1);
        this.payload.put(listKey, page.getContent());
        return this;
    }

    // API별 응답 데이터를 최상위 JSON 필드로 직렬화
    @JsonAnyGetter
    public Map<String, Object> getPayload() {
        return payload;
    }

    // result_code와 동일한 HTTP 상태의 ResponseEntity로 변환
    public ResponseEntity<ApiResponseDto> toResponseEntity() {
        return ResponseEntity.status(resultCode).body(this);
    }
}
