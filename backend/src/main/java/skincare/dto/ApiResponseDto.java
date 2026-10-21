package skincare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ApiResponseDto<T> {
    private int resultCode;
    private String resultMsg;
    private T data;

    public static <T> ApiResponseDto<T> success(String resultMsg, T data) {
        return ApiResponseDto.<T>builder()
                .resultCode(200)
                .resultMsg(resultMsg)
                .data(data)
                .build();
    }

    public static ApiResponseDto<Void> success(String resultMsg) {
        return ApiResponseDto.<Void>builder()
                .resultCode(200)
                .resultMsg(resultMsg)
                .data(null)
                .build();
    }
}