package skincare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import skincare.domain.user.entity.UserSettings;

@Data
@Builder
@AllArgsConstructor
public class UserSettingsResponseDto {
    private String userId;
    private boolean pushDiagnosisAlert;
    private boolean pushRecoveryReminder;

    public static UserSettingsResponseDto from(UserSettings settings) {
        return UserSettingsResponseDto.builder()
                .userId(settings.getUserId())
                .pushDiagnosisAlert(settings.isPushDiagnosisAlert())
                .pushRecoveryReminder(settings.isPushRecoveryReminder())
                .build();
    }
}