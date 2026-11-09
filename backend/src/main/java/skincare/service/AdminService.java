package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.admin.entity.Admin;
import skincare.dto.AdminLoginRequestDto;
import skincare.dto.AdminPasswordChangeRequestDto;
import skincare.dto.AdminResponseDto;
import skincare.dto.AdminSignUpRequestDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.AdminRepository;
import skincare.repository.RefreshTokenRepository;
import skincare.security.JwtTokenProvider;

// 관리자(Admin) 관련 비즈니스 로직 처리
@Service
@RequiredArgsConstructor
// AdminService 비즈니스 로직 처리
public class AdminService {

    private final AdminAuditService audit;
    private final AdminRepository adminRepository; // 관리자 데이터 베이스 접근용 Repository
    private final RefreshTokenRepository refreshTokenRepository; // DB 기반 토큰 삭제용 Repository
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 및 검증용 객체
    private final JwtTokenProvider jwtTokenProvider; // JWT 토큰 생성 및 검증용 객체

    // 관리자 회원가입 처리
    @Transactional
    public AdminResponseDto signUp(AdminSignUpRequestDto dto) {
        ServiceInput.text(dto.getAdminId(), 36, "관리자 ID");
        ServiceInput.text(dto.getName(), 50, "관리자 이름");
        ServiceInput.password(dto.getPassword());
        if (!dto.getAdminId().matches("[A-Za-z0-9_.-]{3,36}"))
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "관리자 ID 형식을 확인해 주세요.");

        // 관리자 아이디 중복 확인
        if (adminRepository.existsById(dto.getAdminId())) {
            throw new CustomException(ErrorCode.DUPLICATE_ADMIN_ID);
        }

        // 관리자 엔티티 생성
        // 비밀번호는 암호화
        Admin admin =
                Admin.builder()
                        .adminId(dto.getAdminId())
                        .name(dto.getName())
                        .password(passwordEncoder.encode(dto.getPassword()))
                        .build();

        // DB에 관리자 정보 저장
        adminRepository.save(admin);
        audit.current("ADMIN_CREATE", admin.getAdminId(), null);

        // 응답 DTO 변환 및 반환
        return AdminResponseDto.builder().adminId(admin.getAdminId()).name(admin.getName()).build();
    }

    // 관리자 로그인 처리 및 토큰 발급
    @Transactional
    public AdminResponseDto login(AdminLoginRequestDto dto) {
        ServiceInput.text(dto.getAdminId(), 36, "관리자 ID");
        if (dto.getPassword() == null || dto.getPassword().isBlank())
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "비밀번호를 입력해 주세요.");

        // 입력받은 아이디로 관리자 조회
        // 존재하지 않으면 예외 발생
        Admin admin =
                adminRepository
                        .findById(dto.getAdminId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        // 비밀번호 일치 여부 검증
        if (!passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        // 인증 성공 시 JWT 토큰 생성
        String token =
                jwtTokenProvider.createToken(
                        admin.getAdminId(), "ADMIN", admin.currentTokenVersion());

        audit.record(admin.getAdminId(), "ADMIN_LOGIN", admin.getAdminId(), null);
        // 토큰 및 관리자 정보를 포함한 응답 DTO 반환
        return AdminResponseDto.builder()
                .adminId(admin.getAdminId())
                .name(admin.getName())
                .token(token)
                .build();
    }

    // 관리자 로그아웃 (DB의 Refresh Token 삭제)
    @Transactional
    public void logout(String adminId) {
        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND))
                .revokeTokens();
        refreshTokenRepository.deleteByUserId(adminId);
        audit.record(adminId, "ADMIN_LOGOUT", adminId, null);
    }

    // 관리자 비밀번호 변경 (현재 비밀번호 검증 후 변경)
    @Transactional
    public void changePassword(String adminId, AdminPasswordChangeRequestDto dto) {
        Admin admin =
                adminRepository
                        .findById(adminId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));

        // 1) 현재 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(dto.getCurrentPassword(), admin.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "현재 비밀번호가 일치하지 않습니다.");
        }

        ServiceInput.password(dto.getNewPassword());
        // 2) 새 비밀번호로 암호화 후 수정
        admin.updatePassword(passwordEncoder.encode(dto.getNewPassword()));
        admin.revokeTokens();
        audit.record(adminId, "ADMIN_PASSWORD_CHANGE", adminId, null);
    }
}
