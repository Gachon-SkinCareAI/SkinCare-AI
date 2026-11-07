package skincare.domain.hospital.entity;

import skincare.domain.global.BaseTimeEntity;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import jakarta.persistence.*;
import lombok.*;
import java.util.Objects;

// [의료기관 데이터] 공공데이터 OpenAPI 연동 피부과 병원 및 약국 데이터 엔티티
@Entity
@Table(name = "hospitals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Hospital extends BaseTimeEntity {

    @Version @Column(nullable=false)
    private long version;
    @Column(name="source_url",length=1000)
    private String sourceUrl;
    @Column(name="verified_on")
    private java.time.LocalDate verifiedOn;

    public void update(skincare.dto.HospitalRequest d) {
        name=d.name();hospitalType=d.type();region=d.region();address=d.address();tel=d.tel();
        nightCare=d.nightCare();emergency=d.emergency();dataSource=d.dataSource();sourceUrl=d.sourceUrl();verifiedOn=d.verifiedOn();status=d.status();
    }

    // 기관 고유 ID
    @Id
    @Column(name = "hospital_id", length = 50)
    private String hospitalId; // ex. HIRA-110001

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
public static final String STATUS_ACTIVE="ACTIVE", STATUS_INACTIVE="INACTIVE", STATUS_HIDDEN="HIDDEN";
 @Column private Double latitude;
 @Column private Double longitude;
 @Column(name="weekday_hours",length=50) private String weekdayHours;
 @Column(name="saturday_hours",length=50) private String saturdayHours;
 @Column(name="sunday_holiday_hours",length=50) private String sundayHolidayHours;
    public boolean updateFrom(Hospital source) {
        boolean hidden = STATUS_HIDDEN.equals(status);
        boolean changed = !Objects.equals(name, source.name)
                || hospitalType != source.hospitalType
                || !Objects.equals(region, source.region)
                || !Objects.equals(address, source.address)
                || !Objects.equals(tel, source.tel)
                || !Objects.equals(latitude, source.latitude)
                || !Objects.equals(longitude, source.longitude)
                || !Objects.equals(weekdayHours, source.weekdayHours)
                || !Objects.equals(saturdayHours, source.saturdayHours)
                || !Objects.equals(sundayHolidayHours, source.sundayHolidayHours)
                || nightCare != source.nightCare
                || emergency != source.emergency
                || (!hidden && !STATUS_ACTIVE.equals(status));

        if (changed) {
            this.name = source.name;
            this.hospitalType = source.hospitalType;
            this.region = source.region;
            this.address = source.address;
            this.tel = source.tel;
            this.latitude = source.latitude;
            this.longitude = source.longitude;
            this.weekdayHours = source.weekdayHours;
            this.saturdayHours = source.saturdayHours;
            this.sundayHolidayHours = source.sundayHolidayHours;
            this.nightCare = source.nightCare;
            this.emergency = source.emergency;
            this.dataSource = source.dataSource;
            if (!hidden) {
                this.status = STATUS_ACTIVE;
            }
        }
        return changed;
    }

    // 공공데이터에서 제외된 기관 운영 중지 처리
    public void deactivate() {
        this.status = STATUS_INACTIVE;
    }

    // 관리자 운영 상태 변경 (ACTIVE: 노출, HIDDEN: 숨김 - 다음 동기화에서도 숨김 유지)
    public void changeStatusByAdmin(String status) {
        this.status = status;
    }


}