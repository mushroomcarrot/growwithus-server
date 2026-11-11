package community.likelion.growwithusserver.user.data.entity;

import jakarta.persistence.*;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user")
public class User implements UserDetails {
    private static final long serialVersionUID = 6014984039564979072L;
    private static final String PUBLIC_ID_PREFIX = "usr_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 아이디. UserDetails.getUsername()도 이 값을 반환한다.
    @Column(nullable = false, unique = true, length = 20)
    private String username;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    // 현재 역할의 프로필(주인 또는 관리사) 등록 여부. 프로필 도메인이 등록 시 true로 바꾼다.
    @Builder.Default
    @Column(nullable = false)
    private boolean profileRegistered = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime registeredAt;

    @UpdateTimestamp
    @Column
    private LocalDateTime modifiedAt;

    // API 응답에 쓰는 외부 노출용 ID (예: usr_12)
    public String getPublicId() {
        return PUBLIC_ID_PREFIX + id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(this.role.getValue()));
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
        return true;
    }
}
