package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.ai.entity.AiModelMetrics;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.dto.ai.AiHealthResponseDto;
import skincare.external.AiServiceClient;
import skincare.repository.AiModelMetricsRepository;
import skincare.repository.DiagnosisRepository;
import skincare.repository.InquiryRepository;
import skincare.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

// 관리자 대시보드 지표 및 통계 조회 비즈니스 로직 처리
// 서비스 DB 통계(회원/분석/위험도/문의)와 AI 운영 정보(/ai/health, 모델 성능 지표)를 조합하여 제공
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// AdminDashboardService 비즈니스 로직 처리
public class AdminDashboardService {

    private static final int MAX_CHART_DAYS = 31; // 일별 차트 최대 조회 기간
    private static final int MAX_EMERGENCY_LIMIT = 20; // 응급 케이스 최대 조회 건수

    private final UserRepository userRepository; // 회원 통계
    private final DiagnosisRepository diagnosisRepository; // 분석/위험도 통계
    private final InquiryRepository inquiryRepository; // 문의 현황
    private final AiModelMetricsRepository aiModelMetricsRepository; // AI 모델 성능 지표
    private final AiServiceClient aiServiceClient; // AI 서버 상태 조회

    // 대시보드 요약 통계 (총 사용자, 오늘 분석, 응급 케이스, AI 성능, AI 서버 상태, 문의 현황)
    public Map<String, Object> getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // 회원: 전체 수, 이번 달 신규 가입, 지난달까지 대비 증가율
        long totalUsers = userRepository.count();
        long monthlyIncrease = userRepository.countByCreatedAtBetween(monthStart, tomorrowStart);
        long usersBeforeMonth = userRepository.countByCreatedAtBefore(monthStart);

        // 분석: 오늘/어제 건수
        long todayDiagnoses =
                diagnosisRepository.countByCreatedAtBetween(todayStart, tomorrowStart);
        long yesterdayDiagnoses =
                diagnosisRepository.countByCreatedAtBetween(yesterdayStart, todayStart);

