package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import skincare.domain.admin.entity.Admin;
import skincare.domain.global.IdGenerator;
import skincare.dto.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.AdminRepository;
import skincare.security.TokenService;

// 관리자(Admin) 관련 비즈니스 로직 처리
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository; // 관리자 데이터 베이스 접근용 Repository
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 및 검증용 객체
    private final TokenService tokenService; // Access/Refresh Token 발급 및 폐기 서비스

    // 관리자 계정 생성 처리 (기존 관리자만 호출 가능)
    @Transactional
    public AdminResponseDto signUp(AdminSignUpRequestDto dto) {

        // 관리자 아이디 미입력 시 서버에서 생성
        String adminId = StringUtils.hasText(dto.getAdminId()) ? dto.getAdminId().trim() : IdGenerator.generate("ADM");

        // 관리자 아이디/이메일 중복 확인
        if (adminRepository.existsById(adminId)) {
            throw new CustomException(ErrorCode.DUPLICATE_ADMIN_ID);
        }
        if (adminRepository.existsByEmail(dto.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_ADMIN_EMAIL);
        }

        // 관리자 엔티티 생성
        // 비밀번호는 암호화
        Admin admin = Admin.builder()
                .adminId(adminId)
                .email(dto.getEmail())
                .name(dto.getName())
                .department(dto.getDepartment())
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();

        // DB에 관리자 정보 저장
        adminRepository.save(admin);
        log.info("[Auth] 관리자 계정 생성 완료 (admin_id: {})", adminId);

        // 응답 DTO 변환 및 반환
        return AdminResponseDto.from(admin);
    }

    // 관리자 로그인 처리 및 토큰 발급
    @Transactional
    public LoginResponseDto<AdminResponseDto> login(AdminLoginRequestDto dto) {

        // 이메일로 관리자 조회 (이메일 미등록 기존 관리자 계정은 관리자 아이디로 조회)
        // 계정 존재 여부가 노출되지 않도록 미존재/비밀번호 불일치 모두 동일한 오류(AUTH-001) 반환
        Admin admin = adminRepository.findByEmail(dto.getEmail())
                .or(() -> adminRepository.findById(dto.getEmail()).filter(found -> found.getEmail() == null))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        // 비밀번호 일치 여부 검증
        if (!passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
            log.warn("[Auth] 관리자 로그인 실패 - 이메일 또는 비밀번호 불일치 (email: {})", dto.getEmail());
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 최종 로그인 일시 갱신
        admin.updateLastLogin();

        // 인증 성공 시 JWT 토큰 생성
        AuthTokenDto authToken = tokenService.issue(admin.getAdminId(), "ADMIN");

        log.info("[Auth] 관리자 로그인 성공 (admin_id: {})", admin.getAdminId());

        // 토큰 및 관리자 정보를 포함한 응답 DTO 반환
        return new LoginResponseDto<>(authToken, AdminResponseDto.from(admin));
    }

    // 관리자 프로필 조회 (GET /api/wc/admin/me)
    @Transactional(readOnly = true)
    public AdminResponseDto getMyProfile(String adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
        return AdminResponseDto.from(admin);
    }

    // 관리자 로그아웃 (전달받은 Refresh Token 폐기, 미전달 시 모든 기기에서 로그아웃)
    @Transactional
    public void logout(String adminId, String refreshToken) {
        tokenService.revoke(adminId, "ADMIN", refreshToken);
    }

    // 관리자 비밀번호 변경 (현재 비밀번호 검증 후 변경)
    @Transactional
    public void changePassword(String adminId, AdminPasswordChangeRequestDto dto) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        // 1) 현재 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(dto.getCurrentPassword(), admin.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS, "현재 비밀번호가 일치하지 않습니다.");
        }

        // 2) 새 비밀번호로 암호화 후 수정
        admin.updatePassword(passwordEncoder.encode(dto.getNewPassword()));
        log.info("[Auth] 관리자 비밀번호 변경 완료 (admin_id: {})", adminId);
    }
}
