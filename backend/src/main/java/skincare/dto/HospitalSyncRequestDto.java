package skincare.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 관리자 공공데이터 의료기관 동기화 요청 DTO (POST /api/wc/admin/hospitals/sync)
@Getter
@Setter
@NoArgsConstructor
// HospitalSyncRequestDto 요청과 응답 데이터
public class HospitalSyncRequestDto {
    // 동기화 대상 스코프 (ALL: 병원+약국(기본), HOSPITAL: 병원만, PHARMACY: 약국만)
    private String targetScope;

    // 특정 지역만 동기화할 경우 시도코드 (ex. 110000, 선택)
    // 지역 지정 시 해당 지역 외 기관의 운영 중지 처리는 하지 않음
    private String regionCode;
}
