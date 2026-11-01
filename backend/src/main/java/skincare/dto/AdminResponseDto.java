package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import skincare.domain.admin.entity.Admin;

import java.time.format.DateTimeFormatter;

// 관리자 정보 응답 DTO (관리자 로그인/계정 생성 응답의 admin_info 객체)
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminResponseDto {
    private String adminId;     // 관리자 아이디
    private String email;       // 관리자 이메일
    private String name;        // 관리자 이름
    private String role;        // 권한 등급 (ROLE_SUPER_ADMIN, ROLE_ADMIN)
    private String department;  // 소속 부서
    private String lastLoginAt; // 최종 로그인 일시
    private String createdAt;   // 계정 생성 일시

    // Admin 엔티티를 응답 DTO로 변환
    public static AdminResponseDto from(Admin admin) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return AdminResponseDto.builder()
                .adminId(admin.getAdminId())
                .email(admin.getEmail())
                .name(admin.getName())
                .role(admin.getRole())
                .department(admin.getDepartment())
                .lastLoginAt(admin.getLastLoginAt() != null ? admin.getLastLoginAt().format(formatter) : null)
                .createdAt(admin.getCreatedAt() != null ? admin.getCreatedAt().format(formatter) : null)
                .build();
    }
}
