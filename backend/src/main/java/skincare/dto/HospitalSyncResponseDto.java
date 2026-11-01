package skincare.dto;

import lombok.Builder;
import lombok.Getter;

// 관리자 공공데이터 의료기관 동기화 결과 응답 DTO (POST /api/wc/admin/sync)
@Getter
@Builder
public class HospitalSyncResponseDto {
    private String syncId;          // 동기화 작업 ID (ex. SYNC-20261005-172915)
    private String targetScope;     // 동기화 대상 스코프
    private String executedAt;      // 동기화 실행 일시
    private double durationSeconds; // 동기화 소요 시간 (초)
    private SyncMetrics metrics;    // 동기화 건수 통계
    private String status;          // 동기화 처리 상태 (COMPLETED 등)

    // 동기화 건수 통계 정보
    @Getter
    @Builder
    public static class SyncMetrics {
        private int totalFetched; // 전체 수집 건수
        private int newAdded;     // 신규 추가 건수
        private int updated;      // 정보 갱신 건수
        private int deactivated;  // 비활성(폐업) 처리 건수
    }
}