package skincare.domain.hospital.entity;

import skincare.domain.global.BaseTimeEntity;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import jakarta.persistence.*;
import lombok.*;

// [의료기관 데이터] 공공데이터 OpenAPI 연동 피부과 병원 및 약국 데이터 엔티티
@Entity
@Table(name = "hospitals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Hospital extends BaseTimeEntity {

    // 기관 고유 ID
    @Id
    @Column(name = "hospital_id", length = 255)
    private String hospitalId;

    // 병원/약국 명칭
    @Column(nullable = false, length = 100)
    private String name;

    // 기관 유형 (DERMATOLOGY: 피부과, GENERAL_HOSPITAL: 종합병원, PHARMACY: 약국)
    @Enumerated(EnumType.STRING)
    @Column(name = "hospital_type", nullable = false, length = 30)
    private HospitalType hospitalType;

    // 행정구역 지역명 (ex. 서울특별시 강남구)
    @Column(nullable = false, length = 50)
    private String region;

    // 도로명/지번 상세 주소
    @Column(nullable = false, length = 200)
    private String address;

    // 대표 전화번호
    @Column(length = 20)
    private String tel;

    // 야간 진료 여부
    @Column(name = "is_night_care", nullable = false)
    private boolean nightCare;

    // 응급실 운영 여부
    @Column(name = "is_emergency", nullable = false)
    private boolean emergency;

    // 데이터 제공 출처 (ex. 공공데이터 OpenAPI)
    @Column(name = "data_source", length = 50)
    @Builder.Default
    private String dataSource = "공공데이터 OpenAPI";

    // 영업/운영 상태 (ACTIVE: 운영중, INACTIVE: 폐업/휴업)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    // 동기화 시 데이터 갱신 메서드
    public void updateHospitalInfo(String name, String address, String tel, boolean nightCare, boolean emergency) {
        this.name = name;
        this.address = address;
        this.tel = tel;
        this.nightCare = nightCare;
        this.emergency = emergency;
    }
}