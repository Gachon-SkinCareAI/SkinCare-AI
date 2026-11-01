package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.dto.AdminInquiryAnswerRequestDto;
import skincare.dto.AdminInquiryResponseDto;
import skincare.dto.ApiResponseDto;
import skincare.dto.FilterParam;
import skincare.service.AdminInquiryAnswerService;
import skincare.service.AdminInquiryService;

// 관리자 사용자 문의(Inquiry) 조회 및 답변 관리 API 요청 처리 컨트롤러
@Tag(name = "Admin - Inquiry", description = "관리자 전용 사용자 문의 관리 API")
@RestController
@RequestMapping("/api/wc/admin/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService; // 관리자 문의 조회 비즈니스 로직 서비스
    private final AdminInquiryAnswerService adminInquiryAnswerService; // 관리자 문의 답변 등록/수정/삭제 서비스

    // 사용자 전체 문의 목록 조회 API (페이지네이션, 답변 상태/유형 필터)
    @Operation(
            summary = "사용자 문의 목록 조회 (페이지네이션)",
            description = "status(ALL, WAITING, COMPLETED), inquiry_type(ALL, AI_RESULT, ACCURACY, SYSTEM_ERROR, OTHER), page(1부터), size"
    )
    @GetMapping
    public ResponseEntity<ApiResponseDto> getAllInquiries(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "inquiry_type", required = false) String inquiryType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponseDto.ok("사용자 문의 목록 조회가 완료되었습니다.")
                .with("stats", adminInquiryService.getInquiryStats())
                .withPage("inquiry_list", adminInquiryService.getAllInquiries(
                        FilterParam.toEnum(InquiryStatus.class, status),
                        FilterParam.toEnum(InquiryType.class, inquiryType), page, size))
                .toResponseEntity();
    }

    // 사용자 문의 상세내용 조회 API
    @Operation(
            summary = "사용자 문의 상세내용 및 연관 분석 기록 조회",
            description = "작성자 정보, 문의 내용, 연관 AI 분석 기록, 답변 내용을 상세 조회합니다."
    )
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponseDto> getInquiryDetail(@PathVariable String inquiryId) {
        return ApiResponseDto.ok("문의 상세 정보 및 연관 상처 분석 기록 조회가 완료되었습니다.")
                .with("inquiry_detail", adminInquiryService.getInquiryDetail(inquiryId))
                .toResponseEntity();
    }

    // 사용자 문의 답변 등록 API
    @Operation(
            summary = "사용자 문의 답변 등록",
            description = "특정 문의에 답변(answer_content)을 작성합니다. 등록 성공 시 status가 COMPLETED로 자동 변경됩니다."
    )
    @PostMapping("/{inquiryId}/answers")
    public ResponseEntity<ApiResponseDto> createAnswer(
            @AuthenticationPrincipal String adminId,
            @PathVariable String inquiryId,
            @Valid @RequestBody AdminInquiryAnswerRequestDto requestDto
    ) {
        AdminInquiryResponseDto response = adminInquiryAnswerService.createAnswer(inquiryId, requestDto, adminId);
        return ApiResponseDto.created("사용자 문의 답변이 성공적으로 등록되었습니다.")
                .with("answer_info", response)
                .toResponseEntity();
    }

    // 사용자 문의 답변 수정 API
    @Operation(summary = "사용자 문의 답변 수정", description = "기존에 작성된 답변의 내용을 수정합니다.")
    @PutMapping("/{inquiryId}/answers")
    public ResponseEntity<ApiResponseDto> updateAnswer(
            @AuthenticationPrincipal String adminId,
            @PathVariable String inquiryId,
            @Valid @RequestBody AdminInquiryAnswerRequestDto requestDto
    ) {
        return ApiResponseDto.ok("사용자 문의 답변이 수정되었습니다.")
                .with("answer_info", adminInquiryAnswerService.updateAnswer(inquiryId, requestDto, adminId))
                .toResponseEntity();
    }

    // 사용자 문의 답변 삭제 API
    @Operation(summary = "사용자 문의 답변 삭제", description = "작성된 답변을 삭제합니다. 삭제 시 status가 WAITING으로 자동 변경됩니다.")
    @DeleteMapping("/{inquiryId}/answers")
    public ResponseEntity<ApiResponseDto> deleteAnswer(
            @AuthenticationPrincipal String adminId,
            @PathVariable String inquiryId
    ) {
        return ApiResponseDto.ok("사용자 문의 답변이 삭제되었습니다.")
                .with("inquiry_detail", adminInquiryAnswerService.deleteAnswer(inquiryId, adminId))
                .toResponseEntity();
    }
}
