package skincare.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// ServicePage 요청과 응답 데이터
public record ServicePage<T>(List<T> items, int page, int size, boolean hasNext, long total) {
    public static <T> ServicePage<T> from(Page<T> p) {
        return new ServicePage<>(
                p.getContent(), p.getNumber(), p.getSize(), p.hasNext(), p.getTotalElements());
    }
}
