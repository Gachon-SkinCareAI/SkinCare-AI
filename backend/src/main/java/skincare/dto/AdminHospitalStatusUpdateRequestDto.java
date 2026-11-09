package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Data;

// 관리자 의료기관 운영 상태 변경 요청 DTO (PATCH /api/wc/admin/hospitals/{hospitalId}/status)
@Data
// AdminHospitalStatusUpdateRequestDto 요청과 응답 데이터
public class AdminHospitalStatusUpdateRequestDto {

    // ACTIVE: 사용자 검색 노출, HIDDEN: 숨김 (공공데이터 동기화 후에도 숨김 유지)
    @NotBlank(message = "변경할 운영 상태는 필수 입력 항목입니다.")
    @Pattern(regexp = "ACTIVE|HIDDEN", message = "운영 상태는 ACTIVE 또는 HIDDEN이어야 합니다.")
    private String status;
}
