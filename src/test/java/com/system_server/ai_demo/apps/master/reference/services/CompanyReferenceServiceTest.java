package com.system_server.ai_demo.apps.master.reference.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.database.entity.CompaniesEntity;
import com.system_server.ai_demo.database.mapper.CompaniesMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyReferenceServiceTest {

    private final CompaniesMapper companiesMapper = mock(CompaniesMapper.class);
    private final CompanyReferenceService service = new CompanyReferenceService(companiesMapper);

    private static CompaniesEntity company(int id, String name) {
        CompaniesEntity entity = new CompaniesEntity();
        entity.setCompanyId(id);
        entity.setCompanyName(name);
        return entity;
    }

    @Test
    void getCompanyNames_mapsNamesInMapperOrder() {
        when(companiesMapper.findAll()).thenReturn(List.of(company(1, "エー社"), company(2, "ビー社")));
        assertEquals(List.of("エー社", "ビー社"), service.getCompanyNames());
    }

    @Test
    void getCompanyNames_returnsEmpty_whenNoCompanies() {
        when(companiesMapper.findAll()).thenReturn(List.of());
        assertTrue(service.getCompanyNames().isEmpty());
    }
}
