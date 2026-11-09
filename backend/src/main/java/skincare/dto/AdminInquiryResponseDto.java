package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.user.entity.User;

import java.time.format.DateTimeFormatter;

// 관리자 문의 목록/상세/답변 처리 응답 DTO (/api/wc/admin/inquiries)
// 목록 조회(inquiry_list)는 연관 분석 ID만, 상세 조회(inquiry_detail)는 작성자 정보와 연관 분석 기록 상세까지 포함
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
// AdminInquiryResponseDto 요청과 응답 데이터
public class AdminInquiryResponseDto {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private String inquiryId; // 문의 고유 ID
    private String userId; // 작성자 아이디 (목록 조회)
    private String userName; // 작성자 닉네임 (목록 조회)
    private UserInfo userInfo; // 작성자 정보 (상세 조회)
    private InquiryType inquiryType; // 문의 유형 코드
    private String inquiryTypeLabel; // 문의 유형 표시명
    private String title; // 문의 제목
    private String content; // 문의 내용
    private String createdAt; // 문의 작성 일시
    private InquiryStatus status; // 답변 상태 (WAITING, COMPLETED)
    private String statusLabel; // 답변 상태 표시명
    private String relatedDiagnosisId; // 연관 분석 ID (목록 조회)
    private RelatedDiagnosis relatedDiagnosis; // 연관 분석 기록 상세 (상세 조회)

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private InquiryAnswerDto answer; // 관리자 답변 (답변 전이면 null)

    // 문의 작성자 정보
    @Getter
    @Builder
    public static class UserInfo {
        private String userId; // 사용자 ID
        private String name; // 닉네임
        private String email; // 이메일
    }

    // 연관 AI 분석 기록 상세 (설계서 6.3.6 문의 처리 시 분석 기록 확인)
    @Getter
    @Builder
    public static class RelatedDiagnosis {
        private String diagnosisId; // 분석 고유 ID
        private String imageUrl; // 분석 이미지 조회 API 경로 (관리자)
        private BodyPart bodyPart; // 촬영 신체 부위
        private String suspectedDisease; // AI 의심 유형 표시 문구
        private RiskLevel riskLevel; // 위험도
        private Double confidenceScore; // AI 신뢰도 점수
        private String diagnosedAt; // 분석 일시
    }

    // 관리자 문의 목록 요약 변환
    public static AdminInquiryResponseDto summary(Inquiry inquiry) {
        User user = inquiry.getUser();
        return baseBuilder(inquiry)
                .userId(user.getUserId())
                .userName(user.getNickname())
                .relatedDiagnosisId(
                        inquiry.getDiagnosis() != null
                                ? inquiry.getDiagnosis().getDiagnosisId()
                                : null)
                .build();
    }

    // 상세 조회/답변 처리 응답 변환
    public static AdminInquiryResponseDto detail(Inquiry inquiry) {
        User user = inquiry.getUser();
        Diagnosis diagnosis = inquiry.getDiagnosis();
        return baseBuilder(inquiry)
                .userInfo(
                        UserInfo.builder()
                                .userId(user.getUserId())
                                .name(user.getNickname())
                                .email(user.getEmail())
                                .build())
                .relatedDiagnosis(
                        diagnosis == null
                                ? null
                                : RelatedDiagnosis.builder()
                                        .diagnosisId(diagnosis.getDiagnosisId())
                                        .imageUrl(
                                                AdminDiagnosisResponseDto.imagePath(
                                                        diagnosis.getDiagnosisId()))
                                        .bodyPart(diagnosis.getBodyPart())
                                        .suspectedDisease(diagnosis.getSuspectedDisease())
                                        .riskLevel(diagnosis.getRiskLevel())
                                        .confidenceScore(diagnosis.getConfidenceScore())
                                        .diagnosedAt(
                                                diagnosis.getCreatedAt() != null
                                                        ? diagnosis.getCreatedAt().format(FORMATTER)
                                                        : null)
                                        .build())
                .build();
    }

    // 관리자 문의 상세 필드 매핑
    private static AdminInquiryResponseDtoBuilder baseBuilder(Inquiry inquiry) {
        return AdminInquiryResponseDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .inquiryType(inquiry.getInquiryType())
                .inquiryTypeLabel(inquiry.getInquiryType().getLabel())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .createdAt(
                        inquiry.getCreatedAt() != null
                                ? inquiry.getCreatedAt().format(FORMATTER)
                                : null)
                .status(inquiry.getStatus())
                .statusLabel(inquiry.getStatus().getLabel())
                .answer(InquiryAnswerDto.from(inquiry.getAnswer()));
    }
}
