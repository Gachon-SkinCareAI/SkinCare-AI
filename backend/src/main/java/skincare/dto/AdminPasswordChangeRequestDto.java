package skincare.dto;

import lombok.Data;

@Data
// AdminPasswordChangeRequestDto 요청과 응답 데이터
public class AdminPasswordChangeRequestDto {
    private String currentPassword; // 현재 비밀번호
    private String newPassword; // 변경할 새 비밀번호
}
