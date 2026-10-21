package skincare.dto;

import lombok.Data;

@Data
public class UserSettingsUpdateRequestDto {
    private Boolean pushDiagnosisAlert;
    private Boolean pushRecoveryReminder;
}