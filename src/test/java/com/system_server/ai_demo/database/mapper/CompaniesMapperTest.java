package com.system_server.ai_demo.database.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.system_server.ai_demo.TestcontainersConfiguration;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@ActiveProfiles("test")
class CompaniesMapperTest {

    @Autowired
    private CompaniesMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 会社マスタは Mapper に登録手段を持たないため、テストデータは JDBC で直接投入する。 */
    private void insertCompany(int id, String name) {
        jdbcTemplate.update("INSERT INTO companies (company_id, company_name) VALUES (?, ?)", id,
                name);
    }

    @Test
    void findAll_returnsOrderedByCompanyId() {
        jdbcTemplate.update("DELETE FROM companies");
        insertCompany(2, "ビー社");
        insertCompany(1, "エー社");

        List<CompaniesEntity> all = mapper.findAll();

        assertEquals(2, all.size());
        assertEquals(1, all.get(0).getCompanyId());
        assertEquals("エー社", all.get(0).getCompanyName());
        assertEquals(2, all.get(1).getCompanyId());
        assertEquals("ビー社", all.get(1).getCompanyName());
    }

    @Test
    void findAll_returnsEmptyListWhenNoRows() {
        jdbcTemplate.update("DELETE FROM companies");

        assertTrue(mapper.findAll().isEmpty());
    }
}
