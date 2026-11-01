package skincare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import skincare.domain.user.entity.UserSettings;

import java.time.format.DateTimeFormatter;

// 사용자 환경설정 변경 결과 응답 DTO (PUT /api/wc/users/me/settings 응답의 updated_settings 객체)
@Data
@Builder
@AllArgsConstructor
public class UserSettingsResponseDto {
    private String userId;                // 사용자 아이디
    private boolean pushDiagnosisAlert;   // AI 분석 완료 푸시 알림 수신 여부
    private boolean pushRecoveryReminder; // 회복 경과 리마인더 푸시 알림 수신 여부
    private String updatedAt;             // 설정 변경 일시

    // UserSettings 엔티티를 응답 DTO로 변환
    public static UserSettingsResponseDto from(UserSettings settings) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        return UserSettingsResponseDto.builder()
                .userId(settings.getUserId())
                .pushDiagnosisAlert(settings.isPushDiagnosisAlert())
                .pushRecoveryReminder(settings.isPushRecoveryReminder())
                .updatedAt(settings.getUpdatedAt() != null ? settings.getUpdatedAt().format(formatter) : null)
                .build();
    }
}
