package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import skincare.dto.ApiResponseDto;
import skincare.service.AdminDashboardService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 관리자 대시보드 지표 및 통계 조회 API 요청 처리 컨트롤러
@Tag(name = "Admin - Dashboard", description = "관리자 대시보드 지표/차트/응급 케이스 조회 API")
@RestController
@RequestMapping("/api/wc/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService; // 대시보드 통계 비즈니스 로직 서비스

    // 대시보드 요약 통계 API
    @Operation(summary = "대시보드 요약 통계", description = "총 사용자, 오늘 분석 수, 응급 케이스 수, AI 모델 성능, AI 서버 상태, 문의 현황을 조회합니다.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponseDto> getStats() {
        return ApiResponseDto.ok("대시보드 요약 통계 정보 조회가 완료되었습니다.")
                .with("dashboard_stats", adminDashboardService.getStats())
                .toResponseEntity();
    }

    // 일별 분석 건수 추이 차트 API
    @Operation(summary = "일별 분석 건수 추이 차트", description = "days(기본 7, 최대 31)일 동안의 일별 분석 건수를 조회합니다.")
    @GetMapping("/charts/daily")
    public ResponseEntity<ApiResponseDto> getDailyChart(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> chart = adminDashboardService.getDailyChart(days);
        ApiResponseDto response = ApiResponseDto.ok("일별 분석 건수 차트 데이터 조회가 완료되었습니다.");
        chart.forEach(response::with);
        return response.toResponseEntity();
    }

    // 위험도 분포 차트 API
    @Operation(summary = "위험도 분포 통계", description = "date(yyyy-MM-dd, 기본 오늘)의 관찰/경고/응급/판단 불가 분포를 조회합니다.")
    @GetMapping("/charts/risk")
    public ResponseEntity<ApiResponseDto> getRiskDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> distribution = adminDashboardService.getRiskDistribution(date != null ? date : LocalDate.now());
        ApiResponseDto response = ApiResponseDto.ok("위험도 분포 통계 데이터 조회가 완료되었습니다.");
        distribution.forEach(response::with);
        return response.toResponseEntity();
    }

    // 최근 응급 케이스 목록 API
    @Operation(summary = "최근 응급 케이스 모니터링", description = "limit(기본 5, 최대 20)건의 최근 응급 위험도 분석을 최신순으로 조회합니다.")
    @GetMapping("/emergency-cases")
    public ResponseEntity<ApiResponseDto> getEmergencyCases(@RequestParam(defaultValue = "5") int limit) {
        List<Map<String, Object>> cases = adminDashboardService.getEmergencyCases(limit);
        return ApiResponseDto.ok("최근 응급 케이스 목록 조회가 완료되었습니다.")
                .with("total_count", cases.size())
                .with("emergency_cases", cases)
                .toResponseEntity();
    }
}
