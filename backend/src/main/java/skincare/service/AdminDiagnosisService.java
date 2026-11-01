package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.DiagnosisActionHistory;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.dto.AdminDiagnosisResponseDto;
import skincare.dto.AdminDiagnosisStatusUpdateRequestDto;
import skincare.dto.Paging;
import skincare.dto.StoredFileDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.DiagnosisRepository;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

// 관리자 AI 분석 로그 관리 관련 비즈니스 로직 처리 (설계서 6.3.3)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDiagnosisService {

    private final DiagnosisRepository diagnosisRepository; // 분석 기록 데이터 베이스 접근용 Repository
    private final DiagnosisService diagnosisService;       // 케어 가이드 변환 공통 로직
    private final FileStorageService fileStorageService;   // 상처 사진 파일 저장소
    private final JsonConverter jsonConverter;             // 문진 JSON 변환

    // 전체 사용자 분석 로그 목록 조회 (위험도/처리 상태 필터, 검색어, 페이지네이션)
    public Page<AdminDiagnosisResponseDto> getAllDiagnoses(RiskLevel riskLevel, DiagnosisStatus status,
                                                          String keyword, int page, int size) {
        Page<Diagnosis> diagnoses = diagnosisRepository.findAll(
                DiagnosisRepository.adminSearch(riskLevel, status, keyword),
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return diagnoses.map(AdminDiagnosisResponseDto::summary);
    }

    // 분석 로그 상세 조회 (사용자 정보, AI 원본 지표, 문진, 케어 가이드, 처리 이력)
    public AdminDiagnosisResponseDto getDiagnosisDetail(String diagnosisId) {
        Diagnosis diagnosis = findDiagnosis(diagnosisId);
        return AdminDiagnosisResponseDto.detail(diagnosis,
                diagnosisService.readCareGuide(diagnosis),
                jsonConverter.toMap(diagnosis.getQuestionnaire()));
    }

    // 분석 이미지 조회 (관리자)
    public StoredFileDto getDiagnosisImage(String diagnosisId) {
        Diagnosis diagnosis = findDiagnosis(diagnosisId);
        return new StoredFileDto(fileStorageService.load(diagnosis.getImageUrl()),
                fileStorageService.getMediaType(diagnosis.getImageUrl()));
    }

    // 분석건 처리 상태/검수 상태/관리자 의견 변경 (변경 관리자와 처리 시각을 감사 이력으로 저장)
    @Transactional
    public Map<String, Object> updateDiagnosisStatus(String diagnosisId, AdminDiagnosisStatusUpdateRequestDto dto,
                                                     String adminId) {
        Diagnosis diagnosis = findDiagnosis(diagnosisId);

        DiagnosisActionHistory history = diagnosis.updateByAdmin(
                dto.getStatus(), dto.getReviewStatus(), dto.getAdminNote(), adminId);

        log.info("[Analysis] 정상 처리, 분석 건 상태 변경 (diagnosis_id: {}, status: {} → {}, review_status: {}, admin_id: {})",
                diagnosisId, history.getPreviousStatus(), history.getCurrentStatus(), history.getReviewStatus(), adminId);

        // 설계서 7.3 updated_status_info 응답 형식
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosis_id", diagnosisId);
        result.put("previous_status", history.getPreviousStatus());
        result.put("current_status", diagnosis.getStatus());
        result.put("current_status_label", diagnosis.getStatus().getLabel());
        result.put("review_status", diagnosis.getReviewStatus());
        result.put("admin_note", diagnosis.getAdminNote());
        result.put("updated_by", adminId);
        result.put("updated_at", diagnosis.getReviewedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return result;
    }

    // 분석 기록 조회 (없으면 DIAGNOSIS-006)
    private Diagnosis findDiagnosis(String diagnosisId) {
        return diagnosisRepository.findDetailByDiagnosisId(diagnosisId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND));
    }
}
