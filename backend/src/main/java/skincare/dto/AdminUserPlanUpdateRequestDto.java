package skincare.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import skincare.domain.user.entity.ENUM.PlanType;

// 관리자 회원 이용 플랜 변경 요청 DTO (PATCH /api/wc/admin/users/{userId}/plan)
@Data
// AdminUserPlanUpdateRequestDto 요청과 응답 데이터
public class AdminUserPlanUpdateRequestDto {

    @NotNull(message = "변경할 이용 플랜은 필수 입력 항목입니다.")
    private PlanType plan; // FREE, PREMIUM

    @Size(max = 500, message = "변경 사유는 500자 이하로 입력해 주세요.")
    private String reason; // 변경 사유 (이력 저장)
}
