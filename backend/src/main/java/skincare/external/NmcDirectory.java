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
 * 국립중앙의료원 공공 API에서 전국 약국·병의원 목록을 모두 내려받는다 (관리자 동기화용).
 *
 * <ul>
 *   <li>약국: "전국 약국 정보 조회 서비스" — 약국명, 주소, 요일별 영업시간
 *   <li>병의원: "전국 병·의원 찾기 서비스" — 요일별 진료시간, 응급실 운영 여부(dutyEryn)
 * </ul>
 *
 * 사용자 요청마다 호출하는 {@link NmcMedicalClient}와 달리 한 번에 수만 건을 페이지 단위로 수집한다. 수집이 중간에 끊기거나 일부만
 * 받아오면 예외로 알려, 호출한 동기화가 "목록에서 사라진 기관"을 잘못 중지하거나 진료시간을 비우지 않게 한다. 인증키·URL·응답 본문은
 * 로그나 예외 메시지에 남기지 않는다.
 */
@Component
public class NmcDirectory {
    /** 약국 출처 (사용자 검색 노출 조건인 출처명·URL). */
    public static final String PHARMACY_SOURCE_NAME = "국립중앙의료원 약국정보";

    public static final String PHARMACY_SOURCE_URL = "https://www.data.go.kr/data/15000576/openapi.do";

    static final int PAGE_SIZE = 2000; // 약국 전국 약 2.5만 건, 병의원 약 7.9만 건 (페이지당 약 1.5MB)
    static final int CONCURRENCY = 3;
    private static final String BASE = "https://apis.data.go.kr/B552657/";

    public enum Kind {
        PHARMACY("ErmctInsttInfoInqireService/getParmacyListInfoInqire", Source.NMC_PHARMACY),
        HOSPITAL("HsptlAsembySearchService/getHsptlMdcncListInfoInqire", Source.NMC_HOSPITAL);

        private final String path;
        private final Source source;

        Kind(String path, Source source) {
            this.path = path;
            this.source = source;
        }
    }

    /**
     * 약국 또는 병의원 한 곳. open/close는 인덱스 1(월)~8(공휴일)에 "0900" 같은 4자리 시각이 들어 있고 정보가 없으면 null이다.
     * emergency는 응급실 운영 여부로, 값이 없으면 null이다.
     */
    public record Place(
            String hpid,
            String name,
            String address,
            String tel,
            Double latitude,
            Double longitude,
            Boolean emergency,
            String[] open,
            String[] close) {

        /** 평일(월~금) 중 하루라도 19시 이후까지 영업하면 야간 운영. 자정을 넘겨 닫는 경우(종료가 시작보다 이름)와 24시간 영업도 포함. */
        public boolean nightCare() {
            for (int day = 1; day <= 5; day++) {
                int start = hhmm(open[day]), end = hhmm(close[day]);
                if (start < 0 || end < 0) continue;
                if (end >= 1900 || end < start || (end == start && end == 0)) return true;
            }
            return false;
        }

        /** 평일 중 가장 이른 시작 ~ 가장 늦은 종료. 정보가 없으면 "휴무". */
        public String weekdayHours() {
            return hours(1, 5);
        }

        public String saturdayHours() {
            return hours(6, 6);
        }

        /** 일요일 시간, 없으면 공휴일 시간. */
        public String sundayHolidayHours() {
            String sunday = hours(7, 7);
            return "휴무".equals(sunday) ? hours(8, 8) : sunday;
        }

        private String hours(int from, int to) {
            int earliest = Integer.MAX_VALUE, latest = -1, latestShown = -1;
            for (int day = from; day <= to; day++) {
                int start = hhmm(open[day]), end = hhmm(close[day]);
                if (start < 0 || end < 0) continue;
                int effectiveEnd = end < start ? end + 2400 : end; // 자정을 넘기면 가장 늦은 종료로 본다
                earliest = Math.min(earliest, start);
                if (effectiveEnd > latest) {
                    latest = effectiveEnd;
                    latestShown = end;
                }
            }
            if (latest < 0) return "휴무";
            // 공공데이터는 자정 이후 종료를 2500(= 다음 날 01:00)처럼 적기도 한다. 표시는 24시간 시각으로 정규화한다.
            int shown = latestShown > 2400 ? latestShown - 2400 : latestShown;
            return String.format(
                    "%02d:%02d - %02d:%02d",
                    earliest / 100, earliest % 100, shown / 100, shown % 100);
        }
    }

    private final String key;

    public NmcDirectory(@Value("${skincare.medical.service-key:}") String key) {
        this.key = ServiceKeys.decoded(key); // 인코딩/디코딩 어느 형태로 설정되어도 requestUri가 한 번만 인코딩한다
    }

    /** 키가 설정되어 있는지 (동기화 전에 미리 안내하기 위함). */
    public boolean configured() {
        return !key.isBlank();
    }

    /** 전국(또는 한 시도)의 약국 목록. */
    public List<Place> fetchPharmacies(String sido) {
        return fetchAll(Kind.PHARMACY, sido);
    }

    /** 전국(또는 한 시도)의 병·의원 목록. 치과·한의원 등도 포함되며 이름·좌표로 결합해 쓴다. */
    public List<Place> fetchHospitals(String sido) {
        return fetchAll(Kind.HOSPITAL, sido);
    }

    private List<Place> fetchAll(Kind kind, String sido) {
        if (!configured()) throw new DirectoryException(Failure.UNCONFIGURED, kind.source);
        return PublicDataSupport.fetchAll(
                kind.source,
                PAGE_SIZE,
                CONCURRENCY,
                page -> parsePage(get(requestUri(kind, sido, page)), kind.source),
                Place::hpid,
                this::pause);
    }

    URI requestUri(Kind kind, String sido, int page) {
        var params = new LinkedHashMap<String, String>();
        params.put("serviceKey", ServiceKeys.decoded(key));
        if (sido != null && !sido.isBlank()) params.put("Q0", sido);
        params.put("pageNo", String.valueOf(page));
        params.put("numOfRows", String.valueOf(PAGE_SIZE));
        params.put("ORD", "NAME"); // 페이지 사이에 순서가 바뀌어 항목이 빠지거나 겹치지 않게 고정
        String query =
                params.entrySet().stream()
                        .map(
                                e ->
                                        e.getKey()
                                                + "="
                                                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
        return URI.create(BASE + kind.path + "?" + query);
    }

    /** 테스트에서 대체할 수 있도록 분리한 HTTP 호출. */
    protected byte[] get(URI uri) throws IOException {
        Source source = uri.getPath().contains("Hsptl") ? Source.NMC_HOSPITAL : Source.NMC_PHARMACY;
        return PublicDataSupport.httpGet(uri, 30000, source);
    }

    /** 재시도 사이의 대기. 테스트에서 대체한다. */
    protected void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static Page<Place> parsePage(byte[] bytes, Source source) {
        try {
            Element root = PublicDataSupport.parseRoot(bytes, source);
            long total = PublicDataSupport.totalCount(root, source);
            var nodes = root.getElementsByTagName("item");
            var items = new ArrayList<Place>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                var e = (Element) nodes.item(i);
                String hpid = PublicDataSupport.text(e, "hpid"),
                        name = PublicDataSupport.collapse(PublicDataSupport.text(e, "dutyName")),
                        address = PublicDataSupport.collapse(PublicDataSupport.text(e, "dutyAddr"));
                if (hpid == null || name == null || address == null) continue; // 식별·표시할 수 없는 항목은 건너뛴다
                String[] open = new String[9], close = new String[9];
                for (int day = 1; day <= 8; day++) {
                    open[day] = time(PublicDataSupport.text(e, "dutyTime" + day + "s"));
                    close[day] = time(PublicDataSupport.text(e, "dutyTime" + day + "c"));
                }
                String eryn = PublicDataSupport.text(e, "dutyEryn");
                items.add(
                        new Place(
                                hpid,
                                name,
                                address,
                                PublicDataSupport.text(e, "dutyTel1"),
                                PublicDataSupport.coordinate(PublicDataSupport.text(e, "wgs84Lat"), 90),
                                PublicDataSupport.coordinate(PublicDataSupport.text(e, "wgs84Lon"), 180),
                                "1".equals(eryn) ? Boolean.TRUE : "2".equals(eryn) ? Boolean.FALSE : null,
                                open,
                                close));
            }
            return new Page<>(total, items);
        } catch (DirectoryException known) {
            throw known;
        } catch (Exception malformed) {
            throw new DirectoryException(Failure.INVALID_RESPONSE, source);
        }
    }

    /** 4자리 숫자(HHmm)만 허용한다. */
    static String time(String value) {
        return value != null && value.matches("\\d{4}") ? value : null;
    }

    /** "0900" → 900, 값이 없거나 형식 오류면 -1. */
    static int hhmm(String value) {
        return value == null ? -1 : Integer.parseInt(value);
    }
}
