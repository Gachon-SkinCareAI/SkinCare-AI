package skincare.external;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import skincare.external.DirectoryException.Failure;
import skincare.external.DirectoryException.Source;
import skincare.external.NmcDirectory.Place;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class NmcDirectoryTests {
    // ---- 테스트용 응답 만들기 ----

    static String item(String hpid, String name, String extra) {
        return "<item><dutyAddr>서울특별시 강남구  헌릉로571길 7</dutyAddr><dutyName>"
                + name
                + "</dutyName><dutyTel1>02-445-1460</dutyTel1><hpid>"
                + hpid
                + "</hpid><wgs84Lat>37.466</wgs84Lat><wgs84Lon>127.101</wgs84Lon>"
                + extra
                + "</item>";
    }

    static String page(long total, String items) {
        return "<response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header><body><items>"
                + items
                + "</items><numOfRows>2000</numOfRows><pageNo>1</pageNo><totalCount>"
                + total
                + "</totalCount></body></response>";
    }


    static PublicDataSupport.Page<Place> parsePage(String xml) {
        return NmcDirectory.parsePage(xml.getBytes(StandardCharsets.UTF_8), Source.NMC_PHARMACY);
    }

    /** 요일별 시간(1=월 ... 8=공휴일)을 채운 장소. times: {요일, 시작, 종료}. */
    static Place place(String... times) {
        String[] open = new String[9], close = new String[9];
        for (int i = 0; i < times.length; i += 3) {
            int day = Integer.parseInt(times[i]);
            open[day] = times[i + 1];
            close[day] = times[i + 2];
        }
        return new Place("C1", "약국", "서울 강남구 1", null, null, null, null, open, close);
    }

    static String weekdays(String start, String end) {
        StringBuilder xml = new StringBuilder();
        for (int day = 1; day <= 5; day++)
            xml.append("<dutyTime").append(day).append("c>").append(end).append("</dutyTime").append(day)
                    .append("c><dutyTime").append(day).append("s>").append(start).append("</dutyTime").append(day).append("s>");
        return xml.toString();
    }

    // ---- 파싱 ----

    @Test
    void parsesPharmacyFieldsAndNormalizesWhitespace() {
        var p = parsePage(page(1, item("C1109587", "  경희당 한약국 ", weekdays("0900", "2000") + "<dutyTime6s>0900</dutyTime6s><dutyTime6c>1400</dutyTime6c>")));
        assertEquals(1, p.total());
        var x = p.items().get(0);
        assertEquals("C1109587", x.hpid());
        assertEquals("경희당 한약국", x.name(), "앞뒤·중복 공백 정리");
        assertEquals("서울특별시 강남구 헌릉로571길 7", x.address());
        assertEquals("02-445-1460", x.tel());
        assertEquals(37.466, x.latitude());
        assertEquals(127.101, x.longitude());
        assertEquals("09:00 - 20:00", x.weekdayHours());
        assertEquals("09:00 - 14:00", x.saturdayHours());
        assertEquals("휴무", x.sundayHolidayHours(), "일요일·공휴일 정보가 없으면 휴무");
        assertTrue(x.nightCare());
        assertNull(x.emergency(), "응급실 정보가 없으면 null");
    }

    @Test
    void parsesTheEmergencyRoomFlagOfHospitals() {
        String xml = page(3, item("A1", "응급병원", "<dutyEryn>1</dutyEryn>") + item("A2", "일반병원", "<dutyEryn>2</dutyEryn>") + item("A3", "미상병원", "<dutyEryn>9</dutyEryn>"));
        var items = NmcDirectory.parsePage(xml.getBytes(StandardCharsets.UTF_8), Source.NMC_HOSPITAL).items();
        assertEquals(Boolean.TRUE, items.get(0).emergency());
        assertEquals(Boolean.FALSE, items.get(1).emergency());
        assertNull(items.get(2).emergency());
    }

    @Test
    void skipsItemsThatCannotBeIdentifiedOrShown() {
        var p = parsePage(page(3, item("C1", "정상", "") + "<item><dutyName>기호 없음</dutyName><dutyAddr>주소</dutyAddr></item>" + "<item><hpid>C3</hpid><dutyAddr>주소</dutyAddr></item>"));
        assertEquals(List.of("C1"), p.items().stream().map(Place::hpid).toList());
    }

    @Test
    void invalidCoordinatesAndTimesBecomeAbsentInsteadOfWrongData() {
        var p = parsePage(page(1, "<item><hpid>C1</hpid><dutyName>x</dutyName><dutyAddr>y</dutyAddr><wgs84Lat>NaN</wgs84Lat><wgs84Lon>181</wgs84Lon><dutyTime1s>9am</dutyTime1s><dutyTime1c>2500x</dutyTime1c></item>"));
        var x = p.items().get(0);
        assertNull(x.latitude());
        assertNull(x.longitude());
        assertNull(x.open()[1]);
        assertFalse(x.nightCare());
        assertEquals("휴무", x.weekdayHours());
    }

    @Test
    void providerErrorCodesAreMappedAndNeverLeakTheKeyOrBody() {
        var auth = assertThrows(DirectoryException.class, () -> parsePage("<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>secret-key</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"));
        assertEquals(Failure.AUTH_ERROR, auth.failure());
        assertEquals(Source.NMC_PHARMACY, auth.source());
        assertFalse(String.valueOf(auth.getMessage()).contains("secret-key"));
        assertEquals(Failure.RATE_LIMITED, assertThrows(DirectoryException.class, () -> parsePage("<response><header><resultCode>22</resultCode></header></response>")).failure());
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(DirectoryException.class, () -> parsePage("<html>wrong</html>")).failure());
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(DirectoryException.class, () -> parsePage("<response><header><resultCode>00</resultCode></header><body></body></response>")).failure(), "totalCount 없음");
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(DirectoryException.class, () -> parsePage("<!DOCTYPE x [<!ENTITY leak SYSTEM 'file:///never-read'>]><response>&leak;</response>")).failure());
    }

    // ---- 야간 진료(평일 종료 19시 이후) · 영업시간 ----

    @Test
    void nightCareMeansAWeekdayClosingAtOrAfterNineteen() {
        assertTrue(place("3", "0900", "1900").nightCare(), "정확히 19시");
        assertTrue(place("5", "0900", "2200").nightCare());
        assertFalse(place("1", "0900", "1830", "2", "0900", "1800").nightCare(), "19시 이전 종료");
        assertFalse(place("6", "0900", "2200", "7", "1000", "2300").nightCare(), "주말만 늦게 열면 야간진료 아님(기존 기준: 평일)");
        assertFalse(place().nightCare(), "시간 정보 없음");
    }

    @Test
    void overnightAndAllDayOpeningCountAsNight() {
        assertTrue(place("2", "0900", "0000").nightCare(), "자정에 닫음");
        assertTrue(place("2", "1800", "0200").nightCare(), "새벽 2시에 닫음");
        assertTrue(place("4", "0000", "2400").nightCare(), "24시간");
        assertTrue(place("4", "0000", "0000").nightCare(), "24시간(0000-0000 표기)");
    }

    @Test
    void hoursUseEarliestStartAndLatestEndAndKeepMidnightClosings() {
        var weekdays = place("1", "0930", "1800", "2", "0900", "2100", "3", "1000", "1700");
        assertEquals("09:00 - 21:00", weekdays.weekdayHours());
        assertEquals("09:00 - 00:00", place("1", "0900", "0000", "2", "0900", "2100").weekdayHours(), "자정 종료가 가장 늦은 종료");
        assertEquals("00:00 - 24:00", place("1", "0000", "2400").weekdayHours());
        assertEquals("18:00 - 01:00", place("1", "1800", "2500").weekdayHours(), "2500은 다음 날 01:00으로 표시");
        assertTrue(place("1", "1800", "2500").nightCare());
        assertEquals("10:00 - 15:00", place("8", "1000", "1500").sundayHolidayHours(), "일요일이 없으면 공휴일");
        assertEquals("11:00 - 12:00", place("7", "1100", "1200", "8", "1000", "1500").sundayHolidayHours(), "일요일 우선");
    }

    // ---- 요청 URL ----

    @Test
    void requestUsesTheKeyInEitherFormExactlyOnceAndFixesTheOrder() {
        for (String form : new String[] {"Ab+c/d==", "Ab%2Bc%2Fd%3D%3D", "\"Ab%2Bc%2Fd%3D%3D\""}) {
            String uri = new NmcDirectory(form).requestUri(NmcDirectory.Kind.PHARMACY, null, 3).toASCIIString();
            assertTrue(uri.contains("serviceKey=Ab%2Bc%2Fd%3D%3D&"), uri);
            assertFalse(uri.contains("%25"), "이중 인코딩: " + uri);
            assertTrue(uri.contains("getParmacyListInfoInqire"));
            assertTrue(uri.contains("pageNo=3"));
            assertTrue(uri.contains("numOfRows=2000"));
            assertTrue(uri.contains("ORD=NAME"));
            assertFalse(uri.contains("Q0="), "전국 조회는 시도를 지정하지 않는다");
        }
        String hospital = new NmcDirectory("k").requestUri(NmcDirectory.Kind.HOSPITAL, "서울특별시", 1).toASCIIString();
        assertTrue(hospital.contains("HsptlAsembySearchService/getHsptlMdcncListInfoInqire"), hospital);
        assertTrue(hospital.contains("Q0=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C"), hospital);
    }

    // ---- 전체 수집 (HTTP를 가짜로 대체) ----

    /** 페이지 번호별 응답을 돌려주는 가짜 디렉터리. 여러 스레드가 동시에 부르므로 기록은 스레드 안전해야 한다. */
    static class Fake extends NmcDirectory {
        final List<Integer> pagesRequested = new CopyOnWriteArrayList<>();
        final List<String> paths = new CopyOnWriteArrayList<>();
        final Function<Integer, byte[]> responder;
        final AtomicInteger calls = new AtomicInteger();
        Fake(Function<Integer, byte[]> responder) { super("test-key"); this.responder = responder; }
        @Override protected byte[] get(URI uri) throws IOException {
            calls.incrementAndGet();
            int pageNo = Integer.parseInt(uri.getRawQuery().replaceAll(".*pageNo=(\\d+).*", "$1"));
            pagesRequested.add(pageNo);
            paths.add(uri.getPath());
            return responder.apply(pageNo);
        }
        @Override protected void pause(long millis) { /* 테스트에서는 기다리지 않는다 */ }
    }

    static byte[] pageOf(int page, int total) {
        StringBuilder items = new StringBuilder();
        int from = (page - 1) * 2000, to = Math.min(total, page * 2000);
        for (int i = from; i < to; i++) items.append(item("C" + i, "약국" + i, ""));
        return page(total, items.toString()).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void fetchesEveryPageAndEndpointByKind() {
        var directory = new Fake(page -> pageOf(page, 4500));
        var all = directory.fetchPharmacies(null);
        assertEquals(4500, all.size());
        assertEquals(List.of(1, 2, 3), directory.pagesRequested.stream().sorted().toList(), "첫 페이지로 전체 건수를 안 뒤 나머지를 받는다");
        assertEquals(4500, all.stream().map(Place::hpid).distinct().count());
        assertTrue(directory.paths.stream().allMatch(p -> p.endsWith("getParmacyListInfoInqire")));

        var hospitals = new Fake(page -> pageOf(page, 10));
        assertEquals(10, hospitals.fetchHospitals("경기도").size());
        assertTrue(hospitals.paths.stream().allMatch(p -> p.endsWith("getHsptlMdcncListInfoInqire")));
    }

    @Test
    void retriesTransientFailuresButNotAuthenticationErrors() {
        AtomicInteger failures = new AtomicInteger(2);
        var flaky = new Fake(page -> {
            if (failures.getAndDecrement() > 0) throw new DirectoryException(Failure.UNAVAILABLE, Source.NMC_PHARMACY);
            return pageOf(page, 10);
        });
        assertEquals(10, flaky.fetchPharmacies(null).size());
        assertEquals(3, flaky.calls.get(), "두 번 실패 후 세 번째 성공");

        var down = new Fake(page -> { throw new DirectoryException(Failure.UNAVAILABLE, Source.NMC_PHARMACY); });
        assertEquals(Failure.UNAVAILABLE, assertThrows(DirectoryException.class, () -> down.fetchPharmacies(null)).failure());
        assertEquals(PublicDataSupport.ATTEMPTS, down.calls.get());

        var denied = new Fake(page -> "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>".getBytes(StandardCharsets.UTF_8));
        var error = assertThrows(DirectoryException.class, () -> denied.fetchHospitals(null));
        assertEquals(Failure.AUTH_ERROR, error.failure());
        assertEquals(Source.NMC_HOSPITAL, error.source(), "어느 API가 거부했는지 알린다");
        assertEquals(1, denied.calls.get(), "키 오류는 재시도하지 않는다");
    }

    @Test
    void aFailureOnALaterPageFailsTheWholeFetch() {
        var directory = new Fake(page -> {
            if (page == 3) throw new DirectoryException(Failure.RATE_LIMITED, Source.NMC_PHARMACY);
            return pageOf(page, 8000);
        });
        assertEquals(Failure.RATE_LIMITED, assertThrows(DirectoryException.class, () -> directory.fetchPharmacies(null)).failure());
    }

    @Test
    void refusesAPartialResultInsteadOfReturningIt() {
        // 총 4500건이라 했지만 모든 페이지가 같은 항목을 되풀이 → 2000건만 고유
        var repeating = new Fake(page -> pageOf(1, 4500));
        assertEquals(Failure.INCOMPLETE, assertThrows(DirectoryException.class, () -> repeating.fetchPharmacies(null)).failure());
        // 중간 페이지가 비어 온 경우
        var truncated = new Fake(page -> page == 1 ? pageOf(1, 4500) : page(4500, "").getBytes(StandardCharsets.UTF_8));
        assertEquals(Failure.INCOMPLETE, assertThrows(DirectoryException.class, () -> truncated.fetchPharmacies(null)).failure());
    }

    @Test
    void anUnconfiguredKeyIsReportedBeforeAnyRequest() {
        var directory = new NmcDirectory("\"\"") {
            @Override protected byte[] get(URI uri) { fail("키가 없으면 호출하지 않는다"); return null; }
        };
        assertFalse(directory.configured());
        assertEquals(Failure.UNCONFIGURED, assertThrows(DirectoryException.class, () -> directory.fetchPharmacies(null)).failure());
        assertEquals(Source.NMC_HOSPITAL, assertThrows(DirectoryException.class, () -> directory.fetchHospitals(null)).source());
    }
}
