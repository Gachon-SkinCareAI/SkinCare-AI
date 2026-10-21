package skincare.dto;

import lombok.Data;

@Data
public class AdminPasswordChangeRequestDto {
    private String currentPassword; // 현재 비밀번호
    private String newPassword;     // 변경할 새 비밀번호
}