package skincare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminResponseDto {
    private String adminId;
    private String name;
    private String token;
}