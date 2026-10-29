package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.dto.AdminInquiryAnswerRequestDto;
import skincare.dto.AdminInquiryResponseDto;
import skincare.service.AdminInquiryAnswerService;
import skincare.service.AdminInquiryService;

@Tag(name = "Admin - Inquiry", description = "관리자 전용 사용자 문의 관리 API")
@RestController
@RequestMapping("/api/wc/admin/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;
    private final AdminInquiryAnswerService adminInquiryAnswerService;

    @Operation(
            summary = "사용자 모든 문의 목록 조회 (페이지네이션)",
            description = "사용자가 등록한 전체 문의 목록을 최신순으로 조회합니다. status 파라미터(WAITING, COMPLETED)로 답변 대기/완료 필터링이 가능합니다."
    )
    @GetMapping
    public ResponseEntity<Page<AdminInquiryResponseDto>> getAllInquiries(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<AdminInquiryResponseDto> response = adminInquiryService.getAllInquiries(status, page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "사용자 문의 상세내용 조회",
            description = "inquiryId (ex. INQ-20261005-0014)를 전달받아 작성자 정보, 질문 내용, 진단 기록 ID, 답변 내용을 상세 조회합니다."
    )
    @GetMapping("/{inquiryId}")
    public ResponseEntity<AdminInquiryResponseDto> getInquiryDetail(
            @PathVariable String inquiryId
    ) {
        AdminInquiryResponseDto response = adminInquiryService.getInquiryDetail(inquiryId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "사용자 문의 답변 등록",
            description = "특정 문의에 답변을 작성합니다. 등록 성공 시 status가 COMPLETED로 자동 변경됩니다."
    )
    @PostMapping("/{inquiryId}/answer")
    public ResponseEntity<AdminInquiryResponseDto> createAnswer(
            @PathVariable String inquiryId,
            @Valid @RequestBody AdminInquiryAnswerRequestDto requestDto
    ) {
        AdminInquiryResponseDto response = adminInquiryAnswerService.createAnswer(inquiryId, requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "사용자 문의 답변 수정",
            description = "기존에 작성된 답변의 내용을 수정합니다."
    )
    @PutMapping("/{inquiryId}/answer")
    public ResponseEntity<AdminInquiryResponseDto> updateAnswer(
            @PathVariable String inquiryId,
            @Valid @RequestBody AdminInquiryAnswerRequestDto requestDto
    ) {
        AdminInquiryResponseDto response = adminInquiryAnswerService.updateAnswer(inquiryId, requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "사용자 문의 답변 삭제",
            description = "작성된 답변을 삭제합니다. 삭제 시 status가 WAITING으로 자동 변경됩니다."
    )
    @DeleteMapping("/{inquiryId}/answer")
    public ResponseEntity<AdminInquiryResponseDto> deleteAnswer(
            @PathVariable String inquiryId
    ) {
        AdminInquiryResponseDto response = adminInquiryAnswerService.deleteAnswer(inquiryId);
        return ResponseEntity.ok(response);
    }
}