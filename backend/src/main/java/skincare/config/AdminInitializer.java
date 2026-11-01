package skincare.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.admin.entity.Admin;
import skincare.repository.AdminRepository;

// 최초 관리자 계정 자동 생성 클래스
// 관리자 회원가입 API는 관리자 권한이 필요하므로, 관리자가 한 명도 없을 때에 한해
// 환경변수(ADMIN_INIT_ID / ADMIN_INIT_PASSWORD)로 지정한 계정을 서버 기동 시 1회 생성
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AdminRepository adminRepository; // 관리자 데이터 베이스 접근용 Repository
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화용 객체

    // 최초 관리자 아이디 (환경변수 미설정 시 자동 생성 생략)
    @Value("${admin.init.id:}")
    private String initAdminId;

    // 최초 관리자 이메일 (관리자 로그인 ID)
    @Value("${admin.init.email:}")
    private String initAdminEmail;

    // 최초 관리자 비밀번호
    @Value("${admin.init.password:}")
    private String initAdminPassword;

    // 최초 관리자 표시 이름
    @Value("${admin.init.name:관리자}")
    private String initAdminName;

    // 서버 기동 완료 시 관리자 계정 존재 여부 확인 후 최초 관리자 생성
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }

        if (initAdminId.isBlank() || initAdminEmail.isBlank() || initAdminPassword.isBlank()) {
            log.warn("[AdminInit] 등록된 관리자가 없습니다. ADMIN_INIT_ID / ADMIN_INIT_EMAIL / ADMIN_INIT_PASSWORD 환경변수를 설정하면 최초 관리자가 자동 생성됩니다.");
            return;
        }

        // 최초 관리자는 관리자 계정 생성 권한을 가진 최고 관리자로 생성
        Admin admin = Admin.builder()
                .adminId(initAdminId)
                .email(initAdminEmail)
                .role("ROLE_SUPER_ADMIN")
                .name(initAdminName)
                .password(passwordEncoder.encode(initAdminPassword))
                .build();
        adminRepository.save(admin);

        log.info("[AdminInit] 최초 관리자 계정 생성 완료 (adminId: {})", initAdminId);
    }
}
