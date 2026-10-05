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
import org.springframework.dao.DuplicateKeyException;
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
    void findAll_returnsOrderedByCompanyId() {
        mapper.deleteAll();
        mapper.insert(company(2, "ビー社"));
        mapper.insert(company(1, "エー社"));

        List<CompaniesEntity> all = mapper.findAll();

        assertEquals(2, all.size());
        assertEquals(1, all.get(0).getCompanyId());
        assertEquals("エー社", all.get(0).getCompanyName());
        assertEquals(2, all.get(1).getCompanyId());
        assertEquals("ビー社", all.get(1).getCompanyName());
    }

    @Test
    void findAll_returnsEmptyListWhenNoRows() {
        mapper.deleteAll();

        assertTrue(mapper.findAll().isEmpty());
    }

    @Test
    void insert_storesInputValues_atCompanyIdBounds() {
        mapper.deleteAll();

        assertEquals(1, mapper.insert(company(1, "最小社")));
        assertEquals(1, mapper.insert(company(2147483647, "最大社")));

        List<CompaniesEntity> all = mapper.findAll();

        assertEquals(2, all.size());
        assertEquals(1, all.get(0).getCompanyId());
        assertEquals("最小社", all.get(0).getCompanyName());
        assertEquals(2147483647, all.get(1).getCompanyId());
        assertEquals("最大社", all.get(1).getCompanyName());
    }

    @Test
    void insert_storesCompanyNameOfFiftyCharacters() {
        mapper.deleteAll();
        String name = "ア".repeat(50);

        assertEquals(1, mapper.insert(company(1, name)));

        assertEquals(name, mapper.findAll().get(0).getCompanyName());
    }

    @Test
    void insert_throwsWhenCompanyIdIsDuplicated() {
        mapper.deleteAll();
        mapper.insert(company(1, "エー社"));

        assertThrows(DuplicateKeyException.class, () -> mapper.insert(company(1, "ビー社")));
    }

    @Test
    void deleteAll_removesEveryRowAndReturnsCount() {
        mapper.deleteAll();
        mapper.insert(company(1, "エー社"));
        mapper.insert(company(2, "ビー社"));
        mapper.insert(company(3, "シー社"));

        assertEquals(3, mapper.deleteAll());
        assertTrue(mapper.findAll().isEmpty());
    }

    @Test
    void deleteAll_returnsZeroWhenNoRows() {
        mapper.deleteAll();

        assertEquals(0, mapper.deleteAll());
    }
}
