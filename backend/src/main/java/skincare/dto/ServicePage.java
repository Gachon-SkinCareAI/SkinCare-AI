package skincare.dto;
import java.util.List;
import org.springframework.data.domain.Page;
public record ServicePage<T>(List<T> items, int page, int size, boolean hasNext, long total) {
    public static <T> ServicePage<T> from(Page<T> p) { return new ServicePage<>(p.getContent(),p.getNumber(),p.getSize(),p.hasNext(),p.getTotalElements()); }
}
