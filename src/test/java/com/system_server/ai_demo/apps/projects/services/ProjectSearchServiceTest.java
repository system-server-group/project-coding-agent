package com.system_server.ai_demo.apps.projects.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.apps.projects.models.ProjectListDto;
import com.system_server.ai_demo.apps.projects.models.ProjectSearchCondition;
import com.system_server.ai_demo.apps.projects.models.ProjectSearchQuery;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import com.system_server.ai_demo.database.mapper.ProjectsMapper;
import com.system_server.ai_demo.enums.BpRecruitment;
import com.system_server.ai_demo.enums.ContractType;
import com.system_server.ai_demo.enums.Status;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectSearchServiceTest {

    private final ProjectsMapper projectsMapper = mock(ProjectsMapper.class);
    private final ProjectSearchService service = new ProjectSearchService(projectsMapper);

    @Test
    void search_normalizesConditionIntoQuery() {
        when(projectsMapper.search(any())).thenReturn(List.of());
        ProjectSearchCondition condition = new ProjectSearchCondition();
        condition.setKeyword("Java");
        condition.setKeywordTargets(List.of("title", "skill"));
        condition.setStatuses(List.of("OPEN"));
        condition.setStartDateFrom(LocalDate.of(2026, 6, 1));
        condition.setStartDateTo(null);
        condition.setRegistrant("");
        condition.setClient("富士通");
        condition.setBpOnly(true);

        service.search(condition);

        ArgumentCaptor<ProjectSearchQuery> captor =
                ArgumentCaptor.forClass(ProjectSearchQuery.class);
        verify(projectsMapper).search(captor.capture());
        ProjectSearchQuery query = captor.getValue();
        assertEquals("Java", query.getKeyword());
        assertEquals(List.of("title", "skill"), query.getKeywordTargets());
        assertEquals(LocalDate.of(2026, 6, 1), query.getStartDateFrom());
        assertNull(query.getStartDateTo());
        assertNull(query.getRegistrant());
        assertEquals("富士通", query.getClient());
        assertTrue(query.isBpOnly());
        assertEquals(BpRecruitment.REQUIRED.getCode(), query.getBpRequiredCode());
    }

    @Test
    void search_mapsEntityToDtoWithResolvedCodeEnums() {
        ProjectsEntity entity = new ProjectsEntity();
        entity.setManagementCode(5);
        entity.setTitle("案件");
        entity.setStatus("OPEN");
        entity.setBpRecruitment("REQUIRED");
        entity.setContractType("CONTRACT");
        entity.setStartDate(LocalDate.of(2026, 6, 1));
        when(projectsMapper.search(any())).thenReturn(List.of(entity));

        List<ProjectListDto> result = service.search(new ProjectSearchCondition());

        assertEquals(1, result.size());
        ProjectListDto dto = result.get(0);
        assertEquals(Integer.valueOf(5), dto.managementCode());
        assertEquals(Status.OPEN, dto.status());
        assertEquals("オープン", dto.status().getLabel());
        assertEquals(BpRecruitment.REQUIRED, dto.bpRecruitment());
        assertEquals(ContractType.CONTRACT, dto.contractType());
        assertEquals(LocalDate.of(2026, 6, 1), dto.startDate());
    }

    @Test
    void search_keepsNullCodeEnums_whenEntityValueAbsent() {
        ProjectsEntity entity = new ProjectsEntity();
        entity.setStatus("OPEN");
        entity.setBpRecruitment(null);
        entity.setContractType(null);
        when(projectsMapper.search(any())).thenReturn(List.of(entity));

        ProjectListDto dto = service.search(new ProjectSearchCondition()).get(0);
        assertNull(dto.bpRecruitment());
        assertNull(dto.contractType());
    }
}
