package com.system_server.ai_demo.apps.auth.services;

import com.system_server.ai_demo.commons.code.CodeEnums;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import com.system_server.ai_demo.enums.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 認証サービス。ユーザマスタとの照合（認証）を担う。
 *
 * <p>
 * 認証は Spring Security のフォームログインが本クラス（{@link UserDetailsService}）と {@code PasswordEncoder}
 * を用いて実施する（ユーザマスタ照合＝{@link #loadUserByUsername(String)}）。認可の強制は Spring Security の URL
 * ベースのアクセス制御が担う。
 */
@Service
public class AuthService implements UserDetailsService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_USER = "ROLE_USER";

    private final UsersMapper usersMapper;

    public AuthService(UsersMapper usersMapper) {
        this.usersMapper = usersMapper;
    }

    /**
     * ユーザIDでユーザマスタを照合し、認証用のユーザ情報（権限はロール由来）を返す。
     *
     * @param userId ユーザID
     * @return 認証用ユーザ情報
     * @throws UsernameNotFoundException 該当ユーザが存在しない場合
     */
    @Override
    public UserDetails loadUserByUsername(String userId) {
        UsersEntity user = usersMapper.findByUserId(userId);
        if (user == null) {
            throw new UsernameNotFoundException("authentication failed");
        }
        Role role = CodeEnums.fromCode(Role.class, user.getRole());
        String authority = role == Role.SYSTEM_ADMIN ? ROLE_ADMIN : ROLE_USER;
        return User.withUsername(user.getUserId()).password(user.getPassword())
                .authorities(new SimpleGrantedAuthority(authority)).build();
    }
}
