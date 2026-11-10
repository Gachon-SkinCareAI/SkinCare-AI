package skincare.service;

import skincare.external.HiraHospitalDirectory.Facility;
import skincare.external.NmcDirectory.Place;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 건강보험심사평가원(HIRA) 의료기관과 국립중앙의료원(NMC) 병·의원을 같은 기관끼리 짝짓는다.
 *
 * <p>두 데이터에는 공통 ID가 없어 이름과 좌표로 결합한다. 전국 실측(HIRA 피부과 17,866곳 ↔ NMC 79,072곳)에서 96.0%가 짝지어졌다.
 * 결합 순서(앞 단계에서 찾으면 멈춘다):
 *
 * <ol>
 *   <li>정규화한 이름이 같고 500m 이내 — 가장 가까운 것
 *   <li>50m 이내이고 정규화한 이름이 서로 포함 관계(3자 이상) — 가장 가까운 것
 *   <li>정규화한 이름이 같고 주소에 같은 시군구가 있는 후보가 정확히 하나 — 좌표가 없거나 어긋난 경우
 * </ol>
 *
 * 짝이 없는 기관은 결과에 나오지 않는다(진료시간·응급실 정보 없음). 이 클래스는 순수 계산이며 외부 호출이나 DB를 쓰지 않는다.
 */
public final class HospitalMatcher {
    private HospitalMatcher() {}

    /** 결합 결과. byYkiho는 짝이 있는 기관만 담는다. */
    public record Result(
            Map<String, Place> byYkiho,
            int byName,
            int byProximity,
            int bySigungu,
            int unmatched,
            int sharedPlaces) {
        public int matched() {
            return byName + byProximity + bySigungu;
        }

        /** 짝지어진 비율(0~1). 기관이 없으면 1. */
        public double rate() {
            int total = matched() + unmatched;
            return total == 0 ? 1.0 : (double) matched() / total;
        }
    }

    static final double NAME_RADIUS_METERS = 500;
    static final double CONTAINS_RADIUS_METERS = 50;
    private static final double CELL = 0.005; // 약 450m
    private static final Pattern CORPORATE_MARK = Pattern.compile("\\((의|재|학|사|주|유|복|협|합|자|특)\\)");
    private static final Pattern CORPORATE_WORD =
            Pattern.compile("(의료법인|재단법인|학교법인|사회복지법인|사단법인|의료재단)");
    private static final Pattern NOISE = Pattern.compile("[\\s\\p{Punct}·]");

    /** 이름 비교용 정규화: 법인 표기·공백·구두점을 지우고 소문자로. */
    static String normalize(String name) {
        if (name == null) return "";
        String s = CORPORATE_MARK.matcher(name).replaceAll("");
        s = CORPORATE_WORD.matcher(s).replaceAll("");
        return NOISE.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 두 좌표 사이의 대략적인 거리(미터). 한국 위도에서 경도 1도 ≈ 88km. */
    static double meters(double lat1, double lon1, double lat2, double lon2) {
        double dx = (lon1 - lon2) * 88000, dy = (lat1 - lat2) * 111000;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static long cell(double lat, double lon) {
        long cy = (long) Math.floor(lat / CELL), cx = (long) Math.floor(lon / CELL);
        return (cy << 32) ^ (cx & 0xffffffffL);
    }

    public static Result match(List<Facility> facilities, List<Place> places) {
        String[] placeNames = new String[places.size()];
        Map<Long, List<Integer>> grid = new HashMap<>();
        Map<String, List<Integer>> byName = new HashMap<>();
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            placeNames[i] = normalize(p.name());
            byName.computeIfAbsent(placeNames[i], k -> new ArrayList<>()).add(i);
            if (p.latitude() != null && p.longitude() != null)
                grid.computeIfAbsent(cell(p.latitude(), p.longitude()), k -> new ArrayList<>()).add(i);
        }

        Map<String, Place> matched = new HashMap<>();
        Map<Integer, Integer> claims = new HashMap<>();
        int byNameCount = 0, byProximity = 0, bySigungu = 0, unmatched = 0;
        for (Facility f : facilities) {
            String name = normalize(f.name());
            int hit = -1, strategy = 0;
            if (f.latitude() != null && f.longitude() != null) {
                List<Integer> near = nearby(grid, f.latitude(), f.longitude());
                double best = Double.MAX_VALUE;
                for (int i : near) { // 1) 이름 일치 + 500m 이내, 가장 가까운 것
                    double d = distance(f, places.get(i));
                    if (d <= NAME_RADIUS_METERS && placeNames[i].equals(name) && d < best) {
                        best = d;
                        hit = i;
                    }
                }
                if (hit >= 0) strategy = 1;
                if (hit < 0 && name.length() >= 3) { // 2) 50m 이내 + 이름 포함 관계, 가장 가까운 것
                    best = Double.MAX_VALUE;
                    for (int i : near) {
                        String other = placeNames[i];
                        double d = distance(f, places.get(i));
                        if (d <= CONTAINS_RADIUS_METERS
                                && other.length() >= 3
                                && (other.contains(name) || name.contains(other))
                                && d < best) {
                            best = d;
                            hit = i;
                        }
                    }
                    if (hit >= 0) strategy = 2;
                }
            }
            if (hit < 0 && !f.sigungu().isEmpty()) { // 3) 이름 일치 + 같은 시군구의 유일한 후보
                int found = -1, count = 0;
                for (int i : byName.getOrDefault(name, List.of())) {
                    if (places.get(i).address().contains(f.sigungu())) {
                        found = i;
                        count++;
                    }
                }
                if (count == 1) {
                    hit = found;
                    strategy = 3;
                }
            }
            if (hit < 0) {
                unmatched++;
                continue;
            }
            matched.put(f.ykiho(), places.get(hit));
            claims.merge(hit, 1, Integer::sum);
            if (strategy == 1) byNameCount++;
            else if (strategy == 2) byProximity++;
            else bySigungu++;
        }
        int shared = (int) claims.values().stream().filter(n -> n > 1).count();
        return new Result(matched, byNameCount, byProximity, bySigungu, unmatched, shared);
    }

    private static List<Integer> nearby(Map<Long, List<Integer>> grid, double lat, double lon) {
        List<Integer> result = new ArrayList<>();
        long cy = (long) Math.floor(lat / CELL), cx = (long) Math.floor(lon / CELL);
        for (long dy = -1; dy <= 1; dy++)
            for (long dx = -1; dx <= 1; dx++)
                result.addAll(grid.getOrDefault(((cy + dy) << 32) ^ ((cx + dx) & 0xffffffffL), List.of()));
        return result;
    }

    private static double distance(Facility f, Place p) {
        if (p.latitude() == null || p.longitude() == null) return Double.MAX_VALUE;
        return meters(f.latitude(), f.longitude(), p.latitude(), p.longitude());
    }
}
