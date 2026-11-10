package skincare.external;

/**
 * 공공데이터 API 수집 실패. 인증키·URL·응답 본문은 담지 않는다 (원인 분류와 어느 API인지만 알린다).
 *
 * <p>동기화는 이 예외가 하나라도 나면 전체를 중단한다. 일부 API만 실패한 채 진행하면 진료시간·응급실 정보가 비워진 채 수만 건이
 * 저장될 수 있기 때문이다.
 */
public class DirectoryException extends RuntimeException {
    /** 수집 실패 원인. */
    public enum Failure {
        UNCONFIGURED,
        AUTH_ERROR,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_RESPONSE,
        INCOMPLETE
    }

    /** 실패한 API. 사용자에게 어느 서비스의 문제인지 알리는 데 쓴다. */
    public enum Source {
        NMC_PHARMACY("국립중앙의료원 약국", "전국 약국 정보 조회 서비스"),
        NMC_HOSPITAL("국립중앙의료원 병·의원", "전국 병·의원 찾기 서비스"),
        HIRA_HOSPITAL("건강보험심사평가원 병원", "병원정보서비스");

        private final String label;
        private final String service;

        Source(String label, String service) {
            this.label = label;
            this.service = service;
        }

        public String label() {
            return label;
        }

        /** 데이터포털에서 활용신청해야 하는 서비스 이름. */
        public String service() {
            return service;
        }
    }

    private final Failure failure;
    private final Source source;

    public DirectoryException(Failure failure, Source source) {
        super(source + ":" + failure); // 키·URL·응답 본문은 담지 않는다
        this.failure = failure;
        this.source = source;
    }

    public Failure failure() {
        return failure;
    }

    public Source source() {
        return source;
    }
}
