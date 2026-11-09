package skincare.dto;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.user.entity.ENUM.PlanType;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.domain.user.entity.User;

import java.time.format.DateTimeFormatter;

// 관리자 회원 목록 항목 응답 DTO (GET /api/wc/admin/users 응답의 user_list 항목)
@Getter
@Builder
// AdminUserResponseDto 요청과 응답 데이터
public class AdminUserResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private String userId; // 회원 ID
    private String nickname; // 닉네임
    private String email; // 이메일
    private Provider provider; // 가입 경로 (LOCAL, GOOGLE)
    private String joinedAt; // 가입일
    private String lastAccessAt; // 최종 접속일
    private long totalDiagnoses; // 전체 분석 건수
    private RiskLevel latestRisk; // 가장 최근 분석 위험도
    private PlanType plan; // 이용 플랜
    private UserStatus status; // 계정 상태

    // 회원 엔티티와 분석 통계로 목록 항목 생성
    public static AdminUserResponseDto of(User user, long totalDiagnoses, RiskLevel latestRisk) {
        return AdminUserResponseDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .provider(user.getProvider())
                .joinedAt(
                        user.getCreatedAt() != null ? user.getCreatedAt().format(FORMATTER) : null)
                .lastAccessAt(
                        user.getLastAccessAt() != null
                                ? user.getLastAccessAt().format(FORMATTER)
                                : null)
                .totalDiagnoses(totalDiagnoses)
                .latestRisk(latestRisk)
                .plan(user.getPlan())
                .status(user.getStatus())
                .build();
    }
}
