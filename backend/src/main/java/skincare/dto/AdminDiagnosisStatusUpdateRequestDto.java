package skincare.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;

@Getter
@Setter
@NoArgsConstructor
public class AdminDiagnosisStatusUpdateRequestDto {

    @NotNull(message = "진단건 처리 상태는 필수 선택 항목입니다.")
    private DiagnosisStatus status; // PENDING, IN_PROGRESS, COMPLETED, ALERT_SENT

    private String adminNote;       // 관리자 확인 및 조치 메모
}