package skincare.dto;

// InquiryReplyRequest 요청과 응답 데이터
public record InquiryReplyRequest(String clientRequestId, Long expectedVersion, String content) {}
