package skincare.dto;

import java.time.LocalDateTime;

// InquiryView 요청과 응답 데이터
public record InquiryView(
        String id,
        String type,
        String title,
        String status,
        String analysisId,
        LocalDateTime createdAt,
        long version,
        String content,
        Reply answer) {
    public record Reply(String content, LocalDateTime createdAt) {}
}
