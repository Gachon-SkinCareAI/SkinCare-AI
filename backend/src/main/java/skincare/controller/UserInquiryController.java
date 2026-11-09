package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.dto.ApiResponseDto;
import skincare.dto.FilterParam;
import skincare.dto.UserInquiryCreateRequestDto;
import skincare.dto.UserInquiryUpdateRequestDto;
import skincare.service.UserInquiryService;

// 사용자 1:1 문의(Inquiry) 관련 API 요청 처리 컨트롤러
@Tag(name = "User - Inquiry", description = "사용자 전용 1:1 문의 관리 API")
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "skincare.legacy.controllers-enabled",
        havingValue = "true")
@RequestMapping("/api/wc/inquiries")
@RequiredArgsConstructor
// UserInquiryController API 요청 처리
public class UserInquiryController {

    private final UserInquiryService userInquiryService; // 사용자 문의 비즈니스 로직 서비스

    // 1:1 문의 등록 API
    @Operation(
            summary = "1:1 문의 등록",
            description =
                    "inquiry_type, title, content를 전송하여 문의를 등록합니다. AI 분석 내역이 있는 경우 diagnosis_id로"
                            + " 연동할 수 있습니다.")
    @PostMapping
    public ResponseEntity<ApiResponseDto> createInquiry(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UserInquiryCreateRequestDto requestDto) {
        return ApiResponseDto.created("1:1 문의가 성공적으로 접수되었습니다.")
                .with("inquiry_info", userInquiryService.createInquiry(userId, requestDto))
                .toResponseEntity();
    }

    // 본인이 작성한 문의 목록 조회 API (페이지네이션)
    @Operation(
            summary = "내가 작성한 문의 목록 및 답변 조회 (페이지네이션)",
            description = "status(ALL, WAITING, COMPLETED), page(1부터), size")
    @GetMapping
    public ResponseEntity<ApiResponseDto> getMyInquiries(
            @AuthenticationPrincipal String userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponseDto.ok("1:1 문의 목록 및 답변 조회가 완료되었습니다.")
                .withPage(
                        "inquiries_list",
                        userInquiryService.getMyInquiries(
                                userId,
                                FilterParam.toEnum(InquiryStatus.class, status),
                                page,
                                size))
                .toResponseEntity();
    }

    // 본인 문의 상세 정보 조회 API
    @Operation(
            summary = "내 문의 상세 정보 조회",
            description = "특정 문의의 질문 내용, 연관 AI 분석 정보, 관리자 답변 내역을 상세 조회합니다.")
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponseDto> getMyInquiryDetail(
            @AuthenticationPrincipal String userId, @PathVariable String inquiryId) {
        return ApiResponseDto.ok("1:1 문의 상세 조회가 완료되었습니다.")
                .with("inquiry_detail", userInquiryService.getMyInquiryDetail(userId, inquiryId))
                .toResponseEntity();
    }

    // 본인 문의 수정 API (답변 완료 시 수정 불가)
    @Operation(
            summary = "내 문의 수정",
            description = "작성한 문의 내용을 수정합니다. 단, 이미 관리자가 답변을 등록한 문의(COMPLETED)는 수정할 수 없습니다.")
    @PutMapping("/{inquiryId}")
    public ResponseEntity<ApiResponseDto> updateInquiry(
            @AuthenticationPrincipal String userId,
            @PathVariable String inquiryId,
            @Valid @RequestBody UserInquiryUpdateRequestDto requestDto) {
        return ApiResponseDto.ok("1:1 문의가 수정되었습니다.")
                .with(
                        "inquiry_info",
                        userInquiryService.updateInquiry(userId, inquiryId, requestDto))
                .toResponseEntity();
    }

    // 본인 문의 삭제 API (답변 완료 시 삭제 불가)
    @Operation(
            summary = "내 문의 삭제",
            description = "작성한 문의를 삭제합니다. 단, 이미 관리자가 답변을 등록한 문의(COMPLETED)는 삭제할 수 없습니다.")
    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<ApiResponseDto> deleteInquiry(
            @AuthenticationPrincipal String userId, @PathVariable String inquiryId) {
        userInquiryService.deleteInquiry(userId, inquiryId);
        return ApiResponseDto.ok("1:1 문의가 삭제되었습니다.").toResponseEntity();
    }
}
