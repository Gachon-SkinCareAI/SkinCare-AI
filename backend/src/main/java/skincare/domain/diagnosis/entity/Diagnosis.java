package skincare.domain.diagnosis.entity;

import skincare.domain.diagnosis.entity.ENUM.*;
import skincare.domain.global.BaseTimeEntity;
import skincare.domain.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// [분석 로그] 피부·상처 AI 분석 기록 엔티티
// 화면 표시 명칭은 '분석'을 사용하되 내부 Entity명은 Diagnosis 유지
@Entity
@Table(name = "diagnoses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Diagnosis extends BaseTimeEntity {

    // 분석 고유 ID
    @Id
    @Column(name = "diagnosis_id", length = 36)
    private String diagnosisId; // ex. DX-20261101-153022-3fa85f6457a1

    // 분석을 요청한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 촬영 상처 원본 이미지 저장 경로 (파일 저장소 storage key, 응답 시 이미지 조회 API 경로로 변환)
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    // 촬영 신체 부위 (ARM, LEG, FACE, TORSO 등)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // MySQL ENUM 타입 대신 VARCHAR로 매핑 (Enum 값 추가 시 DDL 변경 불필요)
    @Column(name = "body_part", nullable = false, length = 20)
    private BodyPart bodyPart;

    // AI 의심 유형 코드 (abrasion, bruise, burn, open_wound, normal_skin, UNKNOWN)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ai_label", length = 20)
    private WoundType aiLabel;

    // AI 의심 유형 표시 문구 (ex. 화상 의심)
    @Column(name = "suspected_disease", nullable = false, length = 100)
    private String suspectedDisease;

    // 위험도 단계 (OBSERVATION: 관찰, WARNING: 경고, EMERGENCY: 응급, UNKNOWN: 판단 불가)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // MySQL ENUM 타입 대신 VARCHAR로 매핑 (Enum 값 추가 시 DDL 변경 불필요)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    // AI 예측 신뢰도 점수 (0.0 ~ 100.0)
    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    // Confidence/OOD Gate 통과 여부 (false면 의심 유형을 강제하지 않고 UNKNOWN 처리)
    @Column(name = "gate_passed")
    private Boolean gatePassed;

    // Gate 미통과 사유 (ex. low_conf, ood, no_roi)
    @Column(name = "gate_reason", length = 50)
    private String gateReason;

    // AI 서버 predict 요청 추적 ID (피드백/재학습 연계용)
    @Column(name = "ai_request_id", length = 64)
    private String aiRequestId;

    // 분석에 사용된 AI 모델 버전 (ex. detect=yolov8n-wound-det-20261001, classify=vit-wound-clf-20261001)
    @Column(name = "model_version", length = 200)
    private String modelVersion;

    // 위험도 평가에 적용된 규칙 ID 목록 (쉼표 구분, ex. open.dirty,burn.blister)
    @Column(name = "rule_ids", length = 500)
    private String ruleIds;

    // 적용된 공인 케어 카드 ID 목록 (쉼표 구분, ex. kdca.burn.cool)
    @Column(name = "applied_card_ids", length = 500)
    private String appliedCardIds;

    // 공인 케어 카드 원문/출처/피해야 할 행동 (JSON)
    @Column(name = "care_cards", columnDefinition = "TEXT")
    private String careCards;

    // 문진 질문/응답 및 확보된 슬롯 (JSON)
    @Column(name = "questionnaire", columnDefinition = "TEXT")
    private String questionnaire;

    // 사용자 학습 활용 동의 여부 (true인 분석만 재학습 후보로 사용, 설계서 6.3.8)
    @Column(name = "training_use", nullable = false)
    @Builder.Default
    private boolean trainingUse = false;

    // 분석건 처리 상태 (PENDING: 대기, IN_PROGRESS: 진행중, COMPLETED: 처리완료, ALERT_SENT: 응급알림발송)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // MySQL ENUM 타입 대신 VARCHAR로 매핑 (Enum 값 추가 시 DDL 변경 불필요)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DiagnosisStatus status = DiagnosisStatus.COMPLETED;

    // 관리자 검수 상태 (UNREVIEWED: 미검수, NORMAL: 정상, NEEDS_REVIEW: 확인 필요, MISCLASSIFICATION_SUSPECTED: 오분류 의심)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "review_status", length = 30)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.UNREVIEWED;

    // 마지막으로 처리 상태/검수 정보를 변경한 관리자 ID
    @Column(name = "reviewed_by", length = 36)
    private String reviewedBy;

    // 마지막 관리자 처리 일시
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // 사용자 작성 자가 증상 메모
    @Column(columnDefinition = "TEXT")
    private String symptoms;

    // 응급처치 가이드 (공인 케어 카드 원문 요약)
    @Column(name = "first_aid_guide", columnDefinition = "TEXT")
    private String firstAidGuide;

    // 주의사항 및 행동 지침 (피해야 할 행동 목록)
    @Column(name = "precautions_guide", columnDefinition = "TEXT")
    private String precautionsGuide;

    // 관리자 확인 및 조치 메모 (관리자 의견)
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // AI 비전 분석 세부 메트릭 (1:1 연관관계)
    @OneToOne(mappedBy = "diagnosis", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DiagnosisMetrics metrics;

    // 상처 회복 경과 기록 목록 (1:N 연관관계)
    @OneToMany(mappedBy = "diagnosis", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Recovery> recoveries = new ArrayList<>();

    // 관리자 처리 이력 목록 (감사 이력, 1:N 연관관계)
    @OneToMany(mappedBy = "diagnosis", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<DiagnosisActionHistory> actionHistories = new ArrayList<>();

    // 관리자 처리 상태/검수 상태/메모 변경 및 감사 이력 기록 메서드
    public DiagnosisActionHistory updateByAdmin(DiagnosisStatus status, ReviewStatus reviewStatus,
                                                String adminNote, String adminId) {
        DiagnosisStatus previousStatus = this.status;

        if (status != null) this.status = status;
        if (reviewStatus != null) this.reviewStatus = reviewStatus;
        if (adminNote != null) this.adminNote = adminNote;
        this.reviewedBy = adminId;
        this.reviewedAt = LocalDateTime.now();

        DiagnosisActionHistory history = DiagnosisActionHistory.builder()
                .diagnosis(this)
                .previousStatus(previousStatus)
                .currentStatus(this.status)
                .reviewStatus(this.reviewStatus)
                .note(adminNote)
                .processedBy(adminId)
                .build();
        this.actionHistories.add(history);
        return history;
    }

    // 문진 진행 상태(질문/응답/슬롯) 갱신 메서드
    public void updateQuestionnaire(String questionnaire) {
        this.questionnaire = questionnaire;
    }

    // 문진 완료 후 위험도 및 케어 가이드 재평가 결과 반영 메서드
    // 재평가 결과가 응급이면 관리자 확인이 필요하므로 처리 상태를 대기(PENDING)로 변경
    public void applyReassessment(RiskLevel riskLevel, String ruleIds, String appliedCardIds, String careCards,
                                  String firstAidGuide, String precautionsGuide) {
        this.riskLevel = riskLevel;
        this.ruleIds = ruleIds;
        this.appliedCardIds = appliedCardIds;
        this.careCards = careCards;
        this.firstAidGuide = firstAidGuide;
        this.precautionsGuide = precautionsGuide;
        if (riskLevel == RiskLevel.EMERGENCY && this.status == DiagnosisStatus.COMPLETED) {
            this.status = DiagnosisStatus.PENDING;
        }
    }

    // 학습 활용 동의 여부 변경 메서드 (사용자가 언제든 동의/철회 가능)
    public void updateTrainingUse(boolean trainingUse) {
        this.trainingUse = trainingUse;
    }

    // AI 비전 분석 메트릭 연관관계 초기화 메서드
    public void initMetrics(DiagnosisMetrics metrics) {
        this.metrics = metrics;
    }
    public void updateStatus(DiagnosisStatus status,String note){this.status=status;if(note!=null)this.adminNote=note;}
}
