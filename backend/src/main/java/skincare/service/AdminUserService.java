package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.user.entity.ENUM.PlanType;
import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserChangeHistory;
import skincare.dto.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.DiagnosisRepository;
import skincare.repository.UserChangeHistoryRepository;
import skincare.repository.UserRepository;
import skincare.security.TokenService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// 관리자 회원 관리 비즈니스 로직 처리
// 회원 목록/검색, 상세 프로필·분석 히스토리·최근 활동 조회, 계정 상태 및 이용 플랜 변경(변경 이력 저장)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final UserRepository userRepository;                           // 회원 데이터 베이스 접근용 Repository
    private final DiagnosisRepository diagnosisRepository;                 // 분석 통계 조회
    private final UserChangeHistoryRepository userChangeHistoryRepository; // 회원 변경 이력
    private final UserActivityService userActivityService;                 // 회원 활동 요약 집계
    private final TokenService tokenService;                               // 정지/비활성 시 토큰 폐기

    // 회원 목록 조회 (검색어, 이용 플랜, 계정 상태 필터 / 분석 건수와 최근 위험도 포함)
    public Page<AdminUserResponseDto> getUsers(String keyword, PlanType plan, UserStatus status, int page, int size) {
        Page<User> users = userRepository.findAll(UserRepository.adminSearch(keyword, plan, status),
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        // 현재 페이지 회원들의 분석 건수/최근 위험도를 한 번에 조회 (회원별 반복 조회 방지)
        List<String> userIds = users.getContent().stream().map(User::getUserId).toList();
        Map<String, Long> diagnosisCounts = new HashMap<>();
        Map<String, RiskLevel> latestRisks = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (Object[] row : diagnosisRepository.countGroupByUserIds(userIds)) {
                diagnosisCounts.put((String) row[0], (Long) row[1]);
            }
            for (Object[] row : diagnosisRepository.findLatestRiskByUserIds(userIds)) {
                latestRisks.put((String) row[0], (RiskLevel) row[1]);
            }
        }

        return users.map(user -> AdminUserResponseDto.of(user,
                diagnosisCounts.getOrDefault(user.getUserId(), 0L), latestRisks.get(user.getUserId())));
    }

    // 회원 현황 통계 (전체/Premium 전환율/일간·주간 활성 사용자/정지 회원 수)
    public Map<String, Object> getUserStats() {
        long total = userRepository.count();
        long premium = userRepository.countByPlan(PlanType.PREMIUM);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_users", total);
        stats.put("premium_users", Map.of(
                "count", premium,
                "conversion_rate", total == 0 ? "0.0%" : Math.round(premium * 1000.0 / total) / 10.0 + "%"));
        stats.put("dau", Map.of("count", userRepository.countByLastAccessAtAfter(LocalDate.now().atStartOfDay())));
        stats.put("wau", Map.of("count", userRepository.countByLastAccessAtAfter(LocalDate.now().minusDays(6).atStartOfDay())));
        stats.put("suspended_users", userRepository.countByStatus(UserStatus.SUSPENDED));
        return stats;
    }

    // 회원 상세 조회 (기본 정보, 통계, 계정 정보, 최근 활동, 분석 히스토리, 변경 이력)
    public Map<String, Object> getUserDetail(String userId) {
        User user = findUser(userId);
        Map<String, Object> activity = userActivityService.summarize(userId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_diagnoses", activity.get("total_diagnosis_count"));
        stats.put("total_recoveries", activity.get("total_recovery_count"));
        stats.put("total_inquiries", activity.get("total_inquiry_count"));
        stats.put("plan", user.getPlan());

        Map<String, Object> accountInfo = new LinkedHashMap<>();
        accountInfo.put("joined_at", format(user.getCreatedAt()));
        accountInfo.put("status", user.getStatus());
        accountInfo.put("last_access_at", format(user.getLastAccessAt()));
        accountInfo.put("latest_risk", activity.get("latest_risk_level"));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("user_info", UserResponseDto.from(user));
        detail.put("stats", stats);
        detail.put("account_info", accountInfo);
        detail.put("recent_activities", activity.get("recent_activities"));
        detail.put("diagnosis_history", diagnosisRepository.findTop10ByUserUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AdminDiagnosisResponseDto::summary).toList());
        detail.put("change_history", userChangeHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toHistory).toList());
        return detail;
    }

    // 회원 계정 상태 변경 (정지/비활성 시 발급된 Refresh Token 폐기 → 재로그인 불가)
    @Transactional
    public Map<String, Object> changeStatus(String adminId, String userId, AdminUserStatusUpdateRequestDto dto) {
        if (dto.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴(DELETED) 상태는 회원 탈퇴로만 변경할 수 있습니다.");
        }
        User user = findUser(userId);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN, "탈퇴한 회원의 계정 상태는 변경할 수 없습니다.");
        }

        UserStatus previous = user.getStatus();
        user.changeStatus(dto.getStatus());
        if (dto.getStatus() != UserStatus.ACTIVE) {
            tokenService.revokeAll(userId, "USER");
        }

        UserChangeHistory history = saveHistory(userId, "STATUS", previous.name(), dto.getStatus().name(), dto.getReason(), adminId);
        log.info("[Auth] 정상 처리, 회원 계정 상태 변경 (user_id: {}, {} → {}, admin_id: {})", userId, previous, dto.getStatus(), adminId);
        return toHistory(history);
    }

    // 회원 이용 플랜 변경
    @Transactional
    public Map<String, Object> changePlan(String adminId, String userId, AdminUserPlanUpdateRequestDto dto) {
        User user = findUser(userId);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN, "탈퇴한 회원의 이용 플랜은 변경할 수 없습니다.");
        }

        PlanType previous = user.getPlan();
        user.changePlan(dto.getPlan());

        UserChangeHistory history = saveHistory(userId, "PLAN", previous.name(), dto.getPlan().name(), dto.getReason(), adminId);
        log.info("[Auth] 정상 처리, 회원 이용 플랜 변경 (user_id: {}, {} → {}, admin_id: {})", userId, previous, dto.getPlan(), adminId);
        return toHistory(history);
    }

    // 변경 이력 저장
    private UserChangeHistory saveHistory(String userId, String type, String previous, String next, String reason, String adminId) {
        return userChangeHistoryRepository.saveAndFlush(UserChangeHistory.builder()
                .userId(userId)
                .changeType(type)
                .previousValue(previous)
                .newValue(next)
                .reason(reason)
                .changedBy(adminId)
                .build());
    }

    // 변경 이력 응답 변환
    private Map<String, Object> toHistory(UserChangeHistory history) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("user_id", history.getUserId());
        item.put("change_type", history.getChangeType());
        item.put("previous_value", history.getPreviousValue());
        item.put("new_value", history.getNewValue());
        item.put("reason", history.getReason());
        item.put("changed_by", history.getChangedBy());
        item.put("changed_at", format(history.getCreatedAt()));
        return item;
    }

    // 회원 조회 (없으면 USER-001)
    private User findUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    // 일시 표시 형식 변환
    private String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMATTER) : null;
    }
}
