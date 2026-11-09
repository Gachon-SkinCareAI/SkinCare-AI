package skincare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
// ApiResponseDto 요청과 응답 데이터
public class ApiResponseDto<T> {
    private int resultCode;
    private String resultMsg;
    private T data;

    public static <T> ApiResponseDto<T> success(String resultMsg, T data) {
        return ApiResponseDto.<T>builder().resultCode(200).resultMsg(resultMsg).data(data).build();
    }

    public static ApiResponseDto<Void> success(String resultMsg) {
        return ApiResponseDto.<Void>builder()
                .resultCode(200)
                .resultMsg(resultMsg)
                .data(null)
                .build();
    }

    @Builder.Default @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();

    @com.fasterxml.jackson.annotation.JsonAnyGetter
    public java.util.Map<String, Object> extraFields() {
        return extras;
    }

    public static ApiResponseDto<Object> ok(String message) {
        return success(message, new java.util.LinkedHashMap<>());
    }

    public static ApiResponseDto<Object> created(String message) {
        var d = ok(message);
        d.setResultCode(201);
        return d;
    }

    public ApiResponseDto<T> with(String key, Object value) {
        extras.put(key, value);
        return this;
    }

    public ApiResponseDto<T> withPage(String key, org.springframework.data.domain.Page<?> page) {
        return with("total_count", page.getTotalElements())
                .with("total_pages", page.getTotalPages())
                .with("current_page", page.getNumber() + 1)
                .with(key, page.getContent());
    }

    public org.springframework.http.ResponseEntity<ApiResponseDto> toResponseEntity() {
        return org.springframework.http.ResponseEntity.status(resultCode).body(this);
    }
}
