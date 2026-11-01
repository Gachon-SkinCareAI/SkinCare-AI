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
import skincare.dto.AiModelMetricResponseDto;
import skincare.dto.AiPipelineResponseDto;
import skincare.dto.AiRetrainHistoryResponseDto;
import skincare.dto.Paging;
import skincare.repository.AiModelMetricsRepository;
import skincare.repository.AiPipelineRepository;
import skincare.repository.AiRetrainHistoryRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 관리자 AI 모델 성능, 파이프라인, MLOps 재학습 이력 모니터링 관련 비즈니스 로직 처리
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAiMonitoringService {

    private final AiModelMetricsRepository aiModelMetricsRepository; // AI 모델 성능 지표 데이터 베이스 접근용 Repository
    private final AiPipelineRepository aiPipelineRepository; // AI 파이프라인 상태 데이터 베이스 접근용 Repository
    private final AiRetrainHistoryRepository aiRetrainHistoryRepository; // AI 재학습 이력 데이터 베이스 접근용 Repository

    // AI 모델 Performance 지표 조회 (metric_key를 키로 하는 metrics_summary 객체, 설계서 7.3)
    public Map<String, AiModelMetricResponseDto> getAiModelMetrics() {
        List<AiModelMetrics> metrics = aiModelMetricsRepository.findAll();
        Map<String, AiModelMetricResponseDto> summary = new LinkedHashMap<>();
        metrics.stream()
                .map(AiModelMetricResponseDto::fromEntity)
                .forEach(dto -> summary.put(dto.getMetricKey(), dto));
        return summary;
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
        Page<AiRetrainHistory> historyPage = aiRetrainHistoryRepository.findAllByOrderByExecutedAtDesc(pageable);

        return historyPage.map(AiRetrainHistoryResponseDto::fromEntity);
    }
}