package skincare.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HospitalSyncResponseDto {
    private String syncId;
    private String targetScope;
    private String executedAt;
    private double durationSeconds;
    private SyncMetrics metrics;
    private String status;

    @Getter
    @Builder
    public static class SyncMetrics {
        private int totalFetched;
        private int newAdded;
        private int updated;
        private int deactivated;
    }
}