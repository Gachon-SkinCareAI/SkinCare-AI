package skincare.external;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import skincare.external.DirectoryException.Failure;
import skincare.external.DirectoryException.Source;
import skincare.external.HiraHospitalDirectory.Facility;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class HiraHospitalDirectoryTests {
    static String item(String ykiho, String name, String extra) {
        return "<item><addr>서울특별시 종로구 새문안로 29, (평동)</addr><clCd>01</clCd><clCdNm>상급종합</clCdNm><sidoCd>110000</sidoCd>"
                + "<sidoCdNm>서울</sidoCdNm><sgguCd>110016</sgguCd><sgguCdNm>종로구</sgguCdNm><telno>02-2001-2001</telno>"
                + "<XPos>126.9677500</XPos><YPos>37.5684083</YPos><yadmNm>"
                + name
                + "</yadmNm><ykiho>"
                + ykiho
                + "</ykiho>"
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

    static PublicDataSupport.Page<Facility> parse(String xml) {
        return HiraHospitalDirectory.parsePage(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesFacilityFieldsAndMapsXPosToLongitudeAndYPosToLatitude() {
        var p = parse(page(1, item("JDQ4MTg4MSM1", "  강북삼성  병원 ", "")));
        assertEquals(1, p.total());
        Facility f = p.items().get(0);
        assertEquals("JDQ4MTg4MSM1", f.ykiho());
        assertEquals("강북삼성 병원", f.name(), "공백 정리");
        assertEquals("서울특별시 종로구 새문안로 29, (평동)", f.address());
        assertEquals("상급종합", f.className());
        assertEquals("서울", f.sido());
        assertEquals("종로구", f.sigungu());
        assertEquals("02-2001-2001", f.tel());
        assertEquals(37.5684083, f.latitude(), "YPos가 위도");
        assertEquals(126.96775, f.longitude(), "XPos가 경도");
    }

    @Test
    void decodesXmlEntitiesInNamesAndSkipsUnidentifiableItems() {
        var p = parse(page(3, item("K1", "A&amp;B의원", "") + "<item><yadmNm>기호 없음</yadmNm><addr>주소</addr></item><item><ykiho>K3</ykiho><addr>주소</addr></item>"));
        assertEquals(List.of("K1"), p.items().stream().map(Facility::ykiho).toList());
        assertEquals("A&B의원", p.items().get(0).name());
    }

    @Test
    void missingOptionalFieldsDoNotRejectTheFacility() {
        var p = parse(page(1, "<item><ykiho>K1</ykiho><yadmNm>이름</yadmNm><addr>주소</addr></item>"));
        Facility f = p.items().get(0);
        assertNull(f.tel());
        assertNull(f.latitude());
        assertNull(f.longitude());
        assertEquals("", f.sido());
    }

    @Test
    void providerErrorsAreMappedToTheHiraSource() {
        var denied = assertThrows(DirectoryException.class, () -> parse("<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>secret-key</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"));
        assertEquals(Failure.AUTH_ERROR, denied.failure());
        assertEquals(Source.HIRA_HOSPITAL, denied.source());
        assertFalse(String.valueOf(denied.getMessage()).contains("secret-key"));
        assertEquals(Failure.INVALID_RESPONSE, assertThrows(DirectoryException.class, () -> parse("<html>wrong</html>")).failure());
    }

    @Test
    void requestFiltersByDermatologyAndUsesTheKeyOnce() {
        for (String form : new String[] {"Ab+c/d==", "Ab%2Bc%2Fd%3D%3D"}) {
            String uri = new HiraHospitalDirectory(form).requestUri(null, 2).toASCIIString();
            assertTrue(uri.contains("hospInfoServicev2/getHospBasisList"), uri);
            assertTrue(uri.contains("serviceKey=Ab%2Bc%2Fd%3D%3D&"), uri);
            assertFalse(uri.contains("%25"));
            assertTrue(uri.contains("dgsbjtCd=14"), "피부과 진료과목");
            assertTrue(uri.contains("pageNo=2"));
            assertTrue(uri.contains("numOfRows=2000"));
            assertFalse(uri.contains("sidoCd="), "전국 조회는 시도를 지정하지 않는다");
        }
        assertTrue(new HiraHospitalDirectory("k").requestUri("110000", 1).toASCIIString().contains("sidoCd=110000"));
    }

    // ---- 전체 수집 (HTTP를 가짜로 대체) ----

    static class Fake extends HiraHospitalDirectory {
        final List<Integer> pages = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();
        final Function<Integer, byte[]> responder;
        Fake(Function<Integer, byte[]> responder) { super("test-key"); this.responder = responder; }
        @Override protected byte[] get(URI uri) throws IOException {
            calls.incrementAndGet();
            int pageNo = Integer.parseInt(uri.getRawQuery().replaceAll(".*pageNo=(\\d+).*", "$1"));
            pages.add(pageNo);
            return responder.apply(pageNo);
        }
        @Override protected void pause(long millis) { }
    }

    static byte[] pageOf(int page, int total) {
        StringBuilder items = new StringBuilder();
        int from = (page - 1) * 2000, to = Math.min(total, page * 2000);
        for (int i = from; i < to; i++) items.append(item("K" + i, "의원" + i, ""));
        return page(total, items.toString()).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void fetchesEveryPage() {
        var directory = new Fake(page -> pageOf(page, 4500));
        var all = directory.fetchDermatology(null);
        assertEquals(4500, all.size());
        assertEquals(List.of(1, 2, 3), directory.pages.stream().sorted().toList());
    }

    @Test
    void retriesTransientFailuresButNotAuthErrorsAndRefusesPartialResults() {
        AtomicInteger failures = new AtomicInteger(1);
        var flaky = new Fake(page -> {
            if (failures.getAndDecrement() > 0) throw new DirectoryException(Failure.UNAVAILABLE, Source.HIRA_HOSPITAL);
            return pageOf(page, 10);
        });
        assertEquals(10, flaky.fetchDermatology(null).size());

        var denied = new Fake(page -> "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>".getBytes(StandardCharsets.UTF_8));
        assertEquals(Failure.AUTH_ERROR, assertThrows(DirectoryException.class, () -> denied.fetchDermatology(null)).failure());
        assertEquals(1, denied.calls.get());

        var repeating = new Fake(page -> pageOf(1, 4500));
        assertEquals(Failure.INCOMPLETE, assertThrows(DirectoryException.class, () -> repeating.fetchDermatology(null)).failure());
    }

    @Test
    void anUnconfiguredKeyIsReportedBeforeAnyRequest() {
        var directory = new HiraHospitalDirectory("") {
            @Override protected byte[] get(URI uri) { fail("키가 없으면 호출하지 않는다"); return null; }
        };
        var error = assertThrows(DirectoryException.class, () -> directory.fetchDermatology(null));
        assertEquals(Failure.UNCONFIGURED, error.failure());
        assertEquals(Source.HIRA_HOSPITAL, error.source());
    }
}
