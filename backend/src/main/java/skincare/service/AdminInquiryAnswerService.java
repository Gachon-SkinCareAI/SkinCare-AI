package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.inquiry.entity.InquiryAnswer;
import skincare.dto.AdminInquiryAnswerRequestDto;
import skincare.dto.AdminInquiryResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.InquiryAnswerRepository;
import skincare.repository.InquiryRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInquiryAnswerService {

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;

    // 1. 관리자 답변 등록 (추가)
    // - 답변 생성 후 Inquiry의 status를 COMPLETED로 변경
    @Transactional
    public AdminInquiryResponseDto createAnswer(String inquiryId, AdminInquiryAnswerRequestDto dto) {
        Inquiry inquiry = inquiryRepository.findDetailByInquiryId(inquiryId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND, "해당 문의를 찾을 수 없습니다: " + inquiryId));

        // 이미 답변이 작성된 문의인지 검증
        if (inquiry.getAnswer() != null) {
            throw new CustomException(ErrorCode.INQUIRY_ALREADY_ANSWERED, "이미 답변이 작성된 문의입니다. 수정 API를 이용해 주세요.");
        }

        // 고유 답변 ID 생성 (ex. ANS-20261029-153022-123)
        String answerId = "ANS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + (int)(Math.random() * 900 + 100);

        InquiryAnswer answer = InquiryAnswer.builder()
                .answerId(answerId)
                .inquiry(inquiry)
                .answerContent(dto.getAnswerContent())
                .build();

        inquiryAnswerRepository.save(answer);

        // 문의 상태 COMPLETED로 변경 및 연관관계 매핑
        inquiry.completeAnswer(answer);

        log.info("[Admin Inquiry] 답변 등록 완료 - InquiryId: {}, AnswerId: {}", inquiryId, answerId);

        return AdminInquiryResponseDto.fromEntity(inquiry);
    }

    // 2. 관리자 답변 수정
    @Transactional
    public AdminInquiryResponseDto updateAnswer(String inquiryId, AdminInquiryAnswerRequestDto dto) {
        Inquiry inquiry = inquiryRepository.findDetailByInquiryId(inquiryId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND, "해당 문의를 찾을 수 없습니다: " + inquiryId));

        InquiryAnswer answer = inquiry.getAnswer();
        if (answer == null) {
            throw new CustomException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND, "수정할 답변 내역이 존재하지 않습니다.");
        }

        answer.updateAnswerContent(dto.getAnswerContent());

        log.info("[Admin Inquiry] 답변 수정 완료 - InquiryId: {}, AnswerId: {}", inquiryId, answer.getAnswerId());

        return AdminInquiryResponseDto.fromEntity(inquiry);
    }

    // 관리자 답변 삭제
    // - 답변 삭제 후 Inquiry의 status를 WAITING으로 변경
    @Transactional
    public AdminInquiryResponseDto deleteAnswer(String inquiryId) {
        Inquiry inquiry = inquiryRepository.findDetailByInquiryId(inquiryId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND, "해당 문의를 찾을 수 없습니다: " + inquiryId));

        InquiryAnswer answer = inquiry.getAnswer();
        if (answer == null) {
            throw new CustomException(ErrorCode.INQUIRY_ANSWER_NOT_FOUND, "삭제할 답변 내역이 존재하지 않습니다.");
        }

        // 답변 엔티티 삭제
        inquiryAnswerRepository.delete(answer);

        // 문의 상태 WAITING으로 복구 및 답변 연관관계 초기화
        inquiry.resetAnswerStatus();

        log.info("[Admin Inquiry] 답변 삭제 완료 - InquiryId: {}", inquiryId);

        return AdminInquiryResponseDto.fromEntity(inquiry);
    }
}