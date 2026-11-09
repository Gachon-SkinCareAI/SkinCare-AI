package skincare.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

// 사용자 최근 활동 항목 DTO (내 정보/관리자 회원 상세 응답의 recent_activities 항목)
// ActivityDto 요청과 응답 데이터
public record ActivityDto(
        String activityType, // 활동 유형 (DIAGNOSIS, RECOVERY_LOG, INQUIRY)
        String description, // 활동 설명
        String targetId, // 관련 기록 ID (분석/회복/문의 ID)
        String createdAt, // 활동 일시 (yyyy.MM.dd HH:mm)
        @JsonIgnore LocalDateTime sortKey // 최신순 정렬용 (응답에 포함하지 않음)
        ) {}
