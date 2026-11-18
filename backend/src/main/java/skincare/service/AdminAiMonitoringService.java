package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.ai.entity.AiModelMetrics;
import skincare.domain.ai.entity.AiPipeline;
import skincare.domain.ai.entity.AiRetrainHistory;
import skincare.domain.diagnosis.entity.ENUM.WoundType;
import skincare.dto.AiMetricRequestDto;
import skincare.dto.AiModelMetricResponseDto;
import skincare.dto.AiPipelineResponseDto;
import skincare.dto.AiRetrainHistoryResponseDto;
import skincare.dto.Paging;
import skincare.dto.ai.AiHealthResponseDto;
import skincare.dto.ai.AiJobResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.AiServiceClient;
import skincare.repository.AiModelMetricsRepository;
import skincare.repository.AiPipelineRepository;
import skincare.repository.AiRetrainHistoryRepository;
import skincare.repository.DiagnosisRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 관리자 AI 모델 성능, 파이프라인, MLOps 재학습 이력 모니터링 관련 비즈니스 로직 처리
// 관리자 웹은 AI 서버에 직접 접근하지 않고 메인 서버를 통해 AI/MLOps 서버 정보를 중계받음
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// AdminAiMonitoringService 비즈니스 로직 처리
public class AdminAiMonitoringService {

    private final AiModelMetricsRepository
            aiModelMetricsRepository; // AI 모델 성능 지표 데이터 베이스 접근용 Repository
    private final AiPipelineRepository aiPipelineRepository; // AI 파이프라인 상태 데이터 베이스 접근용 Repository
    private final AiRetrainHistoryRepository
            aiRetrainHistoryRepository; // AI 재학습 이력 데이터 베이스 접근용 Repository
    private final DiagnosisRepository diagnosisRepository; // 재학습 후보(학습 활용 동의 분석) 조회
    private final AiServiceClient aiServiceClient; // AI/MLOps 서버 연동 (health, retrain, jobs)
    private final AdminAuditService audit; // 지표 등록·삭제 감사 기록

    // 지표 키 형식 (소문자로 시작, 소문자·숫자·밑줄, 2~50자)
    private static final Pattern METRIC_KEY = Pattern.compile("^[a-z][a-z0-9_]{1,49}$");

    // AI 모델 Performance 지표 조회 (metric_key를 키로 하는 metrics_summary 객체)
    public Map<String, AiModelMetricResponseDto> getAiModelMetrics() {
        List<AiModelMetrics> metrics = aiModelMetricsRepository.findAll();
        Map<String, AiModelMetricResponseDto> summary = new LinkedHashMap<>();
        metrics.stream()
                .map(AiModelMetricResponseDto::fromEntity)
                .forEach(dto -> summary.put(dto.getMetricKey(), dto));
        return summary;
    }

