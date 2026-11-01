package skincare.domain.diagnosis.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 분석건 처리 상태 (PENDING: 대기, IN_PROGRESS: 진행중, COMPLETED: 처리완료, ALERT_SENT: 응급알림발송)
@Getter
@RequiredArgsConstructor
public enum DiagnosisStatus {
    PENDING("대기"),
    IN_PROGRESS("진행중"),
    COMPLETED("처리완료"),
    ALERT_SENT("알림발송");

    private final String label; // 화면 표시용 한글 명칭
}
