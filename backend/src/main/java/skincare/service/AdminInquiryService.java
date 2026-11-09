package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.dto.AdminInquiryResponseDto;
import skincare.dto.Paging;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.InquiryRepository;

import java.util.LinkedHashMap;
import java.util.Map;

// 관리자 사용자 문의 조회 관련 비즈니스 로직 처리
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// AdminInquiryService 비즈니스 로직 처리
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository; // 문의 데이터 베이스 접근용 Repository

    // 관리자용 사용자 문의 목록 조회 (페이지네이션, 답변 상태/문의 유형 필터링)
    public Page<AdminInquiryResponseDto> getAllInquiries(
            InquiryStatus status, InquiryType inquiryType, int page, int size) {
        Page<Inquiry> inquiries =
                inquiryRepository.findAll(
                        InquiryRepository.adminSearch(status, inquiryType),
                        Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return inquiries.map(AdminInquiryResponseDto::summary);
    }

    // 관리자 문의 현황 통계 (전체, 답변대기, 답변완료 건수)
    public Map<String, Object> getInquiryStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_inquiries", inquiryRepository.count());
        stats.put("waiting_count", inquiryRepository.countByStatus(InquiryStatus.WAITING));
        stats.put("completed_count", inquiryRepository.countByStatus(InquiryStatus.COMPLETED));
        return stats;
    }

    // 관리자용 문의 상세내용 조회 (작성자 정보, 연관 분석 기록 포함)
    public AdminInquiryResponseDto getInquiryDetail(String inquiryId) {
        Inquiry inquiry =
                inquiryRepository
                        .findDetailByInquiryId(inquiryId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "[Inquiry] 요청 실패, 해당 문의 내역을 찾을 수 없습니다. (inquiry_id:"
                                                    + " {})",
                                            inquiryId);
                                    return new CustomException(ErrorCode.INQUIRY_NOT_FOUND);
                                });

        return AdminInquiryResponseDto.detail(inquiry);
    }
}
