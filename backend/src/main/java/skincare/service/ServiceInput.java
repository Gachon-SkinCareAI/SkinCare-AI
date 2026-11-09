package skincare.service;

import static org.springframework.http.HttpStatus.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// ServiceInput 비즈니스 로직 처리
public final class ServiceInput {
    private ServiceInput() {}

    public static String text(String value, int max, String label) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new ResponseStatusException(BAD_REQUEST, label + "을(를) 1~" + max + "자로 입력해 주세요.");
        return value.strip();
    }

    public static String optional(String value, int max, String label) {
        return value == null || value.isBlank() ? null : text(value, max, label);
    }

    public static String uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value))
                throw new IllegalArgumentException();
            return value.toLowerCase();
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "요청 ID 형식을 확인해 주세요.");
        }
    }

    public static PageRequest page(int page, int size, String... fields) {
        if (page < 0 || page > 10000 || size < 1 || size > 50)
            throw new ResponseStatusException(BAD_REQUEST, "페이지 범위를 확인해 주세요.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, fields));
    }

    public static String https(String value, int max) {
        String result = text(value, max, "출처 URL");
        try {
            var u = URI.create(result);
            if (!"https".equalsIgnoreCase(u.getScheme())
                    || u.getHost() == null
                    || u.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "올바른 HTTPS 출처 주소를 입력해 주세요.");
        }
        return result;
    }

    public static void password(String value) {
        if (value == null
                || value.length() < 8
                || value.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(
                    BAD_REQUEST, "새 비밀번호는 8자 이상, UTF-8 기준 72바이트 이하여야 합니다.");
    }
}
