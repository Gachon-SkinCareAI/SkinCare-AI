package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.diagnosis.entity.Recovery;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.dto.ActivityDto;
import skincare.repository.DiagnosisRepository;
import skincare.repository.InquiryRepository;
import skincare.repository.RecoveryRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// 사용자 활동 요약(분석/회복 기록/문의 건수, 최신 위험도, 최근 활동) 집계 서비스
// 사용자 내 정보 조회(GET /api/wc/users/me)와 관리자 회원 상세 조회에서 공통으로 사용
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// UserActivityService 비즈니스 로직 처리
public class UserActivityService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final int RECENT_ACTIVITY_LIMIT = 5;

    private final DiagnosisRepository diagnosisRepository; // 분석 기록 데이터 베이스 접근용 Repository
    private final RecoveryRepository recoveryRepository; // 회복 기록 데이터 베이스 접근용 Repository
    private final InquiryRepository inquiryRepository; // 문의 데이터 베이스 접근용 Repository

    // 사용자 활동 요약 생성 (total_diagnosis_count, total_recovery_count, total_inquiry_count,
    // latest_risk_level, recent_activities)
    public Map<String, Object> summarize(String userId) {
        List<Diagnosis> recentDiagnoses =
                diagnosisRepository.findTop5ByUserUserIdOrderByCreatedAtDesc(userId);
        RiskLevel latestRisk =
                recentDiagnoses.isEmpty() ? null : recentDiagnoses.get(0).getRiskLevel();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_diagnosis_count", diagnosisRepository.countByUserUserId(userId));
        summary.put("total_recovery_count", recoveryRepository.countByDiagnosisUserUserId(userId));
        summary.put("total_inquiry_count", inquiryRepository.countByUserUserId(userId));
        summary.put("latest_risk_level", latestRisk);
        summary.put("latest_risk_level_label", latestRisk != null ? latestRisk.getLabel() : null);
        summary.put("recent_activities", recentActivities(userId, recentDiagnoses));
        return summary;
    }

    // 분석/회복 기록/문의를 합쳐 최신순 최근 활동 목록 생성
    private List<ActivityDto> recentActivities(String userId, List<Diagnosis> recentDiagnoses) {
        List<ActivityDto> activities = new ArrayList<>();

        for (Diagnosis d : recentDiagnoses) {
            activities.add(
                    new ActivityDto(
                            "DIAGNOSIS",
                            "상처 분석을 완료했습니다. (" + d.getSuspectedDisease() + ")",
                            d.getDiagnosisId(),
                            format(d.getCreatedAt()),
                            d.getCreatedAt()));
        }
        for (Recovery r :
                recoveryRepository.findTop5ByDiagnosisUserUserIdOrderByCreatedAtDesc(userId)) {
            activities.add(
                    new ActivityDto(
                            "RECOVERY_LOG",
                            "회복 기록 사진을 추가했습니다.",
                            r.getRecoveryId(),
                            format(r.getCreatedAt()),
                            r.getCreatedAt()));
        }
        for (Inquiry i : inquiryRepository.findTop5ByUserUserIdOrderByCreatedAtDesc(userId)) {
            activities.add(
                    new ActivityDto(
                            "INQUIRY",
                            "1:1 문의를 작성했습니다. (" + i.getTitle() + ")",
                            i.getInquiryId(),
                            format(i.getCreatedAt()),
                            i.getCreatedAt()));
        }

        return activities.stream()
                .filter(a -> a.sortKey() != null)
                .sorted(Comparator.comparing(ActivityDto::sortKey).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    // 사용자 활동 일시 표시 변환
    private String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMATTER) : null;
    }
}
