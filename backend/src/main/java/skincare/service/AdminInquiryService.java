package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.dto.AdminInquiryResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.InquiryRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;

    // 1. 관리자용 사용자 문의 목록 조회 (페이지네이션 및 WAITING/COMPLETED 필터링)
    public Page<AdminInquiryResponseDto> getAllInquiries(InquiryStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Inquiry> inquiries;

        if (status != null) {
            inquiries = inquiryRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            inquiries = inquiryRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return inquiries.map(AdminInquiryResponseDto::fromEntity);
    }

    // 2. 관리자용 문의 상세내용 조회
    public AdminInquiryResponseDto getInquiryDetail(String inquiryId) {
        Inquiry inquiry = inquiryRepository.findDetailByInquiryId(inquiryId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND, "해당 문의를 찾을 수 없습니다: " + inquiryId));

        return AdminInquiryResponseDto.fromEntity(inquiry);
    }
}