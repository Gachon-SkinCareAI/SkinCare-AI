package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.user.entity.User;
import skincare.dto.UserInquiryCreateRequestDto;
import skincare.dto.UserInquiryResponseDto;
import skincare.dto.UserInquiryUpdateRequestDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.DiagnosisRepository;
import skincare.repository.InquiryRepository;
import skincare.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserInquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;
    private final DiagnosisRepository diagnosisRepository;

    // 사용자 문의 신규 등록 (AI 진단기록 선택 연동 가능)
    @Transactional
    public UserInquiryResponseDto createInquiry(String userId, UserInquiryCreateRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // AI 상처 진단 기록 참조 시 해당 진단이 존재하는지 및 사용자 소유인지 확인
        Diagnosis diagnosis = null;
        if (dto.getDiagnosisId() != null) {
            diagnosis = diagnosisRepository.findById(dto.getDiagnosisId())
                    .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND));

            if (!diagnosis.getUser().getUserId().equals(userId)) {
                throw new CustomException(ErrorCode.ACCESS_DENIED, "본인의 AI 진단 기록만 문의에 참조할 수 있습니다.");
            }
        }

        // 고유 문의 ID 생성 (ex. INQ-20261029-153022-123)
        String inquiryId = "INQ-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + (int)(Math.random() * 900 + 100);

        Inquiry inquiry = Inquiry.builder()
                .inquiryId(inquiryId)
                .user(user)
                .diagnosis(diagnosis)
                .inquiryType(dto.getInquiryType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .status(InquiryStatus.WAITING)
                .build();

        Inquiry savedInquiry = inquiryRepository.save(inquiry);
        log.info("[User Inquiry] 문의 작성 완료 - UserId: {}, InquiryId: {}", userId, inquiryId);

        return UserInquiryResponseDto.fromEntity(savedInquiry);
    }

    // 본인이 작성한 모든 문의 목록 조회 (페이지네이션)
    public Page<UserInquiryResponseDto> getMyInquiries(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Inquiry> inquiries = inquiryRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable);

        return inquiries.map(UserInquiryResponseDto::fromEntity);
    }

    // 3. 문의 상세 조회
    public UserInquiryResponseDto getMyInquiryDetail(String userId, String inquiryId) {
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndUserId(inquiryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND, "해당 문의를 찾을 수 없거나 접근 권한이 없습니다."));

        return UserInquiryResponseDto.fromEntity(inquiry);
    }

    // 4. 문의 수정 (관리자 답변 작성 시 수정 불가)
    @Transactional
    public UserInquiryResponseDto updateInquiry(String userId, String inquiryId, UserInquiryUpdateRequestDto dto) {
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndUserId(inquiryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND, "해당 문의를 찾을 수 없거나 접근 권한이 없습니다."));

        // 관리자가 이미 답변을 작성한 경우 수정 불가
        if (inquiry.getStatus() == InquiryStatus.COMPLETED || inquiry.getAnswer() != null) {
            throw new CustomException(ErrorCode.INQUIRY_ALREADY_ANSWERED, "이미 관리자 답변이 완료된 문의는 수정할 수 없습니다.");
        }

        // 진단 정보 변경 시 확인
        Diagnosis diagnosis = null;
        if (dto.getDiagnosisId() != null) {
            diagnosis = diagnosisRepository.findById(dto.getDiagnosisId())
                    .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND));

            if (!diagnosis.getUser().getUserId().equals(userId)) {
                throw new CustomException(ErrorCode.ACCESS_DENIED, "본인의 AI 진단 기록만 문의에 참조할 수 있습니다.");
            }
        }

        inquiry.updateInquiry(dto.getInquiryType(), dto.getTitle(), dto.getContent(), diagnosis);
        log.info("[User Inquiry] 문의 수정 완료 - InquiryId: {}", inquiryId);

        return UserInquiryResponseDto.fromEntity(inquiry);
    }

    /**
     * 5. 문의 삭제 (관리자 답변 작성 시 삭제 불가)
     */
    @Transactional
    public void deleteInquiry(String userId, String inquiryId) {
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndUserId(inquiryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND, "해당 문의를 찾을 수 없거나 접근 권한이 없습니다."));

        // 관리자가 이미 답변을 작성한 경우 삭제 불가
        if (inquiry.getStatus() == InquiryStatus.COMPLETED || inquiry.getAnswer() != null) {
            throw new CustomException(ErrorCode.INQUIRY_ALREADY_ANSWERED, "이미 관리자 답변이 완료된 문의는 삭제할 수 없습니다.");
        }

        inquiryRepository.delete(inquiry);
        log.info("[User Inquiry] 문의 삭제 완료 - InquiryId: {}", inquiryId);
    }
}