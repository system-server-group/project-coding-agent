package com.system_server.ai_demo.apps.projects.services;

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
import com.system_server.ai_demo.enums.BpRecruitment;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 案件登録サービス。案件情報と添付ファイル（任意・1件）を1トランザクションで登録し、任意で営業情報メールを送信する。
 *
 * <p>
 * 登録者・登録部署はログイン中ユーザのユーザ名・部署名、登録日時・更新日時は登録時刻とする（更新者等も同値）。管理コードは DB の
 * 自動採番による。メールはメール送信フラグが真のとき、登録の正常終了（コミット）後に送信し、失敗しても登録は取り消さない。
 */
@Service
public class ProjectRegisterService {

    private final UserReferenceService userReferenceService;
    private final DepartmentReferenceService departmentReferenceService;
    private final UsersMapper usersMapper;
    private final ProjectsMapper projectsMapper;
    private final ProjectAttachmentsMapper projectAttachmentsMapper;
    private final SalesMailService salesMailService;
    private final AiDemoProperties properties;

    /**
     * 依存を注入して生成する。
     *
     * @param userReferenceService ユーザ参照サービス
     * @param departmentReferenceService 部署参照サービス
     * @param usersMapper ユーザマスタ データアクセス（送信元メールアドレス取得）
     * @param projectsMapper 案件情報 データアクセス
     * @param projectAttachmentsMapper 案件情報添付ファイル データアクセス
     * @param salesMailService 営業情報メール送信サービス
     * @param properties アプリ固有プロパティ（更新画面URLのベースURL）
     */
    public ProjectRegisterService(UserReferenceService userReferenceService,
            DepartmentReferenceService departmentReferenceService, UsersMapper usersMapper,
            ProjectsMapper projectsMapper, ProjectAttachmentsMapper projectAttachmentsMapper,
            SalesMailService salesMailService, AiDemoProperties properties) {
        this.userReferenceService = userReferenceService;
        this.departmentReferenceService = departmentReferenceService;
        this.usersMapper = usersMapper;
        this.projectsMapper = projectsMapper;
        this.projectAttachmentsMapper = projectAttachmentsMapper;
        this.salesMailService = salesMailService;
        this.properties = properties;
    }

    /**
     * 案件情報と添付ファイルを登録し、任意で営業情報メールを送信する。
     *
     * @param form 案件登録Form
     * @param loginUserId ログイン中ユーザID
     * @return 採番された管理コード
     */
    @Transactional
    public Integer register(ProjectRegisterForm form, String loginUserId) {
        String userName = userReferenceService.getUserName(loginUserId);
        Integer departmentId = userReferenceService.getUserDepartmentId(loginUserId);
        String departmentName = departmentReferenceService.getDepartmentName(departmentId);
        UsersEntity loginUser = usersMapper.findByUserId(loginUserId);
        String fromAddress = loginUser == null ? null : loginUser.getEmail();
        OffsetDateTime now = OffsetDateTime.now();

        ProjectsEntity project = toEntity(form, userName, departmentName, now);
        projectsMapper.insert(project);
        Integer managementCode = project.getManagementCode();

        boolean hasAttachment = isPresent(form.getAttachment());
        if (hasAttachment) {
            projectAttachmentsMapper.insert(toAttachment(form.getAttachment(), managementCode,
                    userName, departmentName, now));
        }

        if (form.isSendMail()) {
            scheduleMail(form, project, managementCode, fromAddress, hasAttachment);
        }
        return managementCode;
    }

    private ProjectsEntity toEntity(
            ProjectRegisterForm form,
            String userName,
            String departmentName,
            OffsetDateTime now) {
        ProjectsEntity entity = new ProjectsEntity();
        entity.setTitle(form.getSubject());
        entity.setStatus(form.getStatus());
        entity.setClientName(form.getClient());
        entity.setCommercialFlow(form.getDistribution());
        entity.setSummary(form.getOverview());
        entity.setProcess(form.getProcess());
        entity.setStartDate(form.getStartDate());
        entity.setEndDate(form.getEndDate());
        entity.setSkill(form.getSkill());
        entity.setHeadcount(form.getHeadcount());
        entity.setRemainingCount(form.getRemainingCount());
        entity.setBpRecruitment(form.isBpRequired() ? BpRecruitment.REQUIRED.getCode()
                : BpRecruitment.NOT_REQUIRED.getCode());
        entity.setBpRecruitmentDetail(form.getBpDetail());
        entity.setWorkLocation(form.getWorkplace());
        entity.setEstimatedUnitPrice(form.getEstimatedPrice());
        entity.setContractType(blankToNull(form.getContractType()));
        entity.setNote(form.getNote());
        entity.setCreatedBy(userName);
        entity.setCreatedDepartment(departmentName);
        entity.setCreatedAt(now);
        entity.setUpdatedBy(userName);
        entity.setUpdatedDepartment(departmentName);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static ProjectAttachmentsEntity toAttachment(
            MultipartFile file,
            Integer managementCode,
            String userName,
            String departmentName,
            OffsetDateTime now) {
        ProjectAttachmentsEntity entity = new ProjectAttachmentsEntity();
        entity.setManagementCode(managementCode);
        entity.setFileName(file.getOriginalFilename());
        entity.setFileData(readBytes(file));
        entity.setFileSize((int) file.getSize());
        entity.setCreatedBy(userName);
        entity.setCreatedDepartment(departmentName);
        entity.setCreatedAt(now);
        return entity;
    }

    private void scheduleMail(
            ProjectRegisterForm form,
            ProjectsEntity project,
            Integer managementCode,
            String fromAddress,
            boolean hasAttachment) {
        SalesMailCaseInfo caseInfo = SalesMailCaseInfos.of(project);
        String url = properties.getMail().getBaseUrl() + "/projects/detail/" + managementCode;
        Runnable send = () -> salesMailService.send(SalesMailOperation.REGISTER, caseInfo,
                form.getMailBody(), fromAddress, hasAttachment, url);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager
                    .registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            send.run();
                        }
                    });
        } else {
            send.run();
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("添付ファイルの読み取りに失敗しました。", ex);
        }
    }

    private static boolean isPresent(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
