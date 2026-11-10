package backend.skincare;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import skincare.controller.AdminHospitalController;
import skincare.domain.admin.entity.Admin;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;
import skincare.dto.HospitalSyncRequestDto;
import skincare.dto.HospitalSyncResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.DirectoryException;
import skincare.external.DirectoryException.Failure;
import skincare.external.DirectoryException.Source;
import skincare.external.HiraHospitalDirectory;
import skincare.external.HiraHospitalDirectory.Facility;
import skincare.external.NmcDirectory;
import skincare.external.NmcDirectory.Place;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;
import skincare.service.AdminAuditService;
import skincare.service.HospitalService;
import skincare.service.HospitalSyncService;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;

/**
 * 공공데이터 의료기관 동기화의 운영 규칙 검증.
 *
 * <p>병원은 건강보험심사평가원(피부과) + 국립중앙의료원(진료시간·응급실), 약국은 국립중앙의료원 API에서 받아오므로 서비스 테스트에는
 * 가짜 수집기를 직접 주입해 실제 네트워크를 쓰지 않는다. (테스트 환경이 .env의 실제 인증키를 읽을 수 있어 HTTP 경로로는 동기화를
 * 실행하지 않는다.)
 */
class HospitalSyncTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired HospitalRepository hospitals;
    @Autowired AdminRepository admins;
    @Autowired JwtTokenProvider jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    MockMvc mvc;
    String admin;
    StubNmc nmc;
    StubHira hira;
    HospitalSyncService service;

    /** 외부 API 대신 정해 둔 목록 또는 실패를 돌려주는 국립중앙의료원 수집기. 세 수집이 동시에 실행되므로 기록은 스레드 안전해야 한다. */
    static class StubNmc extends NmcDirectory {
        List<Place> pharmacies = List.of(), hospitals = List.of();
        DirectoryException pharmacyFailure, hospitalFailure;
        final List<String> pharmacyRegions = Collections.synchronizedList(new ArrayList<>());
        final List<String> hospitalRegions = Collections.synchronizedList(new ArrayList<>());

        StubNmc() {
            super("test-key");
        }

        @Override
        public List<Place> fetchPharmacies(String sido) {
            pharmacyRegions.add(sido);
            if (pharmacyFailure != null) throw pharmacyFailure;
            return List.copyOf(pharmacies);
        }

        @Override
        public List<Place> fetchHospitals(String sido) {
            hospitalRegions.add(sido);
            if (hospitalFailure != null) throw hospitalFailure;
            return List.copyOf(hospitals);
        }
    }

    /** 외부 API 대신 정해 둔 피부과 기관 목록 또는 실패를 돌려주는 건강보험심사평가원 수집기. */
    static class StubHira extends HiraHospitalDirectory {
        List<Facility> facilities = List.of();
        DirectoryException failure;
        final List<String> codes = Collections.synchronizedList(new ArrayList<>());

        StubHira() {
            super("test-key");
        }

        @Override
        public List<Facility> fetchDermatology(String sidoCode) {
            codes.add(sidoCode);
            if (failure != null) throw failure;
            return List.copyOf(facilities);
        }
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        hospitals.deleteAll();
        var a =
                admins.findById("sync-admin")
                        .orElseGet(
                                () ->
                                        Admin.builder()
                                                .adminId("sync-admin")
                                                .name("동기화 검증 관리자")
                                                .password(encoder.encode("ServiceTest123!"))
                                                .build());
        admins.saveAndFlush(a);
        admin = "Bearer " + jwt.createToken(a.getAdminId(), "ADMIN", a.currentTokenVersion());
        nmc = new StubNmc();
        hira = new StubHira();
        service = new HospitalSyncService(hospitals, nmc, hira, entityManager, transactionManager);
    }

    // ---- 도우미 ----

    HospitalSyncRequestDto request(String scope, String regionCode) {
        var dto = new HospitalSyncRequestDto();
        dto.setTargetScope(scope);
        dto.setRegionCode(regionCode);
        return dto;
    }

    /** 평일(월~금)을 같은 시간으로 채운 약국. */
    Place pharmacy(String hpid, String open, String close) {
        String[] o = new String[9], c = new String[9];
        for (int day = 1; day <= 5; day++) {
            o[day] = open;
            c[day] = close;
        }
        return new Place(hpid, "약국 " + hpid, "서울특별시 강남구 테스트로 " + hpid, "02-111-2222", 37.5, 127.0, null, o, c);
    }

    List<Place> pharmacies(int count) {
        var rows = new ArrayList<Place>();
        for (int i = 0; i < count; i++) rows.add(pharmacy("C" + i, "0900", i % 2 == 0 ? "2000" : "1800"));
        return rows;
    }

    static double lat(int i) { return 37.0 + i * 0.001; } // 기관마다 100m 이상 떨어뜨려 서로 다른 기관과 섞이지 않게 한다

    /** i번째 피부과 보유 기관. 10의 배수는 종합병원, 7의 배수는 이름에 '피부'가 들어간 의원, 그 외는 일반 의원. */
    Facility facility(int i) {
        String className = i % 10 == 0 ? "종합병원" : "의원";
        String name = i % 7 == 0 && i % 10 != 0 ? "피부사랑" + i + "의원" : "건강" + i + "의원";
        return new Facility("YK" + i, name, "서울특별시 강남구 테스트로 " + i, className, "서울", "강남구", "02-000-" + i, lat(i), 127.0);
    }

    /** i번째 기관과 짝지어질 국립중앙의료원 항목: 짝수는 평일 20시까지, 홀수는 18시까지, 9의 배수는 응급실 운영. */
    Place nmcPlaceFor(int i) {
        String[] o = new String[9], c = new String[9];
        for (int day = 1; day <= 5; day++) {
            o[day] = "0900";
            c[day] = i % 2 == 0 ? "2000" : "1800";
        }
        return new Place("N" + i, facility(i).name(), "서울특별시 강남구 테스트로 " + i, null, lat(i), 127.0, i % 9 == 0 ? Boolean.TRUE : Boolean.FALSE, o, c);
    }

    /** 0..count-1번 기관을 만들고, 3으로 나눠 1이 남는 기관은 국립중앙의료원에 항목이 없게 한다(결합 실패 ≈ 33%의 절반 미만이 되도록 조정). */
    void seedHospitals(int count, int unmatchedEvery) {
        var facilities = new ArrayList<Facility>();
        var places = new ArrayList<Place>();
        for (int i = 1; i <= count; i++) {
            facilities.add(facility(i));
            if (unmatchedEvery == 0 || i % unmatchedEvery != 0) places.add(nmcPlaceFor(i));
        }
        hira.facilities = facilities;
        nmc.hospitals = places;
    }

    String failureMessage(HospitalSyncRequestDto dto) {
        var e = assertThrows(CustomException.class, () -> service.syncHospitals(dto));
        assertEquals(ErrorCode.HOSPITAL_SYNC_FAILED, e.getErrorCode());
        return e.getMessage();
    }

    Hospital hospital(String id, String status, String dataSource, HospitalType type) {
        return Hospital.builder()
                .hospitalId(id)
                .name("검증용 " + id)
                .hospitalType(type)
                .region("검증지역")
                .address("검증 주소")
                .status(status)
                .dataSource(dataSource)
                .build();
    }

    long active(HospitalType type) {
        return hospitals.findAll().stream().filter(h -> h.getHospitalType() == type && "ACTIVE".equals(h.getStatus())).count();
    }

    List<String> snapshot() {
        return hospitals.findAll().stream().map(h -> h.getHospitalId() + h.getStatus() + h.getVersion()).sorted().toList();
    }

    // ---- 엔티티 규칙 (공공데이터 원본이 바뀌었다고 가정하고 호출) ----

    Hospital source() {
        return Hospital.builder()
                .hospitalId("HIRA-1")
                .name("새 이름")
                .hospitalType(HospitalType.PHARMACY)
                .region("검증지역")
                .address("검증 주소")
                .dataSource(Hospital.SOURCE_PUBLIC_DATA)
                .sourceUrl(Hospital.SOURCE_PUBLIC_DATA_URL)
                .verifiedOn(LocalDate.of(2026, 11, 10))
                .status("ACTIVE")
                .build();
    }

    @Test
    void adminHiddenHospitalStaysHiddenAfterSourceChanges() {
        var h = hospital("HIRA-1", "INACTIVE", Hospital.SOURCE_PUBLIC_DATA, HospitalType.PHARMACY);
        assertTrue(h.updateFrom(source()));
        assertEquals("새 이름", h.getName(), "내용은 원본대로 갱신된다");
        assertEquals("INACTIVE", h.getStatus(), "관리자가 숨긴 기관은 되살아나면 안 된다");
        assertFalse(h.updateFrom(source()), "같은 원본을 다시 받으면 변경이 없다");
        assertEquals("INACTIVE", h.getStatus());
    }

    @Test
    void legacyHiddenStatusIsAlsoKept() {
        var h = hospital("HIRA-1", "HIDDEN", Hospital.SOURCE_PUBLIC_DATA, HospitalType.PHARMACY);
        h.updateFrom(source());
        assertEquals("HIDDEN", h.getStatus());
    }

    @Test
    void hospitalExcludedBySyncIsRestoredWhenItReturns() {
        var h = hospital("HIRA-1", "ACTIVE", Hospital.SOURCE_PUBLIC_DATA, HospitalType.PHARMACY);
        h.deactivate();
        assertEquals("INACTIVE", h.getStatus());
        assertEquals(Hospital.SOURCE_EXCLUDED, h.getDataSource(), "동기화가 중지한 것을 표시한다");
        assertTrue(h.updateFrom(source()));
        assertEquals("ACTIVE", h.getStatus());
        assertEquals(Hospital.SOURCE_PUBLIC_DATA, h.getDataSource());
    }

    @Test
    void existingHospitalWithoutProvenanceIsBackfilled() {
        var h = hospital("HIRA-1", "ACTIVE", Hospital.SOURCE_PUBLIC_DATA, HospitalType.PHARMACY);
        h.update(
                new skincare.dto.HospitalRequest(
                        "새 이름", HospitalType.PHARMACY, "검증지역", "검증 주소", null, false, false,
                        Hospital.SOURCE_PUBLIC_DATA, null, null, "ACTIVE", null, null));
        assertNull(h.getSourceUrl());
        assertTrue(h.updateFrom(source()), "출처·확인일이 비어 있으면 채워야 한다");
        assertEquals(Hospital.SOURCE_PUBLIC_DATA_URL, h.getSourceUrl());
        assertEquals(LocalDate.of(2026, 11, 10), h.getVerifiedOn());
    }

    @Test
    void bothSyncPrefixesAreManagedButManualRecordsAreNot() {
        assertTrue(Hospital.isSyncManaged("HIRA-abc"));
        assertTrue(Hospital.isSyncManaged("NMC-C1109587"));
        assertFalse(Hospital.isSyncManaged("0f8fad5b-d9cb-469f-a165-70867728950e"));
        assertFalse(Hospital.isSyncManaged(null));
    }

    // ---- 동기화 API (HTTP: 인증과 입력 검증만. 동기화 자체는 외부 API가 필요해 실행하지 않는다) ----

    @Test
    void onlyAdministratorsCanSync() throws Exception {
        mvc.perform(post("/api/wc/admin/hospitals/sync")).andExpect(status().isUnauthorized());
        assertEquals(0, hospitals.count());
    }

    @Test
    void unknownScopeIsRejectedWithoutChangingData() throws Exception {
        mvc.perform(
                        post("/api/wc/admin/hospitals/sync")
                                .header("Authorization", admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"targetScope\":\"EVERYTHING\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, hospitals.count());
    }

    @Test
    void unsupportedRegionCodeIsRejectedBeforeAnyFetch() {
        var error = assertThrows(CustomException.class, () -> service.syncHospitals(request("ALL", "240000")));
        assertEquals(ErrorCode.INVALID_INPUT_VALUE, error.getErrorCode());
        assertTrue(hira.codes.isEmpty() && nmc.hospitalRegions.isEmpty() && nmc.pharmacyRegions.isEmpty(), "수집을 시작하기 전에 거부");
    }

    // ---- 감사 기록과 동시 실행 (컨트롤러 단위 테스트: 서비스는 모의) ----

    @Test
    void controllerRecordsTheSyncInTheAuditLogAndRejectsConcurrentRuns() {
        var syncService = mock(HospitalSyncService.class);
        var audit = mock(AdminAuditService.class);
        var controller = new AdminHospitalController(mock(HospitalService.class), syncService, audit);
        var result =
                HospitalSyncResponseDto.builder()
                        .syncId("SYNC-1")
                        .targetScope("HOSPITAL")
                        .status("COMPLETED")
                        .metrics(HospitalSyncResponseDto.SyncMetrics.builder().totalFetched(6).newAdded(1).updated(2).deactivated(3).build())
                        .build();
        when(syncService.syncHospitals(any())).thenAnswer(inv -> {
            // 실행 중에 들어온 두 번째 요청은 거부된다
            var conflict = assertThrows(ResponseStatusException.class, () -> controller.sync(request("HOSPITAL", null)));
            assertEquals(409, conflict.getStatusCode().value());
            return result;
        });

        var response = controller.sync(request("HOSPITAL", null));
        assertEquals(200, response.getStatusCode().value());
        assertSame(result, response.getBody());
        verify(audit).current("HOSPITAL_SYNC", "SYNC-1", "HOSPITAL +1 ~2 -3");

        // 끝난 뒤에는 다시 실행할 수 있다
        reset(syncService);
        when(syncService.syncHospitals(any())).thenReturn(result);
        assertEquals(200, controller.sync(null).getStatusCode().value(), "본문 없이도 실행");
    }

    // ---- 병원 동기화 (건강보험심사평가원 피부과 + 국립중앙의료원 진료시간·응급실) ----

    @Test
    void hospitalSyncAddsDermatologyFacilitiesWithMatchedHoursAndFlags() {
        seedHospitals(300, 10); // 10의 배수 기관은 국립중앙의료원에 항목이 없다
        var result = service.syncHospitals(request("HOSPITAL", null)).getMetrics();
        assertEquals(300, result.getNewAdded());
        assertEquals(300, hospitals.count());
        assertEquals(Arrays.asList((String) null), hira.codes, "전국 수집은 시도를 지정하지 않는다");
        assertTrue(hospitals.findAll().stream().allMatch(h -> h.getHospitalId().startsWith("HIRA-") && h.getHospitalId().length() <= 50));
        assertTrue(hospitals.findAll().stream().allMatch(h -> "ACTIVE".equals(h.getStatus()) && h.getSourceUrl() != null && h.getVerifiedOn() != null));

        var byName = new HashMap<String, Hospital>();
        hospitals.findAll().forEach(h -> byName.put(h.getName(), h));
        // 결합된 기관: 국립중앙의료원의 시간·응급실이 들어온다
        var night = byName.get("건강2의원"); // 짝수 → 20시까지
        assertTrue(night.isNightCare(), "평일 19시 이후 영업은 야간진료");
        assertEquals("09:00 - 20:00", night.getWeekdayHours());
        assertEquals("휴무", night.getSaturdayHours());
        assertEquals(HiraHospitalDirectory.SOURCE_NAME, night.getDataSource());
        var day = byName.get("건강3의원"); // 홀수 → 18시까지
        assertFalse(day.isNightCare());
        assertEquals("09:00 - 18:00", day.getWeekdayHours());
        assertTrue(byName.get("건강9의원").isEmergency(), "9의 배수는 응급실 운영");
        assertFalse(byName.get("건강4의원").isEmergency());
        // 결합하지 못한 기관: 진료시간 없음, 야간·응급 false(정보 없음), 출처는 건강보험심사평가원만
        var unmatched = hospitals.findAll().stream().filter(h -> h.getWeekdayHours() == null).toList();
        assertEquals(30, unmatched.size(), "10의 배수 30곳");
        assertTrue(unmatched.stream().noneMatch(h -> h.isNightCare() || h.isEmergency()));
        assertTrue(unmatched.stream().allMatch(h -> HiraHospitalDirectory.SOURCE_NAME_HIRA_ONLY.equals(h.getDataSource())));
        // 기본 정보는 건강보험심사평가원 값
        assertEquals("서울 강남구", night.getRegion());
        assertEquals(HiraHospitalDirectory.SOURCE_URL, night.getSourceUrl());
        assertEquals(lat(2), night.getLatitude());
    }

    @Test
    void facilityTypesFollowTheInstitutionClassAndName() {
        seedHospitals(150, 0);
        service.syncHospitals(request("HOSPITAL", null));
        var all = hospitals.findAll();
        assertTrue(all.stream().filter(h -> h.getName().contains("피부사랑")).allMatch(h -> h.getHospitalType() == HospitalType.DERMATOLOGY), "이름에 피부가 들어간 의원");
        assertEquals(15, all.stream().filter(h -> h.getHospitalType() == HospitalType.GENERAL_HOSPITAL).count(), "종합병원 10의 배수");
        assertTrue(all.stream().filter(h -> h.getName().startsWith("건강")).filter(h -> h.getHospitalType() == HospitalType.OTHER).count() > 100);
    }

    @Test
    void repeatedHospitalSyncChangesNothingAndKeepsAdminDecisionsAndManualRecords() {
        hospitals.saveAndFlush(hospital("manual-uuid-1", "ACTIVE", "관리자 입력", HospitalType.OTHER));
        seedHospitals(300, 0);
        service.syncHospitals(request("HOSPITAL", null));
        var hidden = hospitals.findAll().stream().filter(h -> h.getName().equals("건강7의원") || h.getName().equals("피부사랑7의원")).findFirst().orElseThrow();
        hidden.changeStatusByAdmin("INACTIVE");
        hospitals.saveAndFlush(hidden);

        var second = service.syncHospitals(request("HOSPITAL", null)).getMetrics();
        assertEquals(0, second.getNewAdded());
        assertEquals(0, second.getUpdated(), "내용이 같으면 갱신하지 않는다");
        assertEquals(0, second.getDeactivated());
        assertEquals("INACTIVE", hospitals.findById(hidden.getHospitalId()).orElseThrow().getStatus(), "관리자가 숨긴 병원은 유지");
        assertEquals("관리자 입력", hospitals.findById("manual-uuid-1").orElseThrow().getDataSource());
        assertEquals(301, hospitals.count());
    }

    @Test
    void earlierCsvSyncHospitalsAreUpdatedInPlaceAndThoseWithoutDermatologyAreDeactivated() {
        seedHospitals(200, 0);
        // 이전 CSV 동기화가 같은 요양기관 기호로 저장해 둔 기관: 같은 ID → 새 데이터로 제자리 갱신(중복 생성 없음)
        String sameId = "HIRA-" + java.util.HexFormat.of().formatHex(sha1("YK1"));
        hospitals.saveAndFlush(
                Hospital.builder().hospitalId(sameId).name("옛 이름").hospitalType(HospitalType.OTHER).region("옛 지역").address("옛 주소")
                        .dataSource("공공데이터 포털").sourceUrl("https://www.data.go.kr/").verifiedOn(LocalDate.of(2026, 11, 1)).status("ACTIVE").build());
        // 피부과 진료과목이 없어 이번 목록에 없는 기관
        for (int i = 0; i < 120; i++) hospitals.save(hospital("HIRA-old" + i, "ACTIVE", "공공데이터 포털", HospitalType.OTHER));
        hospitals.flush();

        var result = service.syncHospitals(request("HOSPITAL", null)).getMetrics();
        assertEquals(199, result.getNewAdded(), "YK1은 이미 있으므로 새로 만들지 않는다");
        assertEquals(1, result.getUpdated());
        assertEquals(120, result.getDeactivated());
        var updated = hospitals.findById(sameId).orElseThrow();
        assertEquals("건강1의원", updated.getName());
        assertEquals("서울 강남구", updated.getRegion());
        assertNotNull(updated.getWeekdayHours(), "진료시간이 채워진다");
        var gone = hospitals.findById("HIRA-old0").orElseThrow();
        assertEquals("INACTIVE", gone.getStatus());
        assertEquals(Hospital.SOURCE_EXCLUDED, gone.getDataSource());
    }

    static byte[] sha1(String text) {
        try {
            return java.security.MessageDigest.getInstance("SHA-1").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aLowMatchRateStopsTheHospitalSyncAndChangesNothing() {
        seedHospitals(300, 0);
        service.syncHospitals(request("HOSPITAL", null));
        var before = snapshot();

        seedHospitals(300, 2); // 절반이 국립중앙의료원과 결합되지 않음 → 응답 형식 변경이나 부분 응답으로 의심
        String message = failureMessage(request("HOSPITAL", null));
        assertTrue(message.contains("결합률"), message);
        assertEquals(before, snapshot(), "진료시간·응급실이 대량으로 비워지면 안 된다");
    }

    @Test
    void aSuddenlyMuchSmallerHospitalListStopsTheSync() {
        seedHospitals(300, 0);
        service.syncHospitals(request("HOSPITAL", null));
        seedHospitals(200, 0); // 기존의 67%
        String message = failureMessage(request("HOSPITAL", null));
        assertTrue(message.contains("크게 줄어"), message);
        assertEquals(300, active(HospitalType.OTHER) + active(HospitalType.DERMATOLOGY) + active(HospitalType.GENERAL_HOSPITAL));
    }

    @Test
    void anyHospitalApiFailureAbortsTheWholeSyncInsteadOfSavingWithoutHours() {
        seedHospitals(300, 0);
        service.syncHospitals(request("HOSPITAL", null));
        var before = snapshot();

        // 국립중앙의료원 병·의원 API 장애: 진료시간 없이 저장하지 않는다
        nmc.hospitalFailure = new DirectoryException(Failure.UNAVAILABLE, Source.NMC_HOSPITAL);
        String message = failureMessage(request("HOSPITAL", null));
        assertTrue(message.contains("국립중앙의료원 병·의원") && message.contains("연결하지 못했습니다"), message);
        assertEquals(before, snapshot());
        nmc.hospitalFailure = new DirectoryException(Failure.INCOMPLETE, Source.NMC_HOSPITAL);
        assertTrue(failureMessage(request("HOSPITAL", null)).contains("모두 받지 못해"));
        assertEquals(before, snapshot());
        nmc.hospitalFailure = null;

        // 건강보험심사평가원 API 장애
        hira.failure = new DirectoryException(Failure.AUTH_ERROR, Source.HIRA_HOSPITAL);
        String auth = failureMessage(request("HOSPITAL", null));
        assertTrue(auth.contains("건강보험심사평가원 병원") && auth.contains("병원정보서비스") && auth.contains("활용신청"), auth);
        assertFalse(auth.contains("test-key"));
        assertEquals(before, snapshot());
    }

    @Test
    void hospitalRegionCodeMapsToHiraCodeAndProviderRegionNamesAndNeverDeactivates() {
        seedHospitals(300, 0);
        service.syncHospitals(request("HOSPITAL", null));
        hira.codes.clear();
        nmc.hospitalRegions.clear();
        seedHospitals(5, 0); // 일부 지역만 받은 상태: 나머지를 중지하면 안 된다
        var result = service.syncHospitals(request("HOSPITAL", "360000")).getMetrics();
        assertEquals(List.of("360000"), hira.codes, "건강보험심사평가원에는 시도코드를 그대로 전달");
        assertEquals(List.of("전남광주통합특별시", "전라남도", "광주광역시"), nmc.hospitalRegions, "국립중앙의료원에는 시도 공식 명칭으로(광주·전남은 새 명칭 포함)");
        assertEquals(0, result.getDeactivated());
        assertEquals(300, active(HospitalType.OTHER) + active(HospitalType.DERMATOLOGY) + active(HospitalType.GENERAL_HOSPITAL));
    }

    // ---- 약국 동기화 (국립중앙의료원 API) ----

    @Test
    void pharmacySyncAddsPublishedRecordsWithHoursAndNightFlags() {
        nmc.pharmacies = pharmacies(1500);
        var result = service.syncHospitals(request("PHARMACY", null));
        assertEquals(1500, result.getMetrics().getNewAdded());
        assertEquals(1500, hospitals.count());

        var night = hospitals.findById("NMC-C0").orElseThrow(); // 평일 20시까지
        var day = hospitals.findById("NMC-C1").orElseThrow(); // 평일 18시까지
        assertEquals(HospitalType.PHARMACY, night.getHospitalType());
        assertTrue(night.isNightCare(), "평일 19시 이후 영업은 야간진료");
        assertFalse(day.isNightCare());
        assertFalse(night.isEmergency());
        assertEquals("09:00 - 20:00", night.getWeekdayHours());
        assertEquals("휴무", night.getSaturdayHours());
        assertEquals("서울특별시 강남구", night.getRegion());
        assertEquals("ACTIVE", night.getStatus());
        // 사용자 검색 노출 조건: 출처 URL과 확인일
        assertEquals(NmcDirectory.PHARMACY_SOURCE_URL, night.getSourceUrl());
        assertEquals(NmcDirectory.PHARMACY_SOURCE_NAME, night.getDataSource());
        assertNotNull(night.getVerifiedOn());
        assertTrue(hospitals.findAll().stream().allMatch(h -> h.getHospitalId().length() <= 50));
        assertEquals(Arrays.asList((String) null), nmc.pharmacyRegions, "전국 수집은 시도를 지정하지 않는다");
    }

    @Test
    void repeatedPharmacySyncChangesNothingAndKeepsAdminDecisions() {
        nmc.pharmacies = pharmacies(1500);
        service.syncHospitals(request("PHARMACY", null));
        var hidden = hospitals.findById("NMC-C7").orElseThrow();
        hidden.changeStatusByAdmin("INACTIVE");
        hospitals.saveAndFlush(hidden);

        var second = service.syncHospitals(request("PHARMACY", null)).getMetrics();
        assertEquals(0, second.getNewAdded());
        assertEquals(0, second.getUpdated(), "내용이 같으면 갱신하지 않는다");
        assertEquals(0, second.getDeactivated());
        assertEquals("INACTIVE", hospitals.findById("NMC-C7").orElseThrow().getStatus(), "관리자가 숨긴 약국은 유지");
        assertEquals(1500, hospitals.count());
    }

    @Test
    void changedPharmacyDetailsAreUpdatedAndVanishedPharmaciesAreDeactivated() {
        nmc.pharmacies = pharmacies(1500);
        service.syncHospitals(request("PHARMACY", null));

        // C0은 영업시간 변경, C1499는 목록에서 사라짐
        var changed = new ArrayList<>(pharmacies(1499));
        changed.set(0, pharmacy("C0", "0900", "1800"));
        nmc.pharmacies = changed;
        var result = service.syncHospitals(request("PHARMACY", null)).getMetrics();
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getDeactivated());
        assertFalse(hospitals.findById("NMC-C0").orElseThrow().isNightCare(), "시간 변경이 반영된다");
        var gone = hospitals.findById("NMC-C1499").orElseThrow();
        assertEquals("INACTIVE", gone.getStatus());
        assertEquals(Hospital.SOURCE_EXCLUDED, gone.getDataSource());

        // 다시 나타나면 복구된다
        nmc.pharmacies = pharmacies(1500);
        var back = service.syncHospitals(request("PHARMACY", null)).getMetrics();
        assertEquals(0, back.getNewAdded());
        assertEquals("ACTIVE", hospitals.findById("NMC-C1499").orElseThrow().getStatus());
    }

    @Test
    void pharmaciesFromTheOldCsvSyncAreSupersededByTheApiRecords() {
        hospitals.saveAndFlush(hospital("HIRA-old-csv-pharmacy", "ACTIVE", Hospital.SOURCE_PUBLIC_DATA, HospitalType.PHARMACY));
        nmc.pharmacies = pharmacies(10);
        var result = service.syncHospitals(request("PHARMACY", null)).getMetrics();
        assertEquals(10, result.getNewAdded());
        assertEquals(1, result.getDeactivated());
        assertEquals("INACTIVE", hospitals.findById("HIRA-old-csv-pharmacy").orElseThrow().getStatus());
    }

    @Test
    void aSuddenlyMuchSmallerPharmacyFetchStopsTheSyncAndChangesNothing() {
        nmc.pharmacies = pharmacies(1500);
        service.syncHospitals(request("PHARMACY", null));
        nmc.pharmacies = pharmacies(900); // 기존의 60%
        String message = failureMessage(request("PHARMACY", null));
        assertTrue(message.contains("크게 줄어"), message);
        assertEquals(1500, active(HospitalType.PHARMACY), "중지된 기관이 없어야 한다");
        // 80% 이상이면 정상 진행 (실제 폐업으로 보고 중지)
        nmc.pharmacies = pharmacies(1300);
        assertEquals(200, service.syncHospitals(request("PHARMACY", null)).getMetrics().getDeactivated());
    }

    @Test
    void collectionFailuresExplainTheCauseAndLeaveDataUntouched() {
        nmc.pharmacies = pharmacies(300);
        service.syncHospitals(request("PHARMACY", null));
        var before = snapshot();

        Map<Failure, String> expected = Map.of(
                Failure.UNCONFIGURED, "PUBLIC_MEDICAL_SERVICE_KEY",
                Failure.AUTH_ERROR, "인증키가 거부",
                Failure.RATE_LIMITED, "호출 한도",
                Failure.UNAVAILABLE, "연결하지 못했습니다",
                Failure.INVALID_RESPONSE, "해석하지 못했습니다",
                Failure.INCOMPLETE, "모두 받지 못해");
        for (var entry : expected.entrySet()) {
            nmc.pharmacyFailure = new DirectoryException(entry.getKey(), Source.NMC_PHARMACY);
            String message = failureMessage(request("PHARMACY", null));
            assertTrue(message.contains(entry.getValue()), entry.getKey() + ": " + message);
            assertFalse(message.contains("test-key"), "인증키가 메시지에 들어가면 안 된다");
        }
        assertEquals(before, snapshot(), "수집에 실패하면 어떤 기관도 바뀌지 않는다");
    }

    @Test
    void anEmptyPharmacyListNeverWipesExistingData() {
        nmc.pharmacies = pharmacies(300);
        service.syncHospitals(request("PHARMACY", null));
        nmc.pharmacies = List.of();
        assertTrue(failureMessage(request("PHARMACY", null)).contains("읽지 못했습니다"));
        assertEquals(300, active(HospitalType.PHARMACY));
    }

    @Test
    void pharmacyRegionCodesAreMappedToProviderRegionNamesAndNeverDeactivate() {
        nmc.pharmacies = pharmacies(300);
        service.syncHospitals(request("PHARMACY", null));
        nmc.pharmacyRegions.clear();
        nmc.pharmacies = pharmacies(5);
        var result = service.syncHospitals(request("PHARMACY", "360000")).getMetrics();
        assertEquals(List.of("전남광주통합특별시", "전라남도", "광주광역시"), nmc.pharmacyRegions, "전남광주 코드는 새 명칭과 옛 명칭을 모두 조회");
        assertEquals(0, result.getDeactivated(), "지역 지정 동기화는 중지하지 않는다");
        assertEquals(300, active(HospitalType.PHARMACY));
    }

    // ---- 범위 ----

    @Test
    void pharmacyOnlySyncNeverCallsTheHospitalApis() {
        nmc.pharmacies = pharmacies(300);
        hira.failure = new DirectoryException(Failure.UNAVAILABLE, Source.HIRA_HOSPITAL);
        nmc.hospitalFailure = new DirectoryException(Failure.UNAVAILABLE, Source.NMC_HOSPITAL);
        assertEquals(300, service.syncHospitals(request("PHARMACY", null)).getMetrics().getNewAdded());
        assertTrue(hira.codes.isEmpty() && nmc.hospitalRegions.isEmpty());
    }

    @Test
    void hospitalOnlySyncNeverCallsThePharmacyApiOrTouchesPharmacies() {
        nmc.pharmacies = pharmacies(300);
        service.syncHospitals(request("PHARMACY", null));
        nmc.pharmacyRegions.clear();
        nmc.pharmacyFailure = new DirectoryException(Failure.UNAVAILABLE, Source.NMC_PHARMACY); // 호출되면 실패한다
        seedHospitals(150, 0);
        var result = service.syncHospitals(request("HOSPITAL", null)).getMetrics();
        assertTrue(nmc.pharmacyRegions.isEmpty());
        assertEquals(150, result.getNewAdded());
        assertEquals(0, result.getDeactivated(), "병원 동기화는 약국을 중지하지 않는다");
        assertEquals(300, active(HospitalType.PHARMACY));
    }

    @Test
    void fullSyncCombinesHospitalsAndPharmaciesAndIsIdempotent() {
        nmc.pharmacies = pharmacies(1500);
        seedHospitals(300, 10);
        var first = service.syncHospitals(request(null, null)).getMetrics();
        assertEquals(1800, first.getNewAdded());
        assertEquals(1800, hospitals.count(), "ID 중복 없이 모두 저장");
        assertEquals(1500, hospitals.findAll().stream().filter(h -> h.getHospitalType() == HospitalType.PHARMACY).count());
        assertTrue(hospitals.findAll().stream().allMatch(h -> h.getSourceUrl() != null && h.getVerifiedOn() != null && h.getHospitalId().length() <= 50));

        var again = service.syncHospitals(request(null, null)).getMetrics();
        assertEquals(0, again.getNewAdded());
        assertEquals(0, again.getUpdated());
        assertEquals(0, again.getDeactivated());
    }

    @Test
    void aFailureInOneApiDuringAFullSyncSavesNothingFromTheOthers() {
        nmc.pharmacies = pharmacies(300);
        seedHospitals(300, 0);
        hira.failure = new DirectoryException(Failure.RATE_LIMITED, Source.HIRA_HOSPITAL);
        assertTrue(failureMessage(request("ALL", null)).contains("호출 한도"));
        assertEquals(0, hospitals.count(), "약국만 먼저 저장되는 일이 없어야 한다");
    }
}
