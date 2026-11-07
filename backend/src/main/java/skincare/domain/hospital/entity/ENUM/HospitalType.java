package skincare.domain.hospital.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 의료기관 유형 (DERMATOLOGY: 피부과, GENERAL_HOSPITAL: 종합병원, PHARMACY: 약국, OTHER: 병원/의원)
@Getter
@RequiredArgsConstructor
public enum HospitalType {
    DERMATOLOGY("피부과"),
    GENERAL_HOSPITAL("종합병원"),
    PHARMACY("약국"),
    OTHER("병원/의원");

    private final String label; // 화면 표시용 한글 명칭
}
