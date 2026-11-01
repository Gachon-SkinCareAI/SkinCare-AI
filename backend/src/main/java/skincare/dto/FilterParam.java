package skincare.dto;

import skincare.exception.CustomException;
import skincare.exception.ErrorCode;

import java.util.Locale;

// 목록 조회 API의 Enum 필터 파라미터 변환 유틸리티
// 설계서 기준 필터 값 "ALL"(또는 미입력)은 전체 조회(null)로 처리
public final class FilterParam {

    private FilterParam() {
    }

    // 문자열 필터 값을 Enum으로 변환 (ALL/공백이면 null, 정의되지 않은 값이면 COMMON-005)
    public static <E extends Enum<E>> E toEnum(Class<E> enumType, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TYPE_VALUE, "지원하지 않는 필터 값입니다: " + value);
        }
    }
}
