package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.dto.UserInquiryCreateRequestDto;
import skincare.dto.UserInquiryResponseDto;
import skincare.dto.UserInquiryUpdateRequestDto;
import skincare.security.UserPrincipal; // Security Context 인증 사용자 객체에 맞게 패키지 조정
import skincare.service.UserInquiryService;

@Tag(name = "User - Inquiry", description = "사용자 전용 1:1 문의 관리 API")
@RestController
@RequestMapping("/api/wc/inquiries")
@RequiredArgsConstructor
public class UserInquiryController {

    private final UserInquiryService userInquiryService;

    @Operation(
            summary = "1:1 문의 등록",
            description = "사용자가 1:1 문의를 등록합니다. AI 상처 진단 내역이 있는 경우 diagnosisId를 전달하여 참조시킬 수 있습니다."
    )
    @PostMapping
    public ResponseEntity<UserInquiryResponseDto> createInquiry(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserInquiryCreateRequestDto requestDto
    ) {
        UserInquiryResponseDto response = userInquiryService.createInquiry(principal.getUserId(), requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "내가 작성한 문의 목록 조회 (페이지네이션)",
            description = "로그인한 사용자가 본인이 작성한 전체 문의 내역을 최신순으로 조회합니다."
    )
    @GetMapping
    public ResponseEntity<Page<UserInquiryResponseDto>> getMyInquiries(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<UserInquiryResponseDto> response = userInquiryService.getMyInquiries(principal.getUserId(), page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "내 문의 상세 정보 조회",
            description = "특정 문의 ID의 질문 내용, 참조된 AI 진단 정보, 관리자 답변 내역을 상세 조회합니다."
    )
    @GetMapping("/{inquiryId}")
    public ResponseEntity<UserInquiryResponseDto> getMyInquiryDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String inquiryId
    ) {
        UserInquiryResponseDto response = userInquiryService.getMyInquiryDetail(principal.getUserId(), inquiryId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "내 문의 수정",
            description = "작성한 문의 내용을 수정합니다. 단, 이미 관리자가 답변을 등록한 문의(COMPLETED)는 수정할 수 없습니다."
    )
    @PutMapping("/{inquiryId}")
    public ResponseEntity<UserInquiryResponseDto> updateInquiry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String inquiryId,
            @Valid @RequestBody UserInquiryUpdateRequestDto requestDto
    ) {
        UserInquiryResponseDto response = userInquiryService.updateInquiry(principal.getUserId(), inquiryId, requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "내 문의 삭제",
            description = "작성한 문의를 삭제합니다. 단, 이미 관리자가 답변을 등록한 문의(COMPLETED)는 삭제할 수 없습니다."
    )
    @DeleteMapping("/{inquiryId}")
    public ResponseEntity<Void> deleteInquiry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String inquiryId
    ) {
        userInquiryService.deleteInquiry(principal.getUserId(), inquiryId);
        return ResponseEntity.noContent().build();
    }
}