package com.system_server.ai_demo.apps.projects.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.apps.projects.models.ProjectUpdateForm;
import com.system_server.ai_demo.apps.projects.models.ProjectUpdateResult;
import com.system_server.ai_demo.apps.projects.models.SalesMailOperation;
import com.system_server.ai_demo.config.AiDemoProperties;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsEntity;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsSummaryEntity;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.ProjectAttachmentsMapper;
import com.system_server.ai_demo.database.mapper.ProjectsMapper;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class ProjectUpdateServiceTest {

    private static final OffsetDateTime LOADED =
            OffsetDateTime.of(2026, 6, 10, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    private final UserReferenceService userReferenceService = mock(UserReferenceService.class);
    private final DepartmentReferenceService departmentReferenceService =
            mock(DepartmentReferenceService.class);
    private final UsersMapper usersMapper = mock(UsersMapper.class);
    private final ProjectsMapper projectsMapper = mock(ProjectsMapper.class);
    private final ProjectAttachmentsMapper projectAttachmentsMapper =
            mock(ProjectAttachmentsMapper.class);
    private final SalesMailService salesMailService = mock(SalesMailService.class);
    private final AiDemoProperties properties = new AiDemoProperties();
    private final ProjectUpdateService service =
            new ProjectUpdateService(userReferenceService, departmentReferenceService, usersMapper,
                    projectsMapper, projectAttachmentsMapper, salesMailService, properties);

    @BeforeEach
    void setup() {
        when(userReferenceService.getUserName("U1")).thenReturn("更新太郎");
        when(userReferenceService.getUserDepartmentId("U1")).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("更新部署");
        UsersEntity user = new UsersEntity();
        user.setEmail("u1@example.com");
        when(usersMapper.findByUserId("U1")).thenReturn(user);
        properties.getMail().setBaseUrl("http://host");
    }

    private static ProjectUpdateForm form() {
        ProjectUpdateForm form = new ProjectUpdateForm();
        form.setManagementCode(100);
        form.setSubject("更新件名");
        form.setStatus("OPEN");
        form.setContractType("");
        form.setBpRequired(false);
        form.setSendMail(false);
        form.setLoadedUpdatedAt(LOADED);
        return form;
    }

    private static ProjectAttachmentsEntity attachment(int fileId, OffsetDateTime createdAt) {
        ProjectAttachmentsEntity entity = new ProjectAttachmentsEntity();
        entity.setFileId(fileId);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private static ProjectAttachmentsSummaryEntity summary(int fileId, OffsetDateTime createdAt) {
        ProjectAttachmentsSummaryEntity entity = new ProjectAttachmentsSummaryEntity();
        entity.setFileId(fileId);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    // ---- getProject / getAttachments ----

    @Test
    void getProject_returnsProject_withoutReadingAttachments() {
        ProjectsEntity project = new ProjectsEntity();
        project.setManagementCode(100);
        when(projectsMapper.findByManagementCode(100)).thenReturn(project);

        ProjectsEntity actual = service.getProject(100);

        assertEquals(100, actual.getManagementCode());
        // 【ファイル一覧】は添付一覧APIが担うため、案件情報の取得では添付を読み出さない。
        verify(projectAttachmentsMapper, never()).findByManagementCode(any());
    }

    @Test
    void getProject_returnsNull_whenAbsent() {
        when(projectsMapper.findByManagementCode(999)).thenReturn(null);
        assertNull(service.getProject(999));
    }

    @Test
    void getAttachments_returnsSortedByCreatedAt() {
        ProjectAttachmentsSummaryEntity later = summary(2, LOADED.plusHours(2));
        ProjectAttachmentsSummaryEntity earlier = summary(1, LOADED);
        when(projectAttachmentsMapper.findByManagementCode(100))
                .thenReturn(List.of(later, earlier));

        List<ProjectAttachmentsSummaryEntity> actual = service.getAttachments(100);

        assertEquals(2, actual.size());
        assertEquals(1, actual.get(0).getFileId());
        assertEquals(2, actual.get(1).getFileId());
    }

    // ---- update ----

    @Test
    void update_succeeds_andSetsAuditAndExclusionColumns() {
        when(projectsMapper.update(any(), any())).thenReturn(1);
        final OffsetDateTime before = OffsetDateTime.now();

        ProjectUpdateResult result = service.update(form(), "U1");

        assertEquals(ProjectUpdateResult.SUCCESS, result);
        ArgumentCaptor<ProjectsEntity> captor = ArgumentCaptor.forClass(ProjectsEntity.class);
        // 楽観排他の照合に用いる読込時更新日時は別引数で渡す
        verify(projectsMapper).update(captor.capture(), eq(LOADED));
        ProjectsEntity entity = captor.getValue();
        assertEquals(Integer.valueOf(100), entity.getManagementCode());
        assertEquals("更新件名", entity.getTitle());
        assertEquals("更新太郎", entity.getUpdatedBy());
        assertEquals("更新部署", entity.getUpdatedDepartment());
        // 更新日時には書き込む新しい更新日時（更新時の現在時刻）を設定する
        assertTrue(!entity.getUpdatedAt().isBefore(before));
        verify(projectAttachmentsMapper, never()).insert(any());
    }

    @Test
    void update_returnsConflict_whenUpdateAffectsNoRow() {
        when(projectsMapper.update(any(), any())).thenReturn(0);

        ProjectUpdateResult result = service.update(form(), "U1");

        assertEquals(ProjectUpdateResult.CONFLICT, result);
        verify(projectAttachmentsMapper, never()).insert(any());
        verify(salesMailService, never()).send(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void update_returnsAttachmentLimit_whenAlreadyFive() {
        ProjectUpdateForm form = form();
        form.setAttachment(
                new MockMultipartFile("attachment", "f.txt", "text/plain", "x".getBytes()));
        when(projectAttachmentsMapper.findByManagementCode(100))
                .thenReturn(List.of(summary(1, LOADED), summary(2, LOADED), summary(3, LOADED),
                        summary(4, LOADED), summary(5, LOADED)));

        ProjectUpdateResult result = service.update(form, "U1");

        assertEquals(ProjectUpdateResult.ATTACHMENT_LIMIT, result);
        verify(projectsMapper, never()).update(any(), any());
    }

    @Test
    void update_insertsAttachment_whenPresentUnderLimit() {
        ProjectUpdateForm form = form();
        form.setAttachment(
                new MockMultipartFile("attachment", "add.txt", "text/plain", "hello".getBytes()));
        when(projectAttachmentsMapper.findByManagementCode(100))
                .thenReturn(List.of(summary(1, LOADED), summary(2, LOADED)));
        when(projectsMapper.update(any(), any())).thenReturn(1);

        ProjectUpdateResult result = service.update(form, "U1");

        assertEquals(ProjectUpdateResult.SUCCESS, result);
        ArgumentCaptor<ProjectAttachmentsEntity> captor =
                ArgumentCaptor.forClass(ProjectAttachmentsEntity.class);
        verify(projectAttachmentsMapper).insert(captor.capture());
        assertEquals("add.txt", captor.getValue().getFileName());
        assertEquals(Integer.valueOf(100), captor.getValue().getManagementCode());
        assertEquals(5, captor.getValue().getFileSize());
        // 添付の登録日時は案件の更新日時と同一の現在時刻（サービスが取得した唯一の now）
        ArgumentCaptor<ProjectsEntity> projectCaptor =
                ArgumentCaptor.forClass(ProjectsEntity.class);
        verify(projectsMapper).update(projectCaptor.capture(), eq(LOADED));
        assertEquals(projectCaptor.getValue().getUpdatedAt(), captor.getValue().getCreatedAt());
    }

    @Test
    void update_sendsUpdateMail_whenSendMailTrue() {
        ProjectUpdateForm form = form();
        form.setSendMail(true);
        form.setMailBody("更新本文");
        when(projectsMapper.update(any(), any())).thenReturn(1);
        ProjectsEntity updated = new ProjectsEntity();
        updated.setManagementCode(100);
        updated.setStatus("OPEN");
        when(projectsMapper.findByManagementCode(100)).thenReturn(updated);

        service.update(form, "U1");

        verify(salesMailService).send(eq(SalesMailOperation.UPDATE), any(), eq("更新本文"),
                eq("u1@example.com"), eq(false), eq("http://host/projects/detail/100"));
    }

    // ---- 添付ダウンロード・削除 ----

    @Test
    void getAttachment_delegatesToMapper() {
        ProjectAttachmentsEntity entity = attachment(7, LOADED);
        when(projectAttachmentsMapper.findByFileId(7)).thenReturn(entity);
        assertEquals(entity, service.getAttachment(7));
    }

    @Test
    void deleteAttachment_delegatesToMapper() {
        when(projectAttachmentsMapper.deleteByFileId(7)).thenReturn(1);
        assertEquals(1, service.deleteAttachment(7));
        verify(projectAttachmentsMapper).deleteByFileId(anyInt());
    }
}
