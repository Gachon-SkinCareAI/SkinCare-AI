package skincare.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import skincare.domain.user.entity.ENUM.UserStatus;

// 관리자 회원 계정 상태 변경 요청 DTO (PATCH /api/wc/admin/users/{userId}/status)
@Data
public class AdminUserStatusUpdateRequestDto {

    @NotNull(message = "변경할 계정 상태는 필수 입력 항목입니다.")
    private UserStatus status; // ACTIVE: 활성, INACTIVE: 비활성, SUSPENDED: 정지 (DELETED는 회원 탈퇴로만 처리)

    @Size(max = 500, message = "변경 사유는 500자 이하로 입력해 주세요.")
    private String reason;     // 변경 사유 (이력 저장)
}
