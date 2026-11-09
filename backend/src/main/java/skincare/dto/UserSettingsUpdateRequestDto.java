package skincare.dto;

import lombok.Data;

@Data
// UserSettingsUpdateRequestDto 요청과 응답 데이터
public class UserSettingsUpdateRequestDto {
    private Boolean pushDiagnosisAlert;
    private Boolean pushRecoveryReminder;
}
