package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.dto.AdminDiagnosisResponseDto;
import skincare.dto.AdminDiagnosisStatusUpdateRequestDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.DiagnosisRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDiagnosisService {

    private final DiagnosisRepository diagnosisRepository;

    // 전체 사용자 진단건 목록 조회 (페이지네이션 및 상태 필터)
    public Page<AdminDiagnosisResponseDto> getAllDiagnoses(DiagnosisStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Diagnosis> diagnoses;

        if (status != null) {
            diagnoses = diagnosisRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            diagnoses = diagnosisRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return diagnoses.map(AdminDiagnosisResponseDto::fromEntity);
    }

    // 진단건 상세 내용 조회
    public AdminDiagnosisResponseDto getDiagnosisDetail(String diagnosisId) {
        Diagnosis diagnosis = diagnosisRepository.findDetailByDiagnosisId(diagnosisId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND, "해당 진단 기록을 찾을 수 없습니다: " + diagnosisId));

        return AdminDiagnosisResponseDto.fromEntity(diagnosis);
    }

    // 진단건 처리 상태 및 관리자 조치 메모 변경
    @Transactional
    public AdminDiagnosisResponseDto updateDiagnosisStatus(String diagnosisId, AdminDiagnosisStatusUpdateRequestDto dto) {
        Diagnosis diagnosis = diagnosisRepository.findDetailByDiagnosisId(diagnosisId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND, "해당 진단 기록을 찾을 수 없습니다: " + diagnosisId));

        // Diagnosis 엔티티 내부의 updateStatus 메서드 활용
        diagnosis.updateStatus(dto.getStatus(), dto.getAdminNote());

        log.info("[Admin Diagnosis] 진단 상태 변경 완료 - DiagnosisId: {}, Status: {}", diagnosisId, dto.getStatus());

        return AdminDiagnosisResponseDto.fromEntity(diagnosis);
    }
}