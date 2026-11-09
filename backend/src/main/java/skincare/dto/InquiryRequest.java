package skincare.dto;

import skincare.domain.inquiry.entity.ENUM.InquiryType;

// InquiryRequest 요청과 응답 데이터
public record InquiryRequest(
        String clientRequestId,
        InquiryType type,
        String title,
        String content,
        String analysisId) {}
