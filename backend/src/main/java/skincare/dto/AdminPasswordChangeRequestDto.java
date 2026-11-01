package skincare.dto;

import lombok.Data;

// 관리자 비밀번호 변경 요청 DTO (PUT /api/wc/admin/password)
@Data
public class AdminPasswordChangeRequestDto {
    private String currentPassword; // 현재 비밀번호
    private String newPassword;     // 변경할 새 비밀번호
}