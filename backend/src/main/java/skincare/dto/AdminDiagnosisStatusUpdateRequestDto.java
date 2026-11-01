package skincare.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.ReviewStatus;

// 관리자 분석건 처리 상태/검수 정보 변경 요청 DTO (PATCH /api/wc/admin/diagnoses/{diagnosisId}/status)
@Getter
@Setter
@NoArgsConstructor
public class AdminDiagnosisStatusUpdateRequestDto {

    @NotNull(message = "분석건 처리 상태는 필수 선택 항목입니다.")
    private DiagnosisStatus status;     // PENDING, IN_PROGRESS, COMPLETED, ALERT_SENT

    private ReviewStatus reviewStatus;  // 검수 상태 (NORMAL, NEEDS_REVIEW, MISCLASSIFICATION_SUSPECTED, 선택)

    private String adminNote;           // 관리자 확인 및 조치 메모 (관리자 의견)
}
