package skincare.external;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 공공데이터포털(data.go.kr) 인증키 정규화.
 *
 * <p>포털은 같은 키를 "Encoding"(%2B, %2F, %3D 포함)과 "Decoding"(+, /, = 포함) 두 형태로 보여 준다. .env에는 어느 쪽이든
 * 들어올 수 있으므로, 먼저 디코딩된 형태로 통일한 뒤 URL에 넣을 때 정확히 한 번만 인코딩한다. 인코딩된 키를 그대로 다시 인코딩하면
 * {@code %}가 {@code %25}가 되어 포털이 {@code SERVICE_KEY_IS_NOT_REGISTERED_ERROR}(코드 30)로 거부한다.
 */
final class ServiceKeys {
    private ServiceKeys() {}

    /** 따옴표, 끝의 {@code &}, 인코딩 여부를 정리해 디코딩된 키를 돌려준다. 키가 없으면 빈 문자열. */
    static String decoded(String raw) {
        if (raw == null) return "";
        String key = raw.strip();
        if (key.length() >= 2
                && ((key.startsWith("\"") && key.endsWith("\""))
                        || (key.startsWith("'") && key.endsWith("'")))) {
            key = key.substring(1, key.length() - 1).strip();
        }
        while (key.endsWith("&")) key = key.substring(0, key.length() - 1);
        // %XX가 있으면 인코딩된 키로 본다. 리터럴 '+'가 공백으로 바뀌지 않도록 먼저 %2B로 보호한다.
        if (key.matches(".*%[0-9A-Fa-f]{2}.*")) {
            try {
                key = URLDecoder.decode(key.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException malformed) {
                // 잘못된 % 시퀀스면 원문을 그대로 쓴다 (포털이 거부하므로 UNAUTHORIZED로 드러난다)
            }
        }
        return key;
    }

    /** URL 쿼리에 그대로 붙일 수 있도록 정확히 한 번 인코딩한 키. 키가 없으면 빈 문자열. */
    static String encoded(String raw) {
        String key = decoded(raw);
        return key.isEmpty() ? "" : URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
}
