package com.system_server.ai_demo.database.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.system_server.ai_demo.TestcontainersConfiguration;
import com.system_server.ai_demo.database.entity.DepartmentsEntity;
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
class DepartmentsMapperTest {

    @Autowired
    private DepartmentsMapper mapper;

    private static DepartmentsEntity department(int id, String name) {
        DepartmentsEntity entity = new DepartmentsEntity();
        entity.setDepartmentId(id);
        entity.setDepartmentName(name);
        return entity;
    }

    @Test
    void insert_andFindAll_returnsOrderedByDepartmentId() {
        mapper.deleteAll();
        mapper.insert(department(20, "営業二課"));
        mapper.insert(department(10, "営業一課"));

        List<DepartmentsEntity> all = mapper.findAll();

        assertEquals(2, all.size());
        assertEquals(10, all.get(0).getDepartmentId());
        assertEquals(20, all.get(1).getDepartmentId());
    }

    @Test
    void findByDepartmentId_returnsRowOrNull() {
        mapper.insert(department(30, "総務部"));

        DepartmentsEntity found = mapper.findByDepartmentId(30);
        assertEquals("総務部", found.getDepartmentName());

        assertNull(mapper.findByDepartmentId(999));
    }

    @Test
    void deleteAll_removesEveryRow() {
        mapper.insert(department(40, "削除対象部"));
        assertTrue(mapper.deleteAll() >= 1);
        assertTrue(mapper.findAll().isEmpty());
    }
}
