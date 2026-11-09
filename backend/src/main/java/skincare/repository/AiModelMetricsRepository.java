package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import skincare.domain.ai.entity.AiModelMetrics;

import java.util.Optional;

// AI 모델 성능 지표(AiModelMetrics) 엔티티에 대한 데이터베이스 접근 인터페이스
// AiModelMetricsRepository 데이터 접근 처리
public interface AiModelMetricsRepository extends JpaRepository<AiModelMetrics, Long> {

    // 지표 키로 최신 지표 조회 (대시보드 AI 성능 카드, ex. macro_f1, burn_recall)
    Optional<AiModelMetrics> findFirstByMetricKeyOrderByIdDesc(String metricKey);
}
