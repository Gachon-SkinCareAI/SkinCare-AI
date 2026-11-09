package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.DiagnosisRepository;

// 분석 기록 부분 갱신 전용 컴포넌트
// AI 서버 호출은 트랜잭션 밖에서 수행하고, 결과 반영만 짧은 트랜잭션으로 처리하기 위해 별도 Bean으로 분리
// (같은 클래스 내부 호출은 @Transactional 프록시가 적용되지 않으므로 분리 필요)
@Component
@RequiredArgsConstructor
// DiagnosisUpdater 비즈니스 로직 처리
public class DiagnosisUpdater {

    private final DiagnosisRepository diagnosisRepository; // 분석 기록 데이터 베이스 접근용 Repository

    // 문진 진행 상태 저장
    @Transactional
    public Diagnosis saveQuestionnaire(String diagnosisId, String questionnaireJson) {
        Diagnosis diagnosis = find(diagnosisId);
        diagnosis.updateQuestionnaire(questionnaireJson);
        return diagnosis;
    }

    // 문진 완료 후 재평가 결과(위험도, 규칙, 케어 카드)와 최종 문진 내용 저장
    @Transactional
    public Diagnosis saveReassessment(
            String diagnosisId,
            String questionnaireJson,
            RiskLevel riskLevel,
            String ruleIds,
            String appliedCardIds,
            String careCards,
            String firstAidGuide,
            String precautionsGuide) {
        Diagnosis diagnosis = find(diagnosisId);
        diagnosis.updateQuestionnaire(questionnaireJson);
        diagnosis.applyReassessment(
                riskLevel, ruleIds, appliedCardIds, careCards, firstAidGuide, precautionsGuide);
        return diagnosis;
    }

    // 분석 기록 조회 (사용자 소유 검증은 호출 전 단계에서 완료)
    private Diagnosis find(String diagnosisId) {
        return diagnosisRepository
                .findDetailByDiagnosisId(diagnosisId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND));
    }
}
