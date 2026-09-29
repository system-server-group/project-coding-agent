package com.system_server.ai_demo.apps.auth.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AuthServiceTest {

    private final UsersMapper usersMapper = mock(UsersMapper.class);
    private final AuthService service = new AuthService(usersMapper);

    private static UsersEntity user(String userId, String roleCode) {
        UsersEntity entity = new UsersEntity();
        entity.setUserId(userId);
        entity.setUserName("テスト");
        entity.setEmail(userId + "@example.com");
        entity.setDepartmentId(1);
        entity.setRole(roleCode);
        entity.setPassword("$2a$10$0123456789012345678901234567890123456789012345678901");
        return entity;
    }

    private static boolean hasAuthority(UserDetails details, String authority) {
        return details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    @Test
    void loadUserByUsername_mapsAdminRoleToAdminAuthority() {
        when(usersMapper.findByUserId("SM9")).thenReturn(user("SM9", "9"));
        assertTrue(hasAuthority(service.loadUserByUsername("SM9"), "ROLE_ADMIN"));
    }

    @Test
    void loadUserByUsername_mapsGeneralRoleToUserAuthority() {
        when(usersMapper.findByUserId("SM0")).thenReturn(user("SM0", "0"));
        assertTrue(hasAuthority(service.loadUserByUsername("SM0"), "ROLE_USER"));
    }

    @Test
    void loadUserByUsername_throwsWhenUserAbsent() {
        when(usersMapper.findByUserId("X")).thenReturn(null);
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("X"));
    }
}
