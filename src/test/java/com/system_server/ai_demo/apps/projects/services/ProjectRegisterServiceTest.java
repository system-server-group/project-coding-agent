package com.system_server.ai_demo.apps.projects.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.apps.projects.models.ProjectRegisterForm;
import com.system_server.ai_demo.apps.projects.models.SalesMailCaseInfo;
import com.system_server.ai_demo.apps.projects.models.SalesMailOperation;
import com.system_server.ai_demo.config.AiDemoProperties;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsEntity;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.ProjectAttachmentsMapper;
import com.system_server.ai_demo.database.mapper.ProjectsMapper;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ProjectRegisterServiceTest {

    private final UserReferenceService userReferenceService = mock(UserReferenceService.class);
    private final DepartmentReferenceService departmentReferenceService =
            mock(DepartmentReferenceService.class);
    private final UsersMapper usersMapper = mock(UsersMapper.class);
    private final ProjectsMapper projectsMapper = mock(ProjectsMapper.class);
    private final ProjectAttachmentsMapper projectAttachmentsMapper =
            mock(ProjectAttachmentsMapper.class);
    private final SalesMailService salesMailService = mock(SalesMailService.class);
    private final AiDemoProperties properties = new AiDemoProperties();
    private final ProjectRegisterService service = new ProjectRegisterService(userReferenceService,
            departmentReferenceService, usersMapper, projectsMapper, projectAttachmentsMapper,
            salesMailService, properties);

    @BeforeEach
    void setup() {
        when(userReferenceService.getUserName("U1")).thenReturn("山田太郎");
        when(userReferenceService.getUserDepartmentId("U1")).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("営業部");
        UsersEntity user = new UsersEntity();
        user.setUserId("U1");
        user.setEmail("yamada@example.com");
        when(usersMapper.findByUserId("U1")).thenReturn(user);
        properties.getMail().setBaseUrl("http://host");
        when(projectsMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, ProjectsEntity.class).setManagementCode(100);
            return 1;
        });
    }

    private static ProjectRegisterForm form() {
        ProjectRegisterForm form = new ProjectRegisterForm();
        form.setSubject("案件件名");
        form.setStatus("OPEN");
        form.setContractType("");
        form.setBpRequired(false);
        form.setSendMail(false);
        return form;
    }

    @Test
    void register_insertsProjectWithAuditColumns_andReturnsManagementCode() {
        Integer managementCode = service.register(form(), "U1");

        assertEquals(Integer.valueOf(100), managementCode);
        ArgumentCaptor<ProjectsEntity> captor = ArgumentCaptor.forClass(ProjectsEntity.class);
        verify(projectsMapper).insert(captor.capture());
        ProjectsEntity entity = captor.getValue();
        assertEquals("案件件名", entity.getTitle());
        assertEquals("OPEN", entity.getStatus());
        assertEquals("NOT_REQUIRED", entity.getBpRecruitment());
        assertEquals("山田太郎", entity.getCreatedBy());
        assertEquals("山田太郎", entity.getUpdatedBy());
        assertEquals("営業部", entity.getCreatedDepartment());
        assertEquals("営業部", entity.getUpdatedDepartment());
        org.junit.jupiter.api.Assertions.assertNotNull(entity.getCreatedAt());
        verify(projectAttachmentsMapper, never()).insert(any());
        verify(salesMailService, never()).send(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void register_insertsAttachment_whenPresent() {
        ProjectRegisterForm form = form();
        MultipartFile file =
                new MockMultipartFile("attachment", "spec.txt", "text/plain", "hello".getBytes());
        form.setAttachment(file);

        service.register(form, "U1");

        ArgumentCaptor<ProjectAttachmentsEntity> captor =
                ArgumentCaptor.forClass(ProjectAttachmentsEntity.class);
        verify(projectAttachmentsMapper).insert(captor.capture());
        ProjectAttachmentsEntity attachment = captor.getValue();
        assertEquals("spec.txt", attachment.getFileName());
        assertEquals(Integer.valueOf(100), attachment.getManagementCode());
        assertEquals(5, attachment.getFileSize());
        assertEquals("山田太郎", attachment.getCreatedBy());
    }

    @Test
    void register_setsRequiredBpCode_whenChecked() {
        ProjectRegisterForm form = form();
        form.setBpRequired(true);

        service.register(form, "U1");

        ArgumentCaptor<ProjectsEntity> captor = ArgumentCaptor.forClass(ProjectsEntity.class);
        verify(projectsMapper).insert(captor.capture());
        assertEquals("REQUIRED", captor.getValue().getBpRecruitment());
    }

    @Test
    void register_nullsBlankContractType_andSetsDates() {
        ProjectRegisterForm form = form();
        form.setStartDate(LocalDate.of(2026, 6, 1));
        form.setEndDate(LocalDate.of(2026, 12, 31));

        service.register(form, "U1");

        ArgumentCaptor<ProjectsEntity> captor = ArgumentCaptor.forClass(ProjectsEntity.class);
        verify(projectsMapper).insert(captor.capture());
        ProjectsEntity entity = captor.getValue();
        assertNull(entity.getContractType());
        assertEquals(LocalDate.of(2026, 6, 1), entity.getStartDate());
        assertEquals(LocalDate.of(2026, 12, 31), entity.getEndDate());
    }

    @Test
    void register_sendsMail_whenSendMailTrue() {
        ProjectRegisterForm form = form();
        form.setSendMail(true);
        form.setMailBody("本文です");

        service.register(form, "U1");

        ArgumentCaptor<SalesMailCaseInfo> caseInfo =
                ArgumentCaptor.forClass(SalesMailCaseInfo.class);
        verify(salesMailService).send(eq(SalesMailOperation.REGISTER), caseInfo.capture(),
                eq("本文です"), eq("yamada@example.com"), eq(false),
                eq("http://host/projects/detail/100"));
        assertEquals("100", caseInfo.getValue().managementCode());
        assertEquals("オープン", caseInfo.getValue().status());
        assertEquals("不要", caseInfo.getValue().bpRecruitment());
    }

    @Test
    void register_doesNotSendMail_whenSendMailFalse() {
        service.register(form(), "U1");
        verify(salesMailService, never()).send(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
    }
}
