package skincare.domain.hospital.entity;

import org.springframework.data.domain.Persistable;
import skincare.domain.global.BaseTimeEntity;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.util.Objects;

// [의료기관 데이터] 공공데이터 OpenAPI 연동 피부과 병원 및 약국 데이터 엔티티
// Persistable 구현: 직접 할당 ID 엔티티를 save() 할 때 기존 행 조회(SELECT) 없이 바로 INSERT 하도록 신규 여부를 직접 판단
// (공공데이터 동기화 시 수만 건 저장에서 건별 SELECT가 발생하는 것을 방지)
@Entity
@Table(name = "hospitals", indexes = {
        @Index(name = "idx_hospital_location", columnList = "latitude, longitude"),
        @Index(name = "idx_hospital_type_status", columnList = "hospital_type, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Hospital extends BaseTimeEntity implements Persistable<String> {

    // 기관 고유 ID
    @Id
    @Column(name = "hospital_id", length = 255)
    private String hospitalId;

    // 병원/약국 명칭
    @Column(nullable = false, length = 100)
    private String name;

    // 기관 유형 (DERMATOLOGY: 피부과, GENERAL_HOSPITAL: 종합병원, PHARMACY: 약국, OTHER: 병원/의원)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // MySQL ENUM 타입 대신 VARCHAR로 매핑 (Enum 값 추가 시 DDL 변경 불필요)
    @Column(name = "hospital_type", nullable = false, length = 30)
    private HospitalType hospitalType;

    // 행정구역 지역명 (ex. 서울 강남구)
    @Column(nullable = false, length = 50)
    private String region;

    // 도로명/지번 상세 주소
    @Column(nullable = false, length = 200)
    private String address;

    // 대표 전화번호
    @Column(length = 20)
    private String tel;

    // 위도 (WGS84, 공공데이터 좌표(Y))
    @Column
    private Double latitude;

    // 경도 (WGS84, 공공데이터 좌표(X))
    @Column
    private Double longitude;

    // 평일 진료시간 (ex. 09:00 - 20:00)
    @Column(name = "weekday_hours", length = 50)
    private String weekdayHours;

    // 토요일 진료시간 (ex. 09:00 - 13:00, 휴무)
    @Column(name = "saturday_hours", length = 50)
    private String saturdayHours;

    // 일요일/공휴일 진료시간 (ex. 휴무)
    @Column(name = "sunday_holiday_hours", length = 50)
    private String sundayHolidayHours;

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

    // 영업/운영 상태 (ACTIVE: 운영중, INACTIVE: 폐업/휴업 또는 공공데이터에서 제외됨)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    // 신규 엔티티 여부 (DB 컬럼 아님, 조회/저장 이후에는 false)
    @Transient
    @Builder.Default
    private boolean newEntity = true;

    // 동기화 데이터로 기관 정보 갱신 (변경된 항목이 있으면 true 반환, 폐업 처리된 기관은 다시 운영 상태로 복구)
    public boolean updateFrom(Hospital source) {
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
                || !"ACTIVE".equals(status);

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
            this.status = "ACTIVE";
        }
        return changed;
    }

    // 공공데이터에서 제외된 기관 운영 중지 처리
    public void deactivate() {
        this.status = "INACTIVE";
    }

    // Persistable: 엔티티 식별자
    @Override
    public String getId() {
        return hospitalId;
    }

    // Persistable: 신규 엔티티 여부 (true면 save() 시 merge 대신 persist)
    @Override
    public boolean isNew() {
        return newEntity;
    }

    // DB 조회/저장 이후 신규 엔티티 표시 해제
    @PostLoad
    @PostPersist
    protected void markNotNew() {
        this.newEntity = false;
    }
}
