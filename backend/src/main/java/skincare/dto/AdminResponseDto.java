package skincare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
// AdminResponseDto 요청과 응답 데이터
public class AdminResponseDto {
    private String adminId;
    private String name;
    private String token;
}
