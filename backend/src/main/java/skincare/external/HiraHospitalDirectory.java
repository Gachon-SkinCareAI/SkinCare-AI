package skincare.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import skincare.external.DirectoryException.Failure;
import skincare.external.DirectoryException.Source;
import skincare.external.PublicDataSupport.Page;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 건강보험심사평가원 "병원정보서비스"에서 <b>피부과 진료과목을 가진</b> 모든 의료기관을 내려받는다 (관리자 동기화용).
 *
 * <p>진료과목 코드 14(피부과)로 걸러 전국 약 1.8만 곳(상급종합·종합병원·병원·의원·요양병원 등)을 받는다. 이 API는 진료시간과 응급실
 * 정보를 주지 않으므로 {@link NmcDirectory}의 병·의원 목록과 이름·좌표로 결합해 보완한다. 인증키·URL·응답 본문은 로그나 예외
 * 메시지에 남기지 않는다.
 */
@Component
public class HiraHospitalDirectory {
    public static final String SOURCE_NAME = "건강보험심사평가원·국립중앙의료원"; // 진료시간·응급실 정보까지 결합한 경우
    public static final String SOURCE_NAME_HIRA_ONLY = "건강보험심사평가원"; // 국립중앙의료원 정보와 결합하지 못한 경우
    public static final String SOURCE_URL = "https://www.data.go.kr/data/15001698/openapi.do";

    /** 진료과목 코드: 14 = 피부과. */
    static final String DERMATOLOGY = "14";

    static final int PAGE_SIZE = 2000; // 3,000건에 약 15초 걸려 2,000건씩 받는다
    static final int CONCURRENCY = 5;
    private static final String ENDPOINT = "https://apis.data.go.kr/B551182/hospInfoServicev2/getHospBasisList";

    /** 의료기관 한 곳. ykiho는 80자 암호화 요양기관 기호(고유 식별자). */
    public record Facility(
            String ykiho,
            String name,
            String address,
            String className,
            String sido,
            String sigungu,
            String tel,
            Double latitude,
            Double longitude) {}

    private final String key;

    public HiraHospitalDirectory(@Value("${skincare.medical.service-key:}") String key) {
        this.key = ServiceKeys.decoded(key);
    }

    public boolean configured() {
        return !key.isBlank();
    }

    /**
     * 피부과 진료과목을 가진 의료기관을 모두 수집한다.
     *
     * @param sidoCode 건강보험심사평가원 시도코드(예: 110000). null이면 전국
     */
    public List<Facility> fetchDermatology(String sidoCode) {
        if (!configured()) throw new DirectoryException(Failure.UNCONFIGURED, Source.HIRA_HOSPITAL);
        return PublicDataSupport.fetchAll(
                Source.HIRA_HOSPITAL,
                PAGE_SIZE,
                CONCURRENCY,
                page -> parsePage(get(requestUri(sidoCode, page))),
                Facility::ykiho,
                this::pause);
    }

    URI requestUri(String sidoCode, int page) {
        var params = new LinkedHashMap<String, String>();
        params.put("serviceKey", ServiceKeys.decoded(key));
        params.put("dgsbjtCd", DERMATOLOGY);
        if (sidoCode != null && !sidoCode.isBlank()) params.put("sidoCd", sidoCode);
        params.put("pageNo", String.valueOf(page));
        params.put("numOfRows", String.valueOf(PAGE_SIZE));
        String query =
                params.entrySet().stream()
                        .map(
                                e ->
                                        e.getKey()
                                                + "="
                                                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
        return URI.create(ENDPOINT + "?" + query);
    }

    /** 테스트에서 대체할 수 있도록 분리한 HTTP 호출. 이 API는 느려서(2,000건에 약 10초) 읽기 제한을 길게 둔다. */
    protected byte[] get(URI uri) throws IOException {
        return PublicDataSupport.httpGet(uri, 90000, Source.HIRA_HOSPITAL);
    }

    protected void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static Page<Facility> parsePage(byte[] bytes) {
        Source source = Source.HIRA_HOSPITAL;
        try {
            Element root = PublicDataSupport.parseRoot(bytes, source);
            long total = PublicDataSupport.totalCount(root, source);
            var nodes = root.getElementsByTagName("item");
            var items = new ArrayList<Facility>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                var e = (Element) nodes.item(i);
                String ykiho = PublicDataSupport.text(e, "ykiho"),
                        name = PublicDataSupport.collapse(PublicDataSupport.text(e, "yadmNm")),
                        address = PublicDataSupport.collapse(PublicDataSupport.text(e, "addr"));
                if (ykiho == null || name == null || address == null) continue; // 식별·표시할 수 없는 항목은 건너뛴다
                items.add(
                        new Facility(
                                ykiho,
                                name,
                                address,
                                Objects.toString(PublicDataSupport.text(e, "clCdNm"), ""),
                                Objects.toString(PublicDataSupport.text(e, "sidoCdNm"), ""),
                                Objects.toString(PublicDataSupport.text(e, "sgguCdNm"), ""),
                                PublicDataSupport.text(e, "telno"),
                                PublicDataSupport.coordinate(PublicDataSupport.text(e, "YPos"), 90),
                                PublicDataSupport.coordinate(PublicDataSupport.text(e, "XPos"), 180)));
            }
            return new Page<>(total, items);
        } catch (DirectoryException known) {
            throw known;
        } catch (Exception malformed) {
            throw new DirectoryException(Failure.INVALID_RESPONSE, source);
        }
    }
}
