package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import skincare.repository.AnalysisSessionRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
// OperationsService 비즈니스 로직 처리
public class OperationsService {
    private final AnalysisSessionRepository sessions;
    private static final List<String> STATES =
            List.of("CREATED", "QUESTIONNAIRE", "COMPLETED", "RETRY_REQUIRED");

    @Transactional(readOnly = true)
    public Map<String, Object> analyses(String status, int page, int size) {
        String selected = status == null || status.isBlank() ? null : status;
        if ((selected != null && !STATES.contains(selected))
                || page < 0
                || page > 10000
                || size < 1
                || size > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "분석 상태와 페이지 범위를 확인해 주세요.");
        var counts = new LinkedHashMap<String, Long>();
        STATES.forEach(state -> counts.put(state, 0L));
        for (var count : sessions.countStates()) counts.put(count.getStatus(), count.getTotal());
        var records = sessions.operations(selected, PageRequest.of(page, size));
        var items =
                records.getContent().stream()
                        .map(
                                row -> {
                                    var item = new LinkedHashMap<String, Object>();
                                    item.put("id", row.getId());
                                    item.put("status", row.getStatus());
                                    item.put("turn", row.getTurn());
                                    item.put(
                                            "errorCode",
                                            "AI_UNAVAILABLE".equals(row.getErrorCode())
                                                    ? "AI_UNAVAILABLE"
                                                    : null);
                                    item.put("createdAt", row.getCreatedAt());
                                    item.put("updatedAt", row.getUpdatedAt());
                                    return item;
                                })
                        .toList();
        return Map.of(
                "counts",
                counts,
                "items",
                items,
                "page",
                page,
                "size",
                size,
                "hasNext",
                records.hasNext(),
                "total",
                records.getTotalElements());
    }
}
