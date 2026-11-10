package skincare.service;

import static org.springframework.http.HttpStatus.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import skincare.domain.hospital.entity.*;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.dto.*;
import skincare.repository.HospitalRepository;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
// HospitalService 비즈니스 로직 처리
public class HospitalService {
    private final HospitalRepository hospitals;
    private final AdminAuditService audit;
    private final jakarta.persistence.EntityManager entityManager;

    public ServicePage<HospitalView> search(
            String query, HospitalType type, Boolean emergency, int page, int size, boolean admin) {
        return search(query, type, emergency, null, page, size, admin);
    }

    // 의료기관 검색
    // 사용자용(admin=false)은 운영 중이고 출처·확인일이 있는 기관만 최근 확인순으로 출력
    @Transactional(readOnly = true)
    public ServicePage<HospitalView> search(
            String query,
            HospitalType type,
            Boolean emergency,
            String status,
            int page,
            int size,
            boolean admin) {

        String q = ServiceInput.optional(query, 100, "검색어");
        String state = status == null || status.isBlank() ? null : status.strip().toUpperCase(Locale.ROOT);
        if (state != null && !Set.of("ACTIVE", "INACTIVE").contains(state))
            throw new ResponseStatusException(BAD_REQUEST, "운영 상태는 ACTIVE 또는 INACTIVE여야 합니다.");
        Specification<Hospital> spec =
                (root, cq, cb) -> {
                    var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (!admin) {
                        conditions.add(cb.equal(root.get("status"), "ACTIVE"));
                        conditions.add(cb.isNotNull(root.get("sourceUrl")));
                        conditions.add(cb.isNotNull(root.get("verifiedOn")));
                    } else if (state != null) {
                        conditions.add(cb.equal(root.get("status"), state));
                    }
                    if (type != null) conditions.add(cb.equal(root.get("hospitalType"), type));
                    if (emergency != null)
                        conditions.add(cb.equal(root.get("emergency"), emergency));
                    if (q != null) {
                        String pattern =
                                "%"
                                        + q.toLowerCase(Locale.ROOT)
                                                .replace("!", "!!")
                                                .replace("%", "!%")
                                                .replace("_", "!_")
                                        + "%";
                        conditions.add(
                                cb.or(
                                        cb.like(cb.lower(root.get("name")), pattern, '!'),
                                        cb.like(cb.lower(root.get("region")), pattern, '!'),
                                        cb.like(cb.lower(root.get("address")), pattern, '!')));
                    }
                    return cb.and(
                            conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        var paging =
                admin
                        ? ServiceInput.page(page, size, org.springframework.data.domain.Sort.Direction.ASC, "name", "hospitalId")
                        : ServiceInput.page(page, size, "verifiedOn", "hospitalId");
        return ServicePage.from(hospitals.findAll(spec, paging).map(HospitalView::from));
    }

    @Transactional
    public HospitalView create(HospitalRequest dto) {
        var d = validate(dto);
        String id = ServiceInput.uuid(dto.clientRequestId());
        var h = Hospital.builder().hospitalId(id).build();
        h.update(d);
        var existing = hospitals.findById(id);
        if (existing.isPresent()) {
            if (HospitalView.from(existing.get()).equals(HospitalView.from(h)))
                return HospitalView.from(existing.get());
            throw new ResponseStatusException(CONFLICT, "다른 정보에 사용된 요청 ID입니다. 목록을 확인해 주세요.");
        }
        entityManager.persist(h);
        entityManager.flush();
        audit.current("HOSPITAL_CREATE", id, h.getStatus());
        return HospitalView.from(h);
    }

    @Transactional
    public HospitalView update(String id, HospitalRequest dto) {
        var d = validate(dto);
        var h =
                hospitals
                        .lockById(id)
                        .orElseThrow(
                                () -> new ResponseStatusException(NOT_FOUND, "기관을 찾을 수 없습니다."));
        if (d.expectedVersion() == null || d.expectedVersion() != h.getVersion())
            throw new ResponseStatusException(CONFLICT, "기관 정보가 바뀌었습니다. 새로고침해 주세요.");
        h.update(d);
        hospitals.flush();
        audit.current("HOSPITAL_UPDATE", id, h.getStatus() + " v" + h.getVersion());
        return HospitalView.from(h);
    }

    private HospitalRequest validate(HospitalRequest d) {
        if (d.type() == null
                || d.nightCare() == null
                || d.emergency() == null
                || !Set.of("ACTIVE", "INACTIVE").contains(d.status() == null ? "" : d.status()))
            throw new ResponseStatusException(BAD_REQUEST, "기관 유형과 운영 정보를 확인해 주세요.");
        if (d.verifiedOn() == null
                || d.verifiedOn().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))
            throw new ResponseStatusException(BAD_REQUEST, "확인일은 오늘 또는 이전 날짜여야 합니다.");
        String tel = ServiceInput.optional(d.tel(), 20, "전화번호");
        if (tel != null && !tel.matches("[+0-9() -]{5,20}"))
            throw new ResponseStatusException(BAD_REQUEST, "전화번호 형식을 확인해 주세요.");
        return new HospitalRequest(
                ServiceInput.text(d.name(), 100, "기관명"),
                d.type(),
                ServiceInput.text(d.region(), 50, "지역"),
                ServiceInput.text(d.address(), 200, "주소"),
                tel,
                d.nightCare(),
                d.emergency(),
                ServiceInput.text(d.dataSource(), 50, "출처명"),
                ServiceInput.https(d.sourceUrl(), 1000),
                d.verifiedOn(),
                d.status(),
                d.expectedVersion(),
                d.clientRequestId());
    }
}
