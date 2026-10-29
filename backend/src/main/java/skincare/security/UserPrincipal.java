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

@Getter
public class UserPrincipal implements UserDetails {

    // User 엔티티의 userId 타입인 String에 맞춰 수정
    private final String userId;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    @Builder
    public UserPrincipal(String userId, String email, String password, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    // User 엔티티로부터 UserPrincipal 객체를 생성하는 정적 팩토리 메서드
    public static UserPrincipal create(User user) {
        // 기본값 "ROLE_USER" 권한 부여
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER")
        );

        return UserPrincipal.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email; // 시큐리티 인증 식별자로 email 사용
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // UserStatus가 ACTIVE인 경우에만 활성화 처리
        return userStatusActive();
    }

    private boolean userStatusActive() {
        return true; // 서비스 로직에 맞춰 계정 상태 제어 가능
    }
}