package skincare.domain.diagnosis.entity;

import skincare.domain.global.BaseTimeEntity;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

// [진단 로그] 피부 상처/질환 AI 분석 진단 기록 엔티티
@Entity
@Table(name = "diagnoses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Diagnosis extends BaseTimeEntity {

    // 진단 고유 ID
    @Id
    @Column(name = "diagnosis_id", length = 36)
    private String diagnosisId; // ex. DX-20260929-0438

    // 진단을 요청한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 촬영 상처 원본 이미지 URL
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    // 촬영 신체 부위 (ARM, LEG, FACE, TORSO 등)
    @Enumerated(EnumType.STRING)
    @Column(name = "body_part", nullable = false, length = 20)
    private BodyPart bodyPart;

    // AI 예측 의심 질환명
    @Column(name = "suspected_disease", nullable = false, length = 100)
    private String suspectedDisease;

    // 위험도 단계 (NORMAL: 정상, OBSERVATION: 관찰, WARNING: 경고, EMERGENCY: 응급)
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    // AI 예측 신뢰도 점수 (0.0 ~ 100.0)
    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    // 진단건 처리 상태 (PENDING: 대기, IN_PROGRESS: 진행중, COMPLETED: 완료, ALERT_SENT: 응급알림발송)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DiagnosisStatus status = DiagnosisStatus.COMPLETED;

    // 사용자 작성 자가 증상 메모
    @Column(columnDefinition = "TEXT")
    private String symptoms;

    // 응급처치 가이드
    @Column(name = "first_aid_guide", columnDefinition = "TEXT")
    private String firstAidGuide;

    // 주의사항 및 행동 지침
    @Column(name = "precautions_guide", columnDefinition = "TEXT")
    private String precautionsGuide;

    // 관리자 확인 및 조치 메모
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // AI 비전 분석 세부 메트릭 (1:1 연관관계)
    @OneToOne(mappedBy = "diagnosis", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DiagnosisMetrics metrics;

    // 상처 회복 경과 기록 목록 (1:N 연관관계)
    @OneToMany(mappedBy = "diagnosis", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Recovery> recoveries = new ArrayList<>();

    // 관리자 처리 상태 및 메모 변경 메서드
    public void updateStatus(DiagnosisStatus status, String adminNote) {
        this.status = status;
        if (adminNote != null) this.adminNote = adminNote;
    }

    public void initMetrics(DiagnosisMetrics metrics) {
        this.metrics = metrics;
    }
}