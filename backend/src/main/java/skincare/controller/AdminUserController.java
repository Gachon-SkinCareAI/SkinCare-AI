package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.domain.user.entity.ENUM.PlanType;
import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.dto.AdminUserPlanUpdateRequestDto;
import skincare.dto.AdminUserStatusUpdateRequestDto;
import skincare.dto.ApiResponseDto;
import skincare.dto.FilterParam;
import skincare.service.AdminUserService;

// 관리자 회원 관리 API 요청 처리 컨트롤러
@Tag(name = "Admin - User Management", description = "관리자 전용 회원 목록/상세 조회 및 계정 상태·플랜 관리 API")
@RestController
@RequestMapping("/api/wc/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService; // 관리자 회원 관리 비즈니스 로직 서비스

    // 회원 목록 및 검색 API
    @Operation(
            summary = "회원 목록 조회 및 검색",
            description = "search_keyword(회원 ID/이메일/닉네임), plan_type(ALL, FREE, PREMIUM), status(ALL, ACTIVE, INACTIVE, SUSPENDED, DELETED), page(1부터), size"
    )
    @GetMapping
    public ResponseEntity<ApiResponseDto> getUsers(
            @RequestParam(value = "search_keyword", required = false) String searchKeyword,
            @RequestParam(value = "plan_type", required = false) String planType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponseDto.ok("사용자 관리 목록 조회가 완료되었습니다.")
                .with("stats", adminUserService.getUserStats())
                .withPage("user_list", adminUserService.getUsers(searchKeyword,
                        FilterParam.toEnum(PlanType.class, planType),
                        FilterParam.toEnum(UserStatus.class, status), page, size))
                .toResponseEntity();
    }

    // 회원 상세 프로필 및 최근 활동 조회 API
    @Operation(summary = "회원 상세 조회", description = "회원 기본 정보, 통계, 계정 정보, 최근 활동, 분석 히스토리, 상태/플랜 변경 이력을 조회합니다.")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponseDto> getUserDetail(@PathVariable String userId) {
        return ApiResponseDto.ok("사용자 상세 정보 및 활동 조회가 완료되었습니다.")
                .with("user_detail", adminUserService.getUserDetail(userId))
                .toResponseEntity();
    }

    // 회원 계정 상태 변경 API
    @Operation(summary = "회원 계정 상태 변경", description = "status(ACTIVE, INACTIVE, SUSPENDED)와 reason을 전송합니다. 정지/비활성 시 해당 회원은 즉시 로그아웃됩니다.")
    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponseDto> changeStatus(
            @AuthenticationPrincipal String adminId,
            @PathVariable String userId,
            @Valid @RequestBody AdminUserStatusUpdateRequestDto requestDto
    ) {
        return ApiResponseDto.ok("사용자 계정 상태가 변경되었습니다.")
                .with("change_info", adminUserService.changeStatus(adminId, userId, requestDto))
                .toResponseEntity();
    }

    // 회원 이용 플랜 변경 API
    @Operation(summary = "회원 이용 플랜 변경", description = "plan(FREE, PREMIUM)과 reason을 전송합니다.")
    @PatchMapping("/{userId}/plan")
    public ResponseEntity<ApiResponseDto> changePlan(
            @AuthenticationPrincipal String adminId,
            @PathVariable String userId,
            @Valid @RequestBody AdminUserPlanUpdateRequestDto requestDto
    ) {
        return ApiResponseDto.ok("사용자 이용 플랜이 변경되었습니다.")
                .with("change_info", adminUserService.changePlan(adminId, userId, requestDto))
                .toResponseEntity();
    }
}
