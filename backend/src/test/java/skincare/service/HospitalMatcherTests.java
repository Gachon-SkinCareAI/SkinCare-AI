package skincare.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import skincare.external.HiraHospitalDirectory.Facility;
import skincare.external.NmcDirectory.Place;

import java.util.List;

/** 건강보험심사평가원 기관과 국립중앙의료원 항목을 짝짓는 규칙 검증 (외부 호출·DB 없음). */
class HospitalMatcherTests {
    static final double LAT = 37.5, LON = 127.0;

    /** 위도·경도를 미터 단위로 이동시킨다 (한국 위도 기준). */
    static double lat(double meters) { return LAT + meters / 111000.0; }
    static double lon(double meters) { return LON + meters / 88000.0; }

    static Facility facility(String ykiho, String name, String sigungu, Double lat, Double lon) {
        return new Facility(ykiho, name, sigungu + " 어딘가 1", "의원", "서울", sigungu, null, lat, lon);
    }

    static Place place(String hpid, String name, String address, Double lat, Double lon) {
        return new Place(hpid, name, address, null, lat, lon, null, new String[9], new String[9]);
    }

    @Test
    void normalizationIgnoresCorporateMarksSpacesAndPunctuation() {
        assertEquals("가산광동병원", HospitalMatcher.normalize("(의)가산 광동 병원"));
        assertEquals(HospitalMatcher.normalize("가산의료재단광동병원"), HospitalMatcher.normalize("(의)가산의료재단광동병원"));
        assertEquals(HospitalMatcher.normalize("순천향대학교부속천안병원"), HospitalMatcher.normalize("학교법인동은학원순천향대학교부속천안병원".replace("학교법인", "")).replace("동은학원", ""), "법인명이 끼어도 핵심 이름은 남는다");
        assertEquals("", HospitalMatcher.normalize(null));
        assertEquals("a병원", HospitalMatcher.normalize("A·병원 !"));
    }

    @Test
    void matchesByNormalizedNameWithinFiveHundredMetersChoosingTheNearest() {
        var f = facility("K1", "가산광동병원", "강남구", LAT, LON);
        var near = place("P-near", "(의)가산광동병원", "서울 강남구 1", lat(80), LON);
        var far = place("P-far", "가산광동병원", "서울 강남구 2", lat(400), LON);
        var tooFar = place("P-toofar", "가산광동병원", "서울 강남구 3", lat(900), LON);
        var result = HospitalMatcher.match(List.of(f), List.of(far, tooFar, near));
        assertEquals("P-near", result.byYkiho().get("K1").hpid(), "같은 이름이면 가장 가까운 것");
        assertEquals(1, result.byName());
        assertEquals(0, result.unmatched());
        assertEquals(1.0, result.rate());
    }

    @Test
    void doesNotMatchTheSameNameBeyondFiveHundredMeters() {
        var f = facility("K1", "서울의원", "강남구", LAT, LON);
        var result = HospitalMatcher.match(List.of(f), List.of(place("P1", "서울의원", "부산 해운대구 1", lat(900), LON)));
        assertTrue(result.byYkiho().isEmpty());
        assertEquals(1, result.unmatched());
    }

    @Test
    void matchesNamesThatContainEachOtherOnlyWhenWithinFiftyMeters() {
        var f = facility("K1", "맑은세상피부과의원", "강남구", LAT, LON);
        var closeBy = place("P1", "메디컬닥터스맑은세상피부과의원", "서울 강남구 1", lat(20), LON);
        var result = HospitalMatcher.match(List.of(f), List.of(closeBy));
        assertEquals("P1", result.byYkiho().get("K1").hpid());
        assertEquals(1, result.byProximity());

        var farther = place("P2", "메디컬닥터스맑은세상피부과의원", "서울 강남구 1", lat(120), LON);
        assertTrue(HospitalMatcher.match(List.of(f), List.of(farther)).byYkiho().isEmpty(), "포함 관계는 50m를 넘으면 인정하지 않는다");
    }

    @Test
    void shortNamesAreNotMatchedByContainment() {
        // 2자 이름은 포함 관계만으로는 짝짓지 않는다 (오결합 방지)
        var f = facility("K1", "서울", "강남구", LAT, LON);
        var result = HospitalMatcher.match(List.of(f), List.of(place("P1", "서울의원", "서울 강남구 1", LAT, LON)));
        assertTrue(result.byYkiho().isEmpty());
    }

    @Test
    void fallsBackToTheNameAndDistrictWhenThereAreNoCoordinates() {
        var f = facility("K1", "해든의원", "마포구", null, null);
        var inDistrict = place("P1", "해든의원", "서울특별시 마포구 월드컵로 1", null, null);
        var otherDistrict = place("P2", "해든의원", "서울특별시 강남구 테헤란로 1", null, null);
        var result = HospitalMatcher.match(List.of(f), List.of(otherDistrict, inDistrict));
        assertEquals("P1", result.byYkiho().get("K1").hpid());
        assertEquals(1, result.bySigungu());
    }

    @Test
    void doesNotGuessWhenTheDistrictFallbackIsAmbiguous() {
        var f = facility("K1", "해든의원", "마포구", null, null);
        var a = place("P1", "해든의원", "서울특별시 마포구 월드컵로 1", null, null);
        var b = place("P2", "해든의원", "서울특별시 마포구 양화로 2", null, null);
        var result = HospitalMatcher.match(List.of(f), List.of(a, b));
        assertTrue(result.byYkiho().isEmpty(), "후보가 둘이면 추측하지 않는다");
        assertEquals(1, result.unmatched());
    }

    @Test
    void reportsPlacesClaimedByMoreThanOneFacility() {
        var f1 = facility("K1", "같은빌딩의원", "강남구", LAT, LON);
        var f2 = facility("K2", "같은빌딩의원", "강남구", lat(10), LON);
        var result = HospitalMatcher.match(List.of(f1, f2), List.of(place("P1", "같은빌딩의원", "서울 강남구 1", LAT, LON)));
        assertEquals(2, result.matched());
        assertEquals(1, result.sharedPlaces(), "여러 기관이 같은 항목을 가리키면 오결합 신호로 센다");
    }

    @Test
    void rateCountsMatchedOverAllFacilities() {
        var facilities = List.of(
                facility("K1", "가나다의원", "강남구", LAT, LON),
                facility("K2", "라마바의원", "강남구", lat(2000), LON),
                facility("K3", "사아자의원", "강남구", lat(4000), LON),
                facility("K4", "차카타의원", "강남구", lat(6000), LON));
        var places = List.of(
                place("P1", "가나다의원", "서울 강남구 1", LAT, LON),
                place("P2", "라마바의원", "서울 강남구 1", lat(2000), LON));
        var result = HospitalMatcher.match(facilities, places);
        assertEquals(0.5, result.rate());
        assertEquals(2, result.unmatched());
        assertEquals(1.0, HospitalMatcher.match(List.of(), places).rate(), "기관이 없으면 비율 1");
    }
}
