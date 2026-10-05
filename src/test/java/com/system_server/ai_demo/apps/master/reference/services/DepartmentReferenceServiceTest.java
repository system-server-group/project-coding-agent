package com.system_server.ai_demo.apps.master.reference.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import com.system_server.ai_demo.database.mapper.DepartmentsMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepartmentReferenceServiceTest {

    private final DepartmentsMapper departmentsMapper = mock(DepartmentsMapper.class);
    private final DepartmentReferenceService service =
            new DepartmentReferenceService(departmentsMapper);

    private static DepartmentsEntity department(int id, String name) {
        DepartmentsEntity entity = new DepartmentsEntity();
        entity.setDepartmentId(id);
        entity.setDepartmentName(name);
        return entity;
    }

    @Test
    void getDepartmentName_returnsName_whenPresent() {
        when(departmentsMapper.findByDepartmentId(10)).thenReturn(department(10, "営業一課"));
        assertEquals("営業一課", service.getDepartmentName(10));
    }

    @Test
    void getDepartmentName_returnsNull_whenAbsent() {
        when(departmentsMapper.findByDepartmentId(99)).thenReturn(null);
        assertNull(service.getDepartmentName(99));
    }

    @Test
    void existsDepartment_returnsTrue_whenPresent() {
        when(departmentsMapper.findByDepartmentId(10)).thenReturn(department(10, "営業一課"));
        assertTrue(service.existsDepartment(10));
    }

    @Test
    void existsDepartment_returnsFalse_whenAbsent() {
        when(departmentsMapper.findByDepartmentId(99)).thenReturn(null);
        assertFalse(service.existsDepartment(99));
    }

    @Test
    void getDepartmentNames_mapsNamesInMapperOrder() {
        when(departmentsMapper.findAll())
                .thenReturn(List.of(department(10, "営業一課"), department(20, "営業二課")));
        assertEquals(List.of("営業一課", "営業二課"), service.getDepartmentNames());
    }
}
