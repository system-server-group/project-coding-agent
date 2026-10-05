package com.system_server.ai_demo.apps.projects.services;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import com.system_server.ai_demo.apps.projects.models.ProjectUpdateForm;
import com.system_server.ai_demo.apps.projects.models.ProjectUpdateResult;
import com.system_server.ai_demo.apps.projects.models.SalesMailCaseInfo;
import com.system_server.ai_demo.apps.projects.models.SalesMailOperation;
import com.system_server.ai_demo.config.AiDemoProperties;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsEntity;
import com.system_server.ai_demo.database.entity.ProjectAttachmentsSummaryEntity;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.ProjectAttachmentsMapper;
import com.system_server.ai_demo.database.mapper.ProjectsMapper;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import com.system_server.ai_demo.enums.BpRecruitment;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 案件更新サービス。案件情報の取得（表示用）、楽観排他を伴う更新、添付ファイルの追加・削除・ダウンロード、任意のメール送信を行う。
 */
@Service
public class ProjectUpdateService {

    /** 添付追加可能な登録済み件数の上限（これを超える＝既に5件あると追加不可）。 */
    private static final int MAX_EXISTING_ATTACHMENTS = 4;

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
    public ProjectUpdateService(UserReferenceService userReferenceService,
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
     * 管理コードを指定して、表示対象の案件情報を取得する。
     *
     * <p>
     * 【ファイル一覧】は添付一覧API（{@link #getAttachments}）が担うため、本メソッドは案件情報のみを読み出す。
     *
     * @param managementCode 管理コード
     * @return 案件情報、存在しない場合は {@code null}
     */
    public ProjectsEntity getProject(Integer managementCode) {
        return projectsMapper.findByManagementCode(managementCode);
    }

    /**
     * 管理コードを指定して、登録済み添付ファイル一覧（登録日時昇順）を取得する。実ファイルは含まない。
     *
     * @param managementCode 管理コード
     * @return 添付ファイル一覧（該当が無い場合は空）
     */
    public List<ProjectAttachmentsSummaryEntity> getAttachments(Integer managementCode) {
        return projectAttachmentsMapper.findByManagementCode(managementCode).stream()
                .sorted(Comparator.comparing(a -> a.getCreatedAt())).toList();
    }

    /**
     * 楽観排他を確認した上で案件情報を更新し、添付ファイルを追加し、任意で営業情報メールを送信する。
     *
     * @param form 案件更新Form（読込時更新日時を含む）
     * @param loginUserId ログイン中ユーザID
     * @return 更新結果（成功／排他失敗／添付上限超過）
     */
    @Transactional
    public ProjectUpdateResult update(ProjectUpdateForm form, String loginUserId) {
        boolean hasAttachment = isPresent(form.getAttachment());
        if (hasAttachment
                && existingAttachmentCount(form.getManagementCode()) > MAX_EXISTING_ATTACHMENTS) {
            return ProjectUpdateResult.ATTACHMENT_LIMIT;
        }

        String userName = userReferenceService.getUserName(loginUserId);
        Integer departmentId = userReferenceService.getUserDepartmentId(loginUserId);
        String departmentName = departmentReferenceService.getDepartmentName(departmentId);
        UsersEntity loginUser = usersMapper.findByUserId(loginUserId);
        String fromAddress = loginUser == null ? null : loginUser.getEmail();
        OffsetDateTime now = OffsetDateTime.now();

        ProjectsEntity entity = toEntity(form, userName, departmentName, now);
        if (projectsMapper.update(entity, form.getLoadedUpdatedAt()) == 0) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            return ProjectUpdateResult.CONFLICT;
        }

        if (hasAttachment) {
            projectAttachmentsMapper.insert(toAttachment(form.getAttachment(),
                    form.getManagementCode(), userName, departmentName, now));
        }

        if (form.isSendMail()) {
            scheduleMail(form, hasAttachment, fromAddress);
        }
        return ProjectUpdateResult.SUCCESS;
    }

    /**
     * ファイルIDを指定して添付ファイル（実ファイルを含む）を取得する。
     *
     * @param fileId ファイルID
     * @return 添付ファイル、存在しない場合は {@code null}
     */
    public ProjectAttachmentsEntity getAttachment(Integer fileId) {
        return projectAttachmentsMapper.findByFileId(fileId);
    }

    /**
     * ファイルIDを指定して添付ファイルを削除する。
     *
     * <p>
     * 削除後にデータベース例外が発生した場合に削除を確定させないため、トランザクション内で実行する （機能仕様書「上記以外のエラー」: 処理を中止しテーブルを実施前の状態に戻す）。
     *
     * @param fileId ファイルID
     * @return 削除件数
     */
    @Transactional
    public int deleteAttachment(Integer fileId) {
        return projectAttachmentsMapper.deleteByFileId(fileId);
    }

    private int existingAttachmentCount(Integer managementCode) {
        return projectAttachmentsMapper.findByManagementCode(managementCode).size();
    }

    private ProjectsEntity toEntity(
            ProjectUpdateForm form,
            String userName,
            String departmentName,
            OffsetDateTime now) {
        ProjectsEntity entity = new ProjectsEntity();
        entity.setManagementCode(form.getManagementCode());
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
        entity.setUpdatedBy(userName);
        entity.setUpdatedDepartment(departmentName);
        // 書き込む新しい更新日時＝更新時の現在時刻。添付ファイルの登録日時と同一の時刻を用いる
        // （サービス定義書「案件情報の更新」。排他照合の読込時更新日時は update の別引数で渡す）。
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

    private void scheduleMail(ProjectUpdateForm form, boolean hasAttachment, String fromAddress) {
        Integer managementCode = form.getManagementCode();
        ProjectsEntity updated = projectsMapper.findByManagementCode(managementCode);
        SalesMailCaseInfo caseInfo = SalesMailCaseInfos.of(updated);
        String url = properties.getMail().getBaseUrl() + "/projects/detail/" + managementCode;
        Runnable send = () -> salesMailService.send(SalesMailOperation.UPDATE, caseInfo,
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
