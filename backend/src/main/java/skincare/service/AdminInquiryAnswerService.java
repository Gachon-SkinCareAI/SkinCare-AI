package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.global.IdGenerator;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.inquiry.entity.InquiryAnswer;
import skincare.dto.AdminInquiryAnswerRequestDto;
import skincare.dto.AdminInquiryResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.InquiryAnswerRepository;
import skincare.repository.InquiryRepository;


// 관리자 문의 답변 등록/수정/삭제 관련 비즈니스 로직 처리
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInquiryAnswerService {

    private final InquiryRepository inquiryRepository; // 문의 데이터 베이스 접근용 Repository
    private final InquiryAnswerRepository inquiryAnswerRepository; // 문의 답변 데이터 베이스 접근용 Repository

    // 1. 관리자 답변 등록 (추가)
    // - 답변 생성 후 Inquiry의 status를 COMPLETED로 변경, 답변 작성 관리자 기록
    @Transactional
    public AdminInquiryResponseDto createAnswer(String inquiryId, AdminInquiryAnswerRequestDto dto, String adminId) {
        Inquiry inquiry = findInquiry(inquiryId);

        // 이미 답변이 작성된 문의인지 검증
        if (inquiry.getAnswer() != null) {
            throw new CustomException(ErrorCode.INQUIRY_ALREADY_CLOSED, "이미 답변이 작성된 문의입니다. 수정 API를 이용해 주세요.");
        }

        // 고유 답변 ID 생성 (ex. ANS-20261029-153022-3fa85f6457a1)
        String answerId = IdGenerator.generate("ANS");

        InquiryAnswer answer = InquiryAnswer.builder()
                .answerId(answerId)
                .inquiry(inquiry)
                .answerContent(dto.getAnswerContent())
                .adminId(adminId)
                .build();

        // 직접 할당 ID 엔티티의 save()는 merge로 동작하여 전달한 객체와 다른 영속 객체를 반환하므로,
        // 반드시 반환된 영속 객체(savedAnswer)를 문의에 연결해야 함 (원본 객체 연결 시 커밋 시점에 식별자 중복 오류 발생)
        InquiryAnswer savedAnswer;
        try {
            savedAnswer = inquiryAnswerRepository.saveAndFlush(answer);
        } catch (RuntimeException e) {
            log.error("[Inquiry] 답변 저장 실패 (inquiry_id: {}): {}", inquiryId, e.getMessage(), e);
            throw new CustomException(ErrorCode.ANSWER_SAVE_FAILED);
        }

        // 문의 상태 COMPLETED로 변경 및 연관관계 매핑
        inquiry.completeAnswer(savedAnswer);

        log.info("[Inquiry] 정상 처리, 관리자 문의 답변 등록 완료 (inquiry_id: {}, admin_id: {})", inquiryId, adminId);

        return AdminInquiryResponseDto.detail(inquiry);
    }

    // 2. 관리자 답변 수정
    @Transactional
    public AdminInquiryResponseDto updateAnswer(String inquiryId, AdminInquiryAnswerRequestDto dto, String adminId) {
        Inquiry inquiry = findInquiry(inquiryId);

        InquiryAnswer answer = inquiry.getAnswer();
        if (answer == null) {
            throw new CustomException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND, "수정할 답변 내역이 존재하지 않습니다.");
        }

        answer.updateAnswerContent(dto.getAnswerContent(), adminId);

        log.info("[Inquiry] 정상 처리, 관리자 문의 답변 수정 완료 (inquiry_id: {}, admin_id: {})", inquiryId, adminId);

        return AdminInquiryResponseDto.detail(inquiry);
    }

    // 관리자 답변 삭제
    // - 답변 삭제 후 Inquiry의 status를 WAITING으로 변경
    @Transactional
    public AdminInquiryResponseDto deleteAnswer(String inquiryId, String adminId) {
        Inquiry inquiry = findInquiry(inquiryId);

        InquiryAnswer answer = inquiry.getAnswer();
        if (answer == null) {
            throw new CustomException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND, "삭제할 답변 내역이 존재하지 않습니다.");
        }

        // 답변 엔티티 삭제
        inquiryAnswerRepository.delete(answer);

        // 문의 상태 WAITING으로 복구 및 답변 연관관계 초기화
        inquiry.resetAnswerStatus();

        log.info("[Inquiry] 정상 처리, 관리자 문의 답변 삭제 완료 (inquiry_id: {}, admin_id: {})", inquiryId, adminId);

        return AdminInquiryResponseDto.detail(inquiry);
    }

    // 문의 조회 (없으면 INQUIRY-001)
    private Inquiry findInquiry(String inquiryId) {
        return inquiryRepository.findDetailByInquiryId(inquiryId)
                .orElseThrow(() -> {
                    log.warn("[Inquiry] 요청 실패, 해당 문의 내역을 찾을 수 없습니다. (inquiry_id: {})", inquiryId);
                    return new CustomException(ErrorCode.INQUIRY_NOT_FOUND);
                });
    }
}
