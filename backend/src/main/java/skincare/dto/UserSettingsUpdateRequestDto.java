package skincare.dto;

import lombok.Data;

// 사용자 푸시 알림 및 환경설정 변경 요청 DTO (PUT /api/wc/users/me/settings)
@Data
public class UserSettingsUpdateRequestDto {
    private Boolean pushDiagnosisAlert;   // AI 진단 완료 푸시 알림 수신 여부 (null이면 유지)
    private Boolean pushRecoveryReminder; // 회복 경과 리마인더 푸시 알림 수신 여부 (null이면 유지)
}