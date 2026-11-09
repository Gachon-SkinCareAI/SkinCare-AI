package skincare.service;

import skincare.exception.CustomException;
import skincare.exception.ErrorCode;

// ConsentPolicy 비즈니스 로직 처리
public final class ConsentPolicy {
    private ConsentPolicy() {}

    public static final String HEALTH_VERSION = "2026-11-03";
    public static final String TRAINING_VERSION = "2026-11-04";
    public static final String GUEST_VERSION = "2026-11-04";
    public static final String LOCATION_VERSION = "2026-11-04";
    public static final String VERSION = "2026-11-03";

    public static void validate(
            boolean terms, boolean privacy, String termsVersion, String privacyVersion) {
        if (!terms
                || !privacy
                || !VERSION.equals(termsVersion)
                || !VERSION.equals(privacyVersion)) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE, "현재 버전의 필수 약관과 개인정보 처리방침에 직접 동의해야 합니다.");
        }
    }
}
