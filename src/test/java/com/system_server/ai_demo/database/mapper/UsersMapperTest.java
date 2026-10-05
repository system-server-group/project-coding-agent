package com.system_server.ai_demo.database.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.system_server.ai_demo.TestcontainersConfiguration;
import com.system_server.ai_demo.database.entity.UsersEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@ActiveProfiles("test")
class UsersMapperTest {

    @Autowired
    private UsersMapper mapper;

    private static UsersEntity user(String userId, String userName, int departmentId, String role) {
        UsersEntity entity = new UsersEntity();
        entity.setUserId(userId);
        entity.setUserName(userName);
        entity.setEmail(userId + "@example.com");
        entity.setDepartmentId(departmentId);
        entity.setRole(role);
        entity.setPassword("$2a$10$0123456789012345678901234567890123456789012345678901");
        return entity;
    }

    @Test
    void insert_andFindByUserId_returnsRow() {
        mapper.insert(user("SM001", "山田太郎", 10, "0"));

        UsersEntity found = mapper.findByUserId("SM001");

        assertEquals("山田太郎", found.getUserName());
        assertEquals(10, found.getDepartmentId());
        assertEquals("0", found.getRole());
    }

    @Test
    void findByUserId_returnsNull_whenAbsent() {
        assertNull(mapper.findByUserId("NOTEXIST"));
    }

    @Test
    void findAll_returnsOrderedByUserId() {
        mapper.insert(user("SM002", "ビー", 10, "0"));
        mapper.insert(user("SM001", "エー", 10, "9"));

        List<UsersEntity> all = mapper.findAll();

        assertEquals("SM001", all.get(0).getUserId());
        assertEquals("SM002", all.get(1).getUserId());
    }

    @Test
    void update_changesMutableColumns() {
        mapper.insert(user("SM010", "旧名", 10, "0"));
        UsersEntity update = user("SM010", "新名", 20, "9");

        assertEquals(1, mapper.update(update));

        UsersEntity found = mapper.findByUserId("SM010");
        assertEquals("新名", found.getUserName());
        assertEquals(20, found.getDepartmentId());
        assertEquals("9", found.getRole());
    }

    @Test
    void deleteByUserId_removesRow() {
        mapper.insert(user("SM020", "削除対象", 10, "0"));

        assertEquals(1, mapper.deleteByUserId("SM020"));
        assertNull(mapper.findByUserId("SM020"));
    }
}
