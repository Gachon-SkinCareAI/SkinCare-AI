package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.ai.entity.AiModelMetrics;

// AI 모델 성능 지표(AiModelMetrics) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface AiModelMetricsRepository extends JpaRepository<AiModelMetrics, Long> {
}