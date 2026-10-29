package skincare.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HospitalSyncRequestDto {
    // 동기화 대상 스코프 (ex. "ALL", "DERMATOLOGY", "PHARMACY")
    private String targetScope;

    // 특정 지역만 동기화할 경우 (선택)
    private String regionCode;
}