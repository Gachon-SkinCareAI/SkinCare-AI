package skincare.security;

import lombok.Builder;
import lombok.Getter;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import skincare.domain.user.entity.User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

// Spring Security 인증 사용자 정보를 담는 UserDetails 구현 클래스
@Getter
// UserPrincipal 인증과 권한 처리
public class UserPrincipal implements UserDetails {

    // User 엔티티의 userId 타입인 String에 맞춰 수정
    private final String userId;
    private final String email; // 사용자 이메일 (인증 식별자)
    private final String password; // 암호화된 비밀번호
    private final Collection<? extends GrantedAuthority> authorities; // 부여된 권한 목록

    // UserPrincipal 생성자 (Builder 패턴)
    @Builder
    public UserPrincipal(
            String userId,
            String email,
            String password,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    // User 엔티티로부터 UserPrincipal 객체를 생성하는 정적 팩토리 메서드
    public static UserPrincipal create(User user) {
        // 기본값 "ROLE_USER" 권한 부여
        List<GrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        return UserPrincipal.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
    }

    // 사용자 권한 목록 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    // 사용자 비밀번호 반환
    @Override
    public String getPassword() {
        return password;
    }

    // 사용자 인증 식별자 반환
    @Override
    public String getUsername() {
        return email; // 시큐리티 인증 식별자로 email 사용
    }

    // 계정 만료 여부 (true: 만료되지 않음)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠금 여부 (true: 잠기지 않음)
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 비밀번호 만료 여부 (true: 만료되지 않음)
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 계정 활성화 여부
    @Override
    public boolean isEnabled() {
        // UserStatus가 ACTIVE인 경우에만 활성화 처리
        return userStatusActive();
    }

    // 사용자 계정 상태 활성 여부 확인
    private boolean userStatusActive() {
        return true; // 서비스 로직에 맞춰 계정 상태 제어 가능
    }
}
