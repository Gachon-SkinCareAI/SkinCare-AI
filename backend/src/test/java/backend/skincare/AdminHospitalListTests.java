package backend.skincare;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import skincare.domain.admin.entity.Admin;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 관리자 의료기관 목록: 동기화로 수만 건이 쌓여도 서버가 이름순으로 페이지·검색·필터를 처리하고, 화면 카드용 전체 집계를 준다.
 *
 * <p>회귀 방지 대상: 목록이 확인일·ID 내림차순이라 ID 접두어가 큰 약국(NMC-)만 첫 페이지에 몰려 보였던 문제.
 */
class AdminHospitalListTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired HospitalRepository hospitals;
    @Autowired AdminRepository admins;
    @Autowired JwtTokenProvider jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    MockMvc mvc;
    String admin;

    static final int TOTAL = 130;
    static final HospitalType[] TYPES = {HospitalType.PHARMACY, HospitalType.DERMATOLOGY, HospitalType.GENERAL_HOSPITAL, HospitalType.OTHER};

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        hospitals.deleteAll();
        var a =
                admins.findById("list-admin")
                        .orElseGet(() -> Admin.builder().adminId("list-admin").name("목록 검증 관리자").password(encoder.encode("ServiceTest123!")).build());
        admins.saveAndFlush(a);
        admin = "Bearer " + jwt.createToken(a.getAdminId(), "ADMIN", a.currentTokenVersion());
        // 실제 동기화와 같은 모양: 약국은 NMC-, 병원은 HIRA- ID, 모두 같은 날 확인. 이름순과 ID순이 다르게 섞는다.
        var rows = new ArrayList<Hospital>();
        for (int i = 0; i < TOTAL; i++) {
            HospitalType type = TYPES[i % 4];
            rows.add(
                    Hospital.builder()
                            .hospitalId((type == HospitalType.PHARMACY ? "NMC-" : "HIRA-") + String.format("%04d", i))
                            .name(String.format("기관%03d", i))
                            .hospitalType(type)
                            .region(i % 5 == 0 ? "서울 강남구" : "부산 해운대구")
                            .address("주소 " + i + "번지")
                            .nightCare(i % 3 == 0)
                            .emergency(i % 13 == 0)
                            .status(i % 10 == 0 ? "INACTIVE" : "ACTIVE")
                            .dataSource("테스트")
                            .sourceUrl("https://www.data.go.kr/")
                            .verifiedOn(LocalDate.of(2026, 11, 10))
                            .build());
        }
        hospitals.saveAllAndFlush(rows);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> list(String query, int expected) throws Exception {
        var request = get("/api/wc/admin/hospitals").header("Authorization", admin);
        // 쿼리 문자열을 직접 이어 붙이면 템플릿 인코딩이 한 번 더 일어나므로 값은 파라미터로 전달한다
        if (!query.isEmpty()) for (String pair : query.split("&")) request.param(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
        var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        String text = result.getResponse().getContentAsString();
        return text.isBlank() ? Map.of() : json.readValue(text, Map.class);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    @Test
    void defaultListIsSortedByNameSoTypesAreMixedOnTheFirstPage() throws Exception {
        var page = list("page=0&size=50", 200);
        assertEquals(TOTAL, ((Number) page.get("total")).intValue(), "전체 건수");
        assertEquals(true, page.get("hasNext"));
        var names = items(page).stream().map(i -> (String) i.get("name")).toList();
        assertEquals(50, names.size());
        assertEquals(names.stream().sorted().toList(), names, "이름 오름차순");
        assertEquals("기관000", names.get(0));
        var types = items(page).stream().map(i -> (String) i.get("type")).collect(Collectors.toSet());
        assertEquals(Set.of("PHARMACY", "DERMATOLOGY", "GENERAL_HOSPITAL", "OTHER"), types, "첫 페이지에 한 종류만 몰리면 안 된다");
    }

    @Test
    void pagesCoverEveryRecordExactlyOnce() throws Exception {
        var ids = new ArrayList<String>();
        for (int p = 0; p < 3; p++) items(list("page=" + p + "&size=50", 200)).forEach(i -> ids.add((String) i.get("id")));
        assertEquals(TOTAL, ids.size());
        assertEquals(TOTAL, new HashSet<>(ids).size(), "중복 없이 모두");
        assertEquals(false, list("page=2&size=50", 200).get("hasNext"));
    }

    @Test
    void typeFilterAndSearchAreAppliedOnTheServerAcrossAllRecords() throws Exception {
        var derm = list("type=DERMATOLOGY&size=50", 200);
        assertEquals(33, ((Number) derm.get("total")).intValue(), "피부과(i%4==1) 33건");
        assertTrue(items(derm).stream().allMatch(i -> "DERMATOLOGY".equals(i.get("type"))));

        // 첫 50건 밖에 있는 기관도 이름으로 찾는다
        var far = list("q=기관129", 200);
        assertEquals(1, ((Number) far.get("total")).intValue());
        assertEquals("기관129", items(far).get(0).get("name"));
        // 지역과 주소로도 검색된다
        assertEquals(26, ((Number) list("q=강남구&size=50", 200).get("total")).intValue(), "지역(i%5==0) 26건");
        assertEquals(1, ((Number) list("q=주소 77번지", 200).get("total")).intValue());
        // 종류와 검색을 함께 쓴다
        assertEquals(7, ((Number) list("type=PHARMACY&q=강남구&size=50", 200).get("total")).intValue());
    }

    @Test
    void statusFilterSeparatesRunningFromStoppedOrHiddenRecords() throws Exception {
        assertEquals(13, ((Number) list("status=INACTIVE&size=50", 200).get("total")).intValue(), "i%10==0 → 13건");
        assertEquals(117, ((Number) list("status=ACTIVE&size=50", 200).get("total")).intValue());
        assertEquals(13, ((Number) list("status=inactive&size=50", 200).get("total")).intValue(), "소문자도 허용");
        assertTrue(items(list("status=INACTIVE&size=50", 200)).stream().allMatch(i -> "INACTIVE".equals(i.get("status"))));
        // 상태 미지정이면 중지·숨김도 포함한 전체
        assertEquals(TOTAL, ((Number) list("", 200).get("total")).intValue());
        list("status=BROKEN", 400);
        // 종류와 상태를 함께 쓴다 (i%10==0 이면서 i%4==0 → i%20==0 → 7건, 약국)
        var both = list("type=PHARMACY&status=INACTIVE&size=50", 200);
        assertEquals(7, ((Number) both.get("total")).intValue());
    }

    @Test
    void pageSizeIsBoundedAndInvalidInputIsRejected() throws Exception {
        list("size=50", 200);
        list("size=51", 400);
        list("size=0", 400);
        list("page=-1", 400);
        list("type=UNKNOWN", 400);
    }

    @Test
    void dashboardGivesTheWholeCountsTheFacilityCardsNeed() throws Exception {
        var result = mvc.perform(get("/api/wc/admin/dashboard").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> dashboard = json.readValue(result.getResponse().getContentAsString(), Map.class);
        int active = 0, night = 0, emergency = 0, pharmacies = 0;
        for (int i = 0; i < TOTAL; i++) {
            if (i % 10 == 0) continue; // INACTIVE
            active++;
            if (i % 3 == 0) night++;
            if (i % 13 == 0) emergency++;
            if (i % 4 == 0) pharmacies++;
        }
        assertEquals(active, ((Number) dashboard.get("activeHospitals")).intValue());
        assertEquals(TOTAL - active, ((Number) dashboard.get("inactiveHospitals")).intValue());
        assertEquals(night, ((Number) dashboard.get("activeNightCare")).intValue());
        assertEquals(emergency, ((Number) dashboard.get("activeEmergency")).intValue());
        assertEquals(pharmacies, ((Number) dashboard.get("activePharmacies")).intValue());
    }

    @Test
    void onlyAdministratorsCanListHospitalsThisWay() throws Exception {
        mvc.perform(get("/api/wc/admin/hospitals")).andExpect(status().isUnauthorized());
    }
}