        // 응급: 오늘/어제 응급 위험도 건수
        long todayEmergency =
                diagnosisRepository.countByRiskLevelAndCreatedAtBetween(
                        RiskLevel.EMERGENCY, todayStart, tomorrowStart);
        long yesterdayEmergency =
                diagnosisRepository.countByRiskLevelAndCreatedAtBetween(
                        RiskLevel.EMERGENCY, yesterdayStart, todayStart);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put(
                "total_users",
                Map.of(
                        "count", totalUsers,
                        "growth_rate", growthRate(monthlyIncrease, usersBeforeMonth),
                        "monthly_increase", monthlyIncrease));
        stats.put(
                "today_diagnoses",
                Map.of(
                        "count",
                        todayDiagnoses,
                        "growth_rate",
                        growthRate(todayDiagnoses - yesterdayDiagnoses, yesterdayDiagnoses),
                        "daily_increase",
                        todayDiagnoses - yesterdayDiagnoses));
        stats.put(
                "emergency_cases",
                Map.of(
                        "count",
                        todayEmergency,
                        "increase",
                        todayEmergency - yesterdayEmergency,
                        "notice",
                        todayEmergency > 0 ? "즉시 처리 필요" : "응급 케이스 없음"));
        stats.put("ai_accuracy", aiPerformanceSummary());
        stats.put("ai_server", aiServerSummary());
        stats.put(
                "inquiries",
                Map.of(
                        "waiting_count", inquiryRepository.countByStatus(InquiryStatus.WAITING),
                        "total_count", inquiryRepository.count()));
        return stats;
    }

    // 일별 분석 건수 추이 (최근 days일, 최대 31일)
    public Map<String, Object> getDailyChart(int days) {
        int period = Math.min(Math.max(days, 1), MAX_CHART_DAYS);
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> chart = new ArrayList<>();
        long total = 0;
        for (int i = period - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long count =
                    diagnosisRepository.countByCreatedAtBetween(
                            date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            total += count;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put(
                    "day_of_week",
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN));
            item.put("date", date.toString());
            item.put("count", count);
            chart.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_count", total);
        result.put("daily_chart_data", chart);
        return result;
    }

    // 특정 날짜의 위험도 분포 (관찰/경고/응급/판단 불가 건수 및 비율)
    public Map<String, Object> getRiskDistribution(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        Map<RiskLevel, Long> counts = new EnumMap<>(RiskLevel.class);
        long total = 0;
        for (RiskLevel level : RiskLevel.values()) {
            long count = diagnosisRepository.countByRiskLevelAndCreatedAtBetween(level, start, end);
            counts.put(level, count);
            total += count;
        }

        Map<String, Object> distribution = new LinkedHashMap<>();
        for (RiskLevel level : RiskLevel.values()) {
            long count = counts.get(level);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(
                    "level_name",
                    level.getLabel()
                            + " ("
                            + level.name().charAt(0)
                            + level.name().substring(1).toLowerCase()
                            + ")");
            item.put("count", count);
            item.put("percentage", total == 0 ? 0.0 : Math.round(count * 1000.0 / total) / 10.0);
            distribution.put(level.name().toLowerCase(), item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date.toString());
        result.put("total_count", total);
        result.put("risk_distribution", distribution);
        return result;
    }

    // 최근 응급 케이스 목록 (최신순)
    public List<Map<String, Object>> getEmergencyCases(int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_EMERGENCY_LIMIT);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("MM.dd HH:mm");

        List<Map<String, Object>> cases = new ArrayList<>();
        for (Diagnosis d :
                diagnosisRepository.findRecentByRiskLevel(
                        RiskLevel.EMERGENCY, PageRequest.of(0, size))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("diagnosis_id", d.getDiagnosisId());
            item.put("user_id", d.getUser().getUserId());
            item.put("nickname", d.getUser().getNickname());
            item.put(
                    "time",
                    d.getCreatedAt() != null ? d.getCreatedAt().format(timeFormatter) : null);
            item.put(
                    "diagnosis_summary",
                    d.getSuspectedDisease() + " · " + d.getBodyPart().getLabel());
            item.put("status", d.getStatus());
            item.put("action_status", d.getStatus().getLabel());
            item.put(
                    "needs_action",
                    d.getStatus() == DiagnosisStatus.PENDING
                            || d.getStatus() == DiagnosisStatus.IN_PROGRESS);
            cases.add(item);
        }
        return cases;
    }

    // AI 모델 성능 요약 (macro_f1 우선, 없으면 classification_accuracy / 주요 위험 유형 burn_recall)
    private Map<String, Object> aiPerformanceSummary() {
        Optional<AiModelMetrics> main =
                aiModelMetricsRepository
                        .findFirstByMetricKeyOrderByIdDesc("macro_f1")
                        .or(
                                () ->
                                        aiModelMetricsRepository.findFirstByMetricKeyOrderByIdDesc(
                                                "classification_accuracy"));
        Optional<AiModelMetrics> recall =
                aiModelMetricsRepository.findFirstByMetricKeyOrderByIdDesc("burn_recall");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("metric_key", main.map(AiModelMetrics::getMetricKey).orElse(null));
        summary.put("value", main.map(AiModelMetrics::getMetricValue).orElse(null));
        summary.put("unit", main.map(AiModelMetrics::getUnit).orElse(null));
        summary.put("growth_rate", main.map(AiModelMetrics::getChangeValue).orElse(null));
        summary.put("burn_recall", recall.map(AiModelMetrics::getMetricValue).orElse(null));
        return summary;
    }

    // AI 서버 상태 및 현재 모델 버전 (AI 서버 연결 실패 시 unreachable)
    private Map<String, Object> aiServerSummary() {
        AiHealthResponseDto health = aiServiceClient.health();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", health.status());
        summary.put("models", health.models());
        summary.put("inflight", health.inflight());
        return summary;
    }

    // 증가율 문자열 계산 (ex. +10.6%, 기준값이 0이면 신규 데이터 여부로 표시)
    private String growthRate(long increase, long base) {
        if (base == 0) {
            return increase > 0 ? "+100.0%" : "0.0%";
        }
        double rate = Math.round(increase * 1000.0 / base) / 10.0;
        return (rate >= 0 ? "+" : "") + rate + "%";
    }
}
