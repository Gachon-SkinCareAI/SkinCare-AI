package skincare.dto;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
public record InquiryRequest(String clientRequestId,InquiryType type,String title,String content,String analysisId) {}
