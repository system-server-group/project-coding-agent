package com.system_server.ai_demo.apps.master.reference.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserReferenceServiceTest {

    private final UsersMapper usersMapper = mock(UsersMapper.class);
    private final UserReferenceService service = new UserReferenceService(usersMapper);

    private static UsersEntity user(String userId, String userName, int departmentId) {
        UsersEntity entity = new UsersEntity();
        entity.setUserId(userId);
        entity.setUserName(userName);
        entity.setDepartmentId(departmentId);
        return entity;
    }

    @Test
    void getUserNames_mapsNamesInMapperOrder() {
        when(usersMapper.findAll())
                .thenReturn(List.of(user("SM001", "エー", 10), user("SM002", "ビー", 20)));
        assertEquals(List.of("エー", "ビー"), service.getUserNames());
    }

    @Test
    void getUserName_returnsName_whenPresent() {
        when(usersMapper.findByUserId("SM001")).thenReturn(user("SM001", "山田太郎", 10));
        assertEquals("山田太郎", service.getUserName("SM001"));
    }

    @Test
    void getUserName_returnsNull_whenAbsent() {
        when(usersMapper.findByUserId("NONE")).thenReturn(null);
        assertNull(service.getUserName("NONE"));
    }

    @Test
    void getUserDepartmentId_returnsId_whenPresent() {
        when(usersMapper.findByUserId("SM001")).thenReturn(user("SM001", "山田太郎", 10));
        assertEquals(10, service.getUserDepartmentId("SM001"));
    }

    @Test
    void getUserDepartmentId_returnsNull_whenAbsent() {
        when(usersMapper.findByUserId("NONE")).thenReturn(null);
        assertNull(service.getUserDepartmentId("NONE"));
    }
}
