package com.system_server.ai_demo.database.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.system_server.ai_demo.TestcontainersConfiguration;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@ActiveProfiles("test")
class CompaniesMapperTest {

    @Autowired
    private CompaniesMapper mapper;

    private static CompaniesEntity company(int id, String name) {
        CompaniesEntity entity = new CompaniesEntity();
        entity.setCompanyId(id);
        entity.setCompanyName(name);
        return entity;
    }

    @Test
    void insert_andFindAll_returnsOrderedByCompanyId() {
        mapper.deleteAll();
        assertEquals(1, mapper.insert(company(2, "ビー社")));
        assertEquals(1, mapper.insert(company(1, "エー社")));

        List<CompaniesEntity> all = mapper.findAll();

        assertEquals(2, all.size());
        assertEquals(1, all.get(0).getCompanyId());
        assertEquals(2, all.get(1).getCompanyId());
        assertEquals("エー社", all.get(0).getCompanyName());
    }

    @Test
    void deleteAll_removesEveryRow() {
        mapper.insert(company(10, "削除対象社"));
        assertTrue(mapper.deleteAll() >= 1);
        assertTrue(mapper.findAll().isEmpty());
    }

    @Test
    void insert_throwsOnDuplicatePrimaryKey() {
        mapper.deleteAll();
        mapper.insert(company(1, "エー社"));
        assertThrows(DataIntegrityViolationException.class, () -> mapper.insert(company(1, "重複社")));
    }
}