    // AI 모델 성능 지표 등록·수정 (같은 키는 한 줄만 유지하고 이전 값과의 차이를 증감치로 기록)
    @Transactional
    public AiModelMetricResponseDto saveMetric(String metricKey, AiMetricRequestDto request) {
        if (metricKey == null || !METRIC_KEY.matcher(metricKey).matches()) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "지표 키는 영문 소문자로 시작하고 영문 소문자, 숫자, 밑줄만 쓸 수 있습니다. (2~50자)");
        }
        String unit = blankToNull(request.getUnit());
        if ("%".equals(unit) && request.getValue() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "단위가 %이면 지표 값은 100 이하여야 합니다.");
        }
        String description = blankToNull(request.getDescription());

        // 예전 데이터에 같은 키가 여러 줄이면 최신 한 줄만 남긴다
        List<AiModelMetrics> rows = aiModelMetricsRepository.findAllByMetricKey(metricKey);
        AiModelMetrics latest =
                rows.stream().max(Comparator.comparing(AiModelMetrics::getId)).orElse(null);
        rows.stream()
                .filter(row -> row != latest)
                .forEach(aiModelMetricsRepository::delete);

        String change =
                latest == null ? null : changeText(request.getValue() - latest.getMetricValue(), unit);
        AiModelMetrics saved;
        if (latest == null) {
            saved =
                    aiModelMetricsRepository.save(
                            AiModelMetrics.builder()
                                    .metricKey(metricKey)
                                    .metricValue(request.getValue())
                                    .unit(unit)
                                    .changeValue(change)
                                    .description(description)
                                    .build());
        } else {
            latest.update(request.getValue(), unit, change, description);
            saved = latest;
        }
        audit.current("AI_METRIC_SAVE", metricKey, request.getValue() + (unit == null ? "" : unit));
        return AiModelMetricResponseDto.fromEntity(saved);
    }

    // AI 모델 성능 지표 삭제 (없는 키는 404)
    @Transactional
    public void deleteMetric(String metricKey) {
        List<AiModelMetrics> rows = aiModelMetricsRepository.findAllByMetricKey(metricKey);
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND, "등록된 지표가 없습니다.");
        }
        aiModelMetricsRepository.deleteAll(rows);
        audit.current("AI_METRIC_DELETE", metricKey, rows.size() + "건");
    }

    // 이전 값과의 차이를 "+0.8%" 같은 문구로 만든다 (차이가 없으면 null)
    private static String changeText(double delta, String unit) {
        BigDecimal rounded = BigDecimal.valueOf(delta).setScale(2, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return null;
        }
        String number = rounded.stripTrailingZeros().toPlainString();
        String text = (rounded.signum() > 0 ? "+" : "") + number + (unit == null ? "" : unit);
        return text.length() > 20 ? text.substring(0, 20) : text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // AI 파이프라인 엔진별 상태 및 응답속도 조회
    public List<AiPipelineResponseDto> getAiPipelines() {
        List<AiPipeline> pipelines = aiPipelineRepository.findAll();
        return pipelines.stream()
                .map(AiPipelineResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    // MLOps 모델 재학습 이력 정보 조회 (페이지네이션)
    public Page<AiRetrainHistoryResponseDto> getAiRetrainHistory(int page, int size) {
        Pageable pageable = Paging.of(page, size);
        Page<AiRetrainHistory> historyPage =
                aiRetrainHistoryRepository.findAllByOrderByExecutedAtDesc(pageable);

        return historyPage.map(AiRetrainHistoryResponseDto::fromEntity);
    }

    // AI 서버 상태 조회 (모델 적재 여부, 모델/문항 뱅크/케어 카드 버전, 진행 중 추론 수)
    public AiHealthResponseDto getAiHealth() {
        return aiServiceClient.health();
    }

    // 재학습 후보 현황 조회 (학습 활용 동의(training_use=true) 분석만 후보, 의심 유형별 분포)
    public Map<String, Object> getRetrainCandidates() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : diagnosisRepository.countTrainingCandidatesByLabel()) {
            WoundType label = (WoundType) row[0];
            long count = (Long) row[1];
            distribution.put(label != null ? label.getCode() : "unlabeled", count);
            total += count;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_candidates", total);
        result.put("label_distribution", distribution);
        return result;
    }

    // 재학습 잡 등록 요청 (AI 서버에 queued 상태로 등록, 이미 진행 중인 잡이 있으면 MLOPS-002)
    @Transactional
    public AiRetrainHistoryResponseDto requestRetrain(String adminId, String reason) {
        AiJobResponseDto job = aiServiceClient.retrain(reason);
        if (job.jobId() == null) {
            throw new CustomException(ErrorCode.TRAINING_FAILED, "AI 서버가 재학습 잡 ID를 반환하지 않았습니다.");
        }

        // AI 서버는 진행 중인 잡이 있으면 기존 job_id를 반환하므로 이미 등록된 잡인지 확인
        if (aiRetrainHistoryRepository.existsById(job.jobId())) {
            log.warn("[MLOps] 재학습 중복 요청 - 이미 진행 중인 잡 (job_id: {})", job.jobId());
            throw new CustomException(
                    ErrorCode.RETRAIN_ALREADY_RUNNING,
                    "이미 진행 중인 재학습이 있습니다. (job_id: " + job.jobId() + ")");
        }

        AiRetrainHistory history =
                aiRetrainHistoryRepository.saveAndFlush(
                        AiRetrainHistory.builder()
                                .historyId(job.jobId())
                                .executedAt(LocalDateTime.now())
                                .triggerReason(reason)
                                .performanceChange("측정 전")
                                .status(job.status() != null ? job.status() : "queued")
                                .requestedBy(adminId)
                                .build());

        log.info(
                "[MLOps] 정상 처리, 재학습 잡 등록 (job_id: {}, status: {}, admin_id: {})",
                job.jobId(),
                history.getStatus(),
                adminId);
        return AiRetrainHistoryResponseDto.fromEntity(history);
    }

    // 재학습 잡 상태 조회 (AI 서버 최신 상태를 이력에 동기화)
    @Transactional
    public AiRetrainHistoryResponseDto getJob(String jobId) {
        AiRetrainHistory history = findHistory(jobId);
        AiJobResponseDto job = aiServiceClient.getJob(jobId);
        history.syncStatus(job.status(), job.message());

        if ("failed".equals(job.status())) {
            log.error("[MLOps] 서버 오류, 재학습 실패 (job_id: {}, message: {})", jobId, job.message());
        }
        return AiRetrainHistoryResponseDto.fromEntity(history);
    }

    // 신규 모델 배포 승인 (검증을 통과하여 awaiting_deploy 상태인 잡만 승인 가능)
    // ※ 설계서 7.4 AI API에 배포 실행 API가 정의되어 있지 않아, 승인 이력을 기록하고 AI 서버 배포 반영은 AI 담당 연동 필요
    @Transactional
    public AiRetrainHistoryResponseDto approveDeploy(String adminId, String jobId) {
        AiRetrainHistory history = findHistory(jobId);
        AiJobResponseDto job = aiServiceClient.getJob(jobId);
        history.syncStatus(job.status(), job.message());

        if (!AiRetrainHistory.STATUS_AWAITING_DEPLOY.equals(history.getStatus())) {
            throw new CustomException(
                    ErrorCode.MODEL_VALIDATION_FAILED,
                    "검증을 통과하여 배포 대기(awaiting_deploy) 중인 모델만 승인할 수 있습니다. (현재 상태: "
                            + history.getStatus()
                            + ")");
        }

        history.approveDeploy(adminId);
        log.info("[MLOps] 정상 처리, 신규 모델 배포 승인 (job_id: {}, admin_id: {})", jobId, adminId);
        return AiRetrainHistoryResponseDto.fromEntity(history);
    }

    // 재학습 이력 조회 (없으면 MLOPS-001)
    private AiRetrainHistory findHistory(String jobId) {
        return aiRetrainHistoryRepository
                .findById(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.RETRAIN_HISTORY_NOT_FOUND));
    }
}
