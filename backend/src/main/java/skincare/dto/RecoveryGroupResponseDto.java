package skincare.dto;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;

import java.time.format.DateTimeFormatter;
import java.util.List;

// 상처별 회복 경과 묶음 응답 DTO (GET /api/wc/recoveries 응답의 recovery_groups 항목)
// 하나의 최초 분석 기록과 그 이후 등록한 회복 기록 타임라인으로 구성 (최초 사진과 비교 조회용)
@Getter
@Builder
// RecoveryGroupResponseDto 요청과 응답 데이터
public class RecoveryGroupResponseDto {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private String groupId; // 회복 경과 묶음 ID (최초 분석 ID와 동일)
    private BodyPart bodyPart; // 신체 부위 코드
    private String bodyPartLabel; // 신체 부위 표시명
    private String status; // 경과 상태 (RECOVERING: 경과 관찰 중)
    private InitialDiagnosis initialDiagnosis; // 최초 분석 정보 (비교 기준 사진)
    private int recordCount; // 회복 기록 수
    private String latestRecordedAt; // 최근 기록 일시
    private List<RecoveryResponseDto> timeline; // 회복 기록 타임라인 (최신순)

    // 비교 기준이 되는 최초 분석 정보
    @Getter
    @Builder
    public static class InitialDiagnosis {
        private String diagnosisId; // 최초 분석 ID
        private String suspectedDisease; // AI 의심 유형 표시 문구
        private RiskLevel riskLevel; // 위험도
        private String diagnosedAt; // 분석 일시
        private String imageUrl; // 최초 분석 사진 조회 API 경로
    }

    // 분석 기록과 회복 기록 목록(최신순)으로 묶음 응답 생성
    public static RecoveryGroupResponseDto of(
            Diagnosis diagnosis, List<RecoveryResponseDto> timeline) {
        return RecoveryGroupResponseDto.builder()
                .groupId(diagnosis.getDiagnosisId())
                .bodyPart(diagnosis.getBodyPart())
                .bodyPartLabel(
                        diagnosis.getBodyPart() != null ? diagnosis.getBodyPart().getLabel() : null)
                .status("RECOVERING")
                .initialDiagnosis(
                        InitialDiagnosis.builder()
                                .diagnosisId(diagnosis.getDiagnosisId())
                                .suspectedDisease(diagnosis.getSuspectedDisease())
                                .riskLevel(diagnosis.getRiskLevel())
                                .diagnosedAt(
                                        diagnosis.getCreatedAt() != null
                                                ? diagnosis.getCreatedAt().format(FORMATTER)
                                                : null)
                                .imageUrl(
                                        DiagnosisResponseDto.imagePath(diagnosis.getDiagnosisId()))
                                .build())
                .recordCount(timeline.size())
                .latestRecordedAt(timeline.isEmpty() ? null : timeline.get(0).getRecordedAt())
                .timeline(timeline)
                .build();
    }
}
