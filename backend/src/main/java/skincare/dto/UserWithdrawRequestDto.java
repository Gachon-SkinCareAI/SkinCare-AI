package skincare.dto;

import lombok.Data;

// 사용자 회원 탈퇴 요청 DTO (DELETE /api/wc/users/me)
@Data
public class UserWithdrawRequestDto {
    private String password; // 본인 확인용 비밀번호 (일반 회원가입 사용자만 검증)
    private String reason;   // 탈퇴 사유
}