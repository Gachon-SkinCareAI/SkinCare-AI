package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import skincare.domain.diagnosis.entity.Recovery;

import java.time.format.DateTimeFormatter;
import java.util.Map;

// 회복 경과 기록 응답 DTO (회복 기록 등록 응답의 recovery_record, 목록 응답의 timeline 항목)
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecoveryResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    // AI 면적 변화 경향 표시명 (임상적 호전 표현은 사용하지 않음)
    private static final Map<String, String> TREND_LABELS = Map.of(
            "smaller", "환부 면적이 작아 보임",
            "similar", "환부 면적 변화 적음",
            "larger", "환부 면적이 커 보임",
            "uncomparable", "사진 비교 불가");

    private String recoveryId;      // 회복 기록 고유 ID
    private String diagnosisId;     // 연결된 최초 분석 ID
    private String recordedAt;      // 기록 일시
    private String imageUrl;        // 경과 사진 조회 API 경로 (본인 인증 필요)
    private String memo;            // 경과 메모
    private Integer painScore;      // 통증 점수 (0 ~ 10)
    private Double areaRatioDelta;  // 최초 사진 대비 환부 박스 면적 비율 차 (참고값)
    private String trendHint;       // 면적 변화 경향 코드 (smaller, similar, larger, uncomparable)
    private String trendHintLabel;  // 면적 변화 경향 표시명
    private String trackingNote;    // 비교 결과 안내 문구

    // 경과 사진 조회 API 경로 생성
    public static String imagePath(String recoveryId) {
        return "/api/wc/recoveries/" + recoveryId + "/image";
    }

    // Recovery 엔티티를 응답 DTO로 변환
    public static RecoveryResponseDto from(Recovery recovery) {
        return RecoveryResponseDto.builder()
                .recoveryId(recovery.getRecoveryId())
                .diagnosisId(recovery.getDiagnosis().getDiagnosisId())
                .recordedAt(recovery.getCreatedAt() != null ? recovery.getCreatedAt().format(FORMATTER) : null)
                .imageUrl(imagePath(recovery.getRecoveryId()))
                .memo(recovery.getMemo())
                .painScore(recovery.getPainScore())
                .areaRatioDelta(recovery.getAreaRatioDelta())
                .trendHint(recovery.getTrendHint())
                .trendHintLabel(recovery.getTrendHint() != null ? TREND_LABELS.get(recovery.getTrendHint()) : null)
                .trackingNote(recovery.getTrackingNote())
                .build();
    }
}
