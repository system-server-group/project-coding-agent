package com.system_server.ai_demo.database.mapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.system_server.ai_demo.TestcontainersConfiguration;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsEntity;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsSummaryEntity;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import java.time.OffsetDateTime;
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
class ProjectAttachmentsMapperTest {

    @Autowired
    private ProjectAttachmentsMapper mapper;

    @Autowired
    private ProjectsMapper projectsMapper;

    private static ProjectAttachmentsEntity attachment(
            int managementCode,
            String name,
            byte[] data) {
        ProjectAttachmentsEntity entity = new ProjectAttachmentsEntity();
        entity.setManagementCode(managementCode);
        entity.setFileName(name);
        entity.setFileData(data);
        entity.setFileSize(data.length);
        entity.setCreatedBy("登録者");
        entity.setCreatedDepartment("登録部署");
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }

    private int newProjectCode() {
        ProjectsEntity project = new ProjectsEntity();
        project.setTitle("親案件");
        project.setStatus("OPEN");
        OffsetDateTime now = OffsetDateTime.now();
        project.setCreatedBy("u");
        project.setCreatedDepartment("d");
        project.setCreatedAt(now);
        project.setUpdatedBy("u");
        project.setUpdatedDepartment("d");
        project.setUpdatedAt(now);
        projectsMapper.insert(project);
        return project.getManagementCode();
    }

    @Test
    void insert_andFindByManagementCode_returnsOrderedByFileId() {
        int code = newProjectCode();
        mapper.insert(attachment(code, "a.txt", new byte[] {1}));
        mapper.insert(attachment(code, "b.txt", new byte[] {2}));
        mapper.insert(attachment(code, "c.txt", new byte[] {3}));

        List<ProjectAttachmentsSummaryEntity> all = mapper.findByManagementCode(code);

        assertEquals(3, all.size());
        assertTrue(all.get(0).getFileId() < all.get(1).getFileId());
        assertTrue(all.get(1).getFileId() < all.get(2).getFileId());
    }

    @Test
    void insert_populatesGeneratedFileId_andFindByFileIdReturnsData() {
        int code = newProjectCode();
        byte[] data = {10, 20, 30, 40};
        ProjectAttachmentsEntity entity = attachment(code, "data.bin", data);

        assertEquals(1, mapper.insert(entity));
        assertNotNull(entity.getFileId());

        ProjectAttachmentsEntity found = mapper.findByFileId(entity.getFileId());
        assertEquals("data.bin", found.getFileName());
        assertEquals(4, found.getFileSize());
        assertArrayEquals(data, found.getFileData());
    }

    @Test
    void findByManagementCode_returnsEmpty_whenNoAttachments() {
        int code = newProjectCode();
        assertTrue(mapper.findByManagementCode(code).isEmpty());
    }

    @Test
    void deleteByFileId_removesRow() {
        int code = newProjectCode();
        ProjectAttachmentsEntity entity = attachment(code, "del.txt", new byte[] {9});
        mapper.insert(entity);

        assertEquals(1, mapper.deleteByFileId(entity.getFileId()));
        org.junit.jupiter.api.Assertions.assertNull(mapper.findByFileId(entity.getFileId()));
    }

    @Test
    void insert_throwsOnForeignKeyViolation() {
        assertThrows(DataIntegrityViolationException.class,
                () -> mapper.insert(attachment(999999, "x.txt", new byte[] {1})));
    }
}
