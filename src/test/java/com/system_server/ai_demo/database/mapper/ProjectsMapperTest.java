package com.system_server.ai_demo.database.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.system_server.ai_demo.TestcontainersConfiguration;
import com.system_server.ai_demo.apps.projects.models.ProjectSearchQuery;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class ProjectsMapperTest {

    @Autowired
    private ProjectsMapper mapper;

    private static ProjectsEntity minimalProject(String title) {
        ProjectsEntity entity = new ProjectsEntity();
        entity.setTitle(title);
        entity.setStatus("OPEN");
        OffsetDateTime now = OffsetDateTime.now();
        entity.setCreatedBy("登録者");
        entity.setCreatedDepartment("登録部署");
        entity.setCreatedAt(now);
        entity.setUpdatedBy("更新者");
        entity.setUpdatedDepartment("更新部署");
        entity.setUpdatedAt(now);
        return entity;
    }

    @Test
    void insert_populatesGeneratedManagementCode_andFindReturnsRow() {
        ProjectsEntity project = minimalProject("案件A");

        assertEquals(1, mapper.insert(project));
        assertNotNull(project.getManagementCode());

        ProjectsEntity found = mapper.findByManagementCode(project.getManagementCode());
        assertEquals("案件A", found.getTitle());
        assertEquals("OPEN", found.getStatus());
        assertEquals("登録者", found.getCreatedBy());
    }

    @Test
    void findByManagementCode_returnsNull_whenAbsent() {
        org.junit.jupiter.api.Assertions.assertNull(mapper.findByManagementCode(999999));
    }

    @Test
    void update_succeeds_whenUpdatedAtMatches() {
        ProjectsEntity project = minimalProject("旧件名");
        mapper.insert(project);
        ProjectsEntity read = mapper.findByManagementCode(project.getManagementCode());
        OffsetDateTime loaded = read.getUpdatedAt();
        OffsetDateTime newUpdatedAt = loaded.plusSeconds(5);
        read.setTitle("新件名");
        read.setStatus("CLOSED");
        // エンティティの更新日時には書き込む新しい更新日時を、照合値は別引数で渡す
        read.setUpdatedAt(newUpdatedAt);

        assertEquals(1, mapper.update(read, loaded));

        ProjectsEntity after = mapper.findByManagementCode(project.getManagementCode());
        assertEquals("新件名", after.getTitle());
        assertEquals("CLOSED", after.getStatus());
        assertEquals(newUpdatedAt.toInstant(), after.getUpdatedAt().toInstant());
        // 登録情報は変更されない
        assertEquals("登録者", after.getCreatedBy());
    }

    @Test
    void update_returnsZero_whenUpdatedAtStale() {
        ProjectsEntity project = minimalProject("件名");
        mapper.insert(project);
        ProjectsEntity read = mapper.findByManagementCode(project.getManagementCode());
        OffsetDateTime stale = read.getUpdatedAt().minusSeconds(10);
        read.setTitle("競合更新");

        assertEquals(0, mapper.update(read, stale));

        ProjectsEntity after = mapper.findByManagementCode(project.getManagementCode());
        assertEquals("件名", after.getTitle());
    }

    @Test
    void insert_acceptsTitleAtMaxLength() {
        ProjectsEntity project = minimalProject("あ".repeat(200));

        assertEquals(1, mapper.insert(project));
        assertEquals(200,
                mapper.findByManagementCode(project.getManagementCode()).getTitle().length());
    }

    @Test
    void insert_throwsWhenTitleExceedsMaxLength() {
        ProjectsEntity project = minimalProject("あ".repeat(201));
        assertThrows(DataIntegrityViolationException.class, () -> mapper.insert(project));
    }

    // ---- 条件検索 (search) ----

    private static final OffsetDateTime AT =
            OffsetDateTime.of(2026, 6, 10, 23, 30, 0, 0, ZoneOffset.ofHours(9));

    private ProjectsEntity insertProject(
            String title,
            String status,
            String skill,
            String clientName,
            String createdBy,
            String bpRecruitment,
            LocalDate startDate,
            OffsetDateTime createdAt) {
        ProjectsEntity entity = minimalProject(title);
        entity.setStatus(status);
        entity.setSkill(skill);
        entity.setClientName(clientName);
        entity.setCreatedBy(createdBy);
        entity.setBpRecruitment(bpRecruitment);
        entity.setStartDate(startDate);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        mapper.insert(entity);
        return entity;
    }

    private static ProjectSearchQuery emptyQuery() {
        return new ProjectSearchQuery(null, null, null, null, null, null, null, null, null, null,
                null, null, false, "REQUIRED");
    }

    @Test
    void search_returnsAllOrderedByManagementCodeDesc_whenNoCondition() {
        final ProjectsEntity first = insertProject("案件1", "OPEN", null, null, "u1", null, null, AT);
        final ProjectsEntity second =
                insertProject("案件2", "OPEN", null, null, "u2", null, null, AT);

        List<ProjectsEntity> result = mapper.search(emptyQuery());

        assertEquals(2, result.size());
        assertTrue(result.get(0).getManagementCode() > result.get(1).getManagementCode());
        assertEquals(second.getManagementCode(), result.get(0).getManagementCode());
        assertEquals(first.getManagementCode(), result.get(1).getManagementCode());
    }

    @Test
    void search_filtersByKeywordOverSelectedTargets() {
        insertProject("alpha案件", "OPEN", "Java", null, "u1", null, null, AT);
        insertProject("通常案件", "OPEN", "alphaスキル", null, "u2", null, null, AT);

        ProjectSearchQuery titleOnly = emptyQuery();
        titleOnly.setKeyword("alpha");
        titleOnly.setKeywordTargets(List.of("title"));
        List<ProjectsEntity> titleResult = mapper.search(titleOnly);
        assertEquals(1, titleResult.size());
        assertEquals("alpha案件", titleResult.get(0).getTitle());

        ProjectSearchQuery titleOrSkill = emptyQuery();
        titleOrSkill.setKeyword("alpha");
        titleOrSkill.setKeywordTargets(List.of("title", "skill"));
        assertEquals(2, mapper.search(titleOrSkill).size());
    }

    @Test
    void search_filtersByStatusesOr() {
        insertProject("o", "OPEN", null, null, "u1", null, null, AT);
        insertProject("c", "CLOSED", null, null, "u2", null, null, AT);

        ProjectSearchQuery openOnly = emptyQuery();
        openOnly.setStatuses(List.of("OPEN"));
        List<ProjectsEntity> openResult = mapper.search(openOnly);
        assertEquals(1, openResult.size());
        assertEquals("OPEN", openResult.get(0).getStatus());

        ProjectSearchQuery both = emptyQuery();
        both.setStatuses(List.of("OPEN", "CLOSED"));
        assertEquals(2, mapper.search(both).size());
    }

    @Test
    void search_filtersByStartDateRange_includingOneSidedAndExcludingNull() {
        insertProject("early", "OPEN", null, null, "u1", null, LocalDate.of(2026, 6, 1), AT);
        insertProject("late", "OPEN", null, null, "u2", null, LocalDate.of(2026, 12, 31), AT);
        insertProject("nodate", "OPEN", null, null, "u3", null, null, AT);

        ProjectSearchQuery from = emptyQuery();
        from.setStartDateFrom(LocalDate.of(2026, 7, 1));
        List<ProjectsEntity> fromResult = mapper.search(from);
        assertEquals(1, fromResult.size());
        assertEquals("late", fromResult.get(0).getTitle());

        ProjectSearchQuery to = emptyQuery();
        to.setStartDateTo(LocalDate.of(2026, 6, 30));
        List<ProjectsEntity> toResult = mapper.search(to);
        assertEquals(1, toResult.size());
        assertEquals("early", toResult.get(0).getTitle());
    }

    @Test
    void search_filtersByRegisteredDateByDateUnit() {
        insertProject("on", "OPEN", null, null, "u1", null, null, AT);
        insertProject("off", "OPEN", null, null, "u2", null, null,
                OffsetDateTime.of(2026, 6, 20, 8, 0, 0, 0, ZoneOffset.ofHours(9)));

        ProjectSearchQuery query = emptyQuery();
        query.setRegisteredDateFrom(LocalDate.of(2026, 6, 10));
        query.setRegisteredDateTo(LocalDate.of(2026, 6, 10));

        List<ProjectsEntity> result = mapper.search(query);
        assertEquals(1, result.size());
        assertEquals("on", result.get(0).getTitle());
    }

    @Test
    void search_filtersByRegistrantPartialMatch() {
        insertProject("a", "OPEN", null, null, "山田太郎", null, null, AT);
        insertProject("b", "OPEN", null, null, "佐藤花子", null, null, AT);

        ProjectSearchQuery query = emptyQuery();
        query.setRegistrant("山田");
        List<ProjectsEntity> result = mapper.search(query);
        assertEquals(1, result.size());
        assertEquals("山田太郎", result.get(0).getCreatedBy());
    }

    @Test
    void search_filtersByClientPartialMatch() {
        insertProject("a", "OPEN", null, "富士通FFSYS", "u1", null, null, AT);
        insertProject("b", "OPEN", null, "中部電力", "u2", null, null, AT);

        ProjectSearchQuery query = emptyQuery();
        query.setClient("富士通");
        List<ProjectsEntity> result = mapper.search(query);
        assertEquals(1, result.size());
        assertEquals("富士通FFSYS", result.get(0).getClientName());
    }

    @Test
    void search_filtersByBpOnly_whenRequired() {
        insertProject("need", "OPEN", null, null, "u1", "REQUIRED", null, AT);
        insertProject("noneed", "OPEN", null, null, "u2", "NOT_REQUIRED", null, AT);

        ProjectSearchQuery query = emptyQuery();
        query.setBpOnly(true);
        List<ProjectsEntity> result = mapper.search(query);
        assertEquals(1, result.size());
        assertEquals("REQUIRED", result.get(0).getBpRecruitment());
    }
}
