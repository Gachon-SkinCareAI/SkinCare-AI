package skincare.domain.global;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// [공통] 직접 할당(@Id) 방식 엔티티의 고유 ID 생성 유틸리티
// 형식: {접두어}-{yyyyMMdd}-{HHmmss}-{UUID 앞 12자리} (ex. DX-20261101-153022-3fa85f6457a1)
// 기존 3자리 난수(900가지)는 같은 초에 ID가 겹칠 수 있고, 겹친 ID로 save() 시 기존 기록을 덮어쓰므로
// UUID 기반 48bit 난수를 사용하여 충돌 가능성을 제거 (최대 길이 32자, 컬럼 길이 36 이내)
public final class IdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private IdGenerator() {
    }

    // 접두어(DX, INQ, ANS 등)를 붙인 고유 ID 생성
    public static String generate(String prefix) {
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return prefix + "-" + LocalDateTime.now().format(FORMATTER) + "-" + randomPart;
    }
}
