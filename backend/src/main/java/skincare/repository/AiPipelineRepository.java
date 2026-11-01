package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.ai.entity.AiPipeline;

// AI 파이프라인(AiPipeline) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface AiPipelineRepository extends JpaRepository<AiPipeline, Long> {
}