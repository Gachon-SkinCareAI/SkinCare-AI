package skincare.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.ai.entity.AiRetrainHistory;

import java.util.List;

// AI 재학습 이력(AiRetrainHistory) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface AiRetrainHistoryRepository extends JpaRepository<AiRetrainHistory, String> {

    // 재학습 이력 최신 실행일자순 정렬 조회
    List<AiRetrainHistory> findAllByOrderByExecutedAtDesc();

    // 재학습 이력 최신 실행일자순 정렬 조회 (페이지네이션)
    Page<AiRetrainHistory> findAllByOrderByExecutedAtDesc(Pageable pageable);
}