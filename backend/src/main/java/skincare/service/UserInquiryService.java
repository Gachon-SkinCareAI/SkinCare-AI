package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.global.IdGenerator;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.user.entity.User;
import skincare.dto.Paging;
import skincare.dto.UserInquiryCreateRequestDto;
import skincare.dto.UserInquiryResponseDto;
import skincare.dto.UserInquiryUpdateRequestDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.DiagnosisRepository;
import skincare.repository.InquiryRepository;
import skincare.repository.UserRepository;


// 사용자 1:1 문의(Inquiry) 관련 비즈니스 로직 처리
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserInquiryService {

    private final InquiryRepository inquiryRepository; // 문의 데이터 베이스 접근용 Repository
    private final UserRepository userRepository; // 사용자 데이터 베이스 접근용 Repository
    private final DiagnosisRepository diagnosisRepository; // 진단 기록 데이터 베이스 접근용 Repository

    // 사용자 문의 신규 등록 (AI 진단기록 선택 연동 가능)
    @Transactional
    public UserInquiryResponseDto createInquiry(String userId, UserInquiryCreateRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // AI 상처 진단 기록 참조 시 해당 진단이 존재하는지 및 사용자 소유인지 확인
        Diagnosis diagnosis = null;
        if (dto.getDiagnosisId() != null) {
            // 본인 분석 기록만 연동 가능 (타인 기록은 존재 여부를 노출하지 않고 INQUIRY-004 처리)
            diagnosis = diagnosisRepository.findByDiagnosisIdAndUserId(dto.getDiagnosisId(), userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.RELATED_DIAGNOSIS_NOT_FOUND));
        }

        // 고유 문의 ID 생성 (ex. INQ-20261029-153022-3fa85f6457a1)
        String inquiryId = IdGenerator.generate("INQ");

        Inquiry inquiry = Inquiry.builder()
                .inquiryId(inquiryId)
                .user(user)
                .diagnosis(diagnosis)
                .inquiryType(dto.getInquiryType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .status(InquiryStatus.WAITING)
                .build();

        // 응답에 작성 일시(created_at)를 포함하기 위해 즉시 flush
        Inquiry savedInquiry = inquiryRepository.saveAndFlush(inquiry);
        log.info("[Inquiry] 정상 처리, 1:1 고객 문의 접수 완료 (inquiry_id: {}, user_id: {})", inquiryId, userId);

        return UserInquiryResponseDto.fromEntity(savedInquiry);
    }

    // 본인이 작성한 문의 목록 조회 (페이지네이션, 답변 상태 필터)
    public Page<UserInquiryResponseDto> getMyInquiries(String userId, InquiryStatus status, int page, int size) {
        Pageable pageable = Paging.of(page, size);
        Page<Inquiry> inquiries = (status == null)
                ? inquiryRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable)
                : inquiryRepository.findByUserUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);

        return inquiries.map(UserInquiryResponseDto::fromEntity);
    }

    // 3. 문의 상세 조회
    public UserInquiryResponseDto getMyInquiryDetail(String userId, String inquiryId) {
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndUserId(inquiryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

        return UserInquiryResponseDto.fromEntity(inquiry);
    }

    // 4. 문의 수정 (관리자 답변 작성 시 수정 불가)
    @Transactional
    public UserInquiryResponseDto updateInquiry(String userId, String inquiryId, UserInquiryUpdateRequestDto dto) {
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndUserId(inquiryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

        // 관리자가 이미 답변을 작성한 경우 수정 불가
        if (inquiry.getStatus() == InquiryStatus.COMPLETED || inquiry.getAnswer() != null) {
            throw new CustomException(ErrorCode.INQUIRY_ALREADY_CLOSED, "이미 관리자 답변이 완료된 문의는 수정할 수 없습니다.");
        }

        // 진단 정보 변경 시 확인
        Diagnosis diagnosis = null;
        if (dto.getDiagnosisId() != null) {
            // 본인 분석 기록만 연동 가능 (타인 기록은 존재 여부를 노출하지 않고 INQUIRY-004 처리)
            diagnosis = diagnosisRepository.findByDiagnosisIdAndUserId(dto.getDiagnosisId(), userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.RELATED_DIAGNOSIS_NOT_FOUND));
        }

        inquiry.updateInquiry(dto.getInquiryType(), dto.getTitle(), dto.getContent(), diagnosis);
        log.info("[User Inquiry] 문의 수정 완료 - InquiryId: {}", inquiryId);

        return UserInquiryResponseDto.fromEntity(inquiry);
    }

    // 문의 삭제 (관리자 답변 작성 시 삭제 불가)
    @Transactional
    public void deleteInquiry(String userId, String inquiryId) {
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndUserId(inquiryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

        // 관리자가 이미 답변을 작성한 경우 삭제 불가
        if (inquiry.getStatus() == InquiryStatus.COMPLETED || inquiry.getAnswer() != null) {
            throw new CustomException(ErrorCode.INQUIRY_ALREADY_CLOSED, "이미 관리자 답변이 완료된 문의는 삭제할 수 없습니다.");
        }

        inquiryRepository.delete(inquiry);
        log.info("[User Inquiry] 문의 삭제 완료 - InquiryId: {}", inquiryId);
    }
}