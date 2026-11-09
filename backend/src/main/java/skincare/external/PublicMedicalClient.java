package skincare.external;

import java.time.Instant;
import java.util.List;

// PublicMedicalClient 외부 서비스 연동
public interface PublicMedicalClient {
    enum Kind {
        HOSPITAL,
        PHARMACY
    }

    record Query(
            Kind kind,
            String sido,
            String sigungu,
            String name,
            int page,
            int size,
            Double latitude,
            Double longitude) {
        public Query(Kind kind, String sido, String sigungu, String name, int page, int size) {
            this(kind, sido, sigungu, name, page, size, null, null);
        }

        public boolean nearby() {
            return latitude != null && longitude != null;
        }
    }

    record Place(
            String id,
            String name,
            String address,
            String tel,
            String category,
            Double latitude,
            Double longitude,
            Boolean emergency) {}

    record Result(
            String status,
            List<Place> items,
            int page,
            int size,
            boolean hasNext,
            Long total,
            String sourceUrl,
            Instant fetchedAt) {}

    Result search(Query query);
}
