package com.system_server.ai_demo.apps.master.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.user.models.UserEditFailureType;
import com.system_server.ai_demo.apps.master.user.models.UserEditResult;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

class UserMasterServiceTest {

    private static final String HEADER = "編集属性,ユーザID,ユーザ名,メールアドレス,部署ID,ロール,パスワード";
    private static final String VALID_PASSWORD = "Passw0rd!";

    private final UsersMapper usersMapper = mock(UsersMapper.class);
    private final DepartmentReferenceService departmentReferenceService =
            mock(DepartmentReferenceService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserMasterService service =
            new UserMasterService(usersMapper, departmentReferenceService, passwordEncoder);

    private static String row(
            String edit,
            String userId,
            String userName,
            String email,
            String departmentId,
            String role,
            String password) {
        return String.join(",", edit, userId, userName, email, departmentId, role, password);
    }

    private static MultipartFile csv(String... bodyLines) {
        StringBuilder builder = new StringBuilder(HEADER).append("\r\n");
        for (String line : bodyLines) {
            builder.append(line).append("\r\n");
        }
        return new MockMultipartFile("csvFile", "u.csv", "text/csv",
                builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static UsersEntity user(
            String userId,
            String userName,
            String email,
            Integer departmentId,
            String role,
            String password) {
        UsersEntity entity = new UsersEntity();
        entity.setUserId(userId);
        entity.setUserName(userName);
        entity.setEmail(email);
        entity.setDepartmentId(departmentId);
        entity.setRole(role);
        entity.setPassword(password);
        return entity;
    }

    // ---- 全件取得 ----

    @Test
    void getAllUsers_delegatesToMapper() {
        when(usersMapper.findAll()).thenReturn(List.of(user("U01", "山田", "u@e.com", 1, "0", "h")));
        assertEquals(1, service.getAllUsers().size());
    }

    // ---- 編集: 正常系（行ごとの編集属性） ----

    @Test
    void edit_addsUser_andHashesPassword_whenEditAttributeAdd() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U01")).thenReturn(null);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("HASHED");

        UserEditResult result = service.edit(
                csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", VALID_PASSWORD)), "ADMIN");

        assertTrue(result.success());
        ArgumentCaptor<UsersEntity> captor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersMapper).insert(captor.capture());
        UsersEntity inserted = captor.getValue();
        assertEquals("U01", inserted.getUserId());
        assertEquals(Integer.valueOf(1), inserted.getDepartmentId());
        assertEquals("0", inserted.getRole());
        assertEquals("HASHED", inserted.getPassword());
    }

    @Test
    void edit_updatesUser_andHashesPassword_whenEditAttributeUpdate() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U01"))
                .thenReturn(user("U01", "旧名", "old@example.com", 2, "0", "OLDHASH"));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("NEWHASH");
        when(usersMapper.update(any())).thenReturn(1);

        UserEditResult result = service.edit(
                csv(row("2", "U01", "山田太郎", "u01@example.com", "1", "0", VALID_PASSWORD)), "ADMIN");

        assertTrue(result.success());
        ArgumentCaptor<UsersEntity> captor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersMapper).update(captor.capture());
        assertEquals("NEWHASH", captor.getValue().getPassword());
    }

    @Test
    void edit_keepsExistingPassword_whenUpdateWithBlankPassword() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U01"))
                .thenReturn(user("U01", "旧名", "old@example.com", 2, "0", "OLDHASH"));
        when(usersMapper.update(any())).thenReturn(1);

        UserEditResult result = service
                .edit(csv(row("2", "U01", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");

        assertTrue(result.success());
        ArgumentCaptor<UsersEntity> captor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersMapper).update(captor.capture());
        assertEquals("OLDHASH", captor.getValue().getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void edit_deletesUser_whenEditAttributeDelete() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U02"))
                .thenReturn(user("U02", "佐藤花子", "u02@example.com", 1, "0", "h"));
        when(usersMapper.deleteByUserId("U02")).thenReturn(1);

        UserEditResult result = service
                .edit(csv(row("3", "U02", "佐藤花子", "u02@example.com", "1", "0", "")), "ADMIN");

        assertTrue(result.success());
        verify(usersMapper).deleteByUserId("U02");
    }

    @Test
    void edit_doesNothing_whenEditAttributeNone() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);

        UserEditResult result = service
                .edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");

        assertTrue(result.success());
        verify(usersMapper, never()).insert(any());
        verify(usersMapper, never()).update(any());
        verify(usersMapper, never()).deleteByUserId(anyString());
    }

    // ---- 編集: 形式チェック ----

    @Test
    void edit_failsFormat_whenEmptyFile() {
        UserEditResult result =
                service.edit(new MockMultipartFile("csvFile", new byte[0]), "ADMIN");
        assertFalse(result.success());
        assertEquals(UserEditFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVファイルにデータがありませんでした。"), result.errors());
    }

    @Test
    void edit_failsFormat_whenHeaderOnly() {
        UserEditResult result = service.edit(csv(), "ADMIN");
        assertEquals(UserEditFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVファイルにデータがありませんでした。"), result.errors());
    }

    @Test
    void edit_failsFormat_whenWrongColumnCount() {
        UserEditResult result = service.edit(csv("0,U01,山田太郎,u01@example.com,1,0,,extra"), "ADMIN");
        assertEquals(UserEditFailureType.FORMAT, result.failureType());
        assertEquals(List.of("CSVの読み取りに失敗しました。フォーマットが正しいかファイルを確認してください。"), result.errors());
    }

    // ---- 編集: 入力値チェック（編集属性） ----

    @Test
    void edit_failsValidation_whenEditAttributeRequired() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result =
                service.edit(csv(row("", "U01", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(UserEditFailureType.VALIDATION, result.failureType());
        assertEquals(List.of("0002 - データ1行目 編集属性: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenEditAttributeNotInteger() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("x", "U01", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 編集属性: 整数を入力してください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenEditAttributeOutOfRange() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("4", "U01", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 編集属性: 0:何もしない, 1:追加, 2:更新, 3:削除のいずれかで入力してください"),
                result.errors());
    }

    // ---- 編集: 入力値チェック（ユーザID） ----

    @Test
    void edit_failsValidation_whenUserIdRequired() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result =
                service.edit(csv(row("0", "", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ユーザID: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenUserIdTooLong() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        String longId = "a".repeat(61);
        UserEditResult result = service
                .edit(csv(row("0", longId, "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ユーザID: 60文字以内で入力してください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenUserIdNotAlnum() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("0", "U_01", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ユーザID: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenUserIdDuplicated() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result =
                service.edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "1", "0", ""),
                        row("0", "U01", "佐藤花子", "u02@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0003 - データ2行目 ユーザID: 重複して設定しないでください(U01)"), result.errors());
    }

    // ---- 編集: 入力値チェック（ユーザ名・メール・部署ID・ロール） ----

    @Test
    void edit_failsValidation_whenUserNameRequired() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result =
                service.edit(csv(row("0", "U01", "", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ユーザ名: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenUserNameTooLong() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        String longName = "あ".repeat(31);
        UserEditResult result = service
                .edit(csv(row("0", "U01", longName, "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ユーザ名: 30文字以内で入力してください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenEmailNotHalfWidth() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("0", "U01", "山田太郎", "メール@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 メールアドレス: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenDepartmentIdNotExist() {
        when(departmentReferenceService.existsDepartment(999)).thenReturn(false);
        UserEditResult result = service
                .edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "999", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 部署ID: 部署IDはマスタに存在する値を設定してください"), result.errors());
    }

    @Test
    void edit_failsValidation_whenRoleOutOfRange() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "1", "5", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ロール: 0:一般ユーザ, 9:システム管理者のいずれかで入力してください。"),
                result.errors());
    }

    @Test
    void edit_failsValidation_whenUserNameNotAllowed() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("0", "U01", "ﾔﾏﾀﾞ", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ユーザ名: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenEmailRequired() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result =
                service.edit(csv(row("0", "U01", "山田太郎", "", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 メールアドレス: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenEmailTooLong() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        String longEmail = "a".repeat(61);
        UserEditResult result =
                service.edit(csv(row("0", "U01", "山田太郎", longEmail, "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 メールアドレス: 60文字以内で入力してください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenDepartmentIdRequired() {
        UserEditResult result =
                service.edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 部署ID: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenDepartmentIdNotInteger() {
        UserEditResult result = service
                .edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "x", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 部署ID: 整数を入力してください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenRoleRequired() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result =
                service.edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "1", "", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ロール: この項目は入力が必要です。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenRoleNotInteger() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("0", "U01", "山田太郎", "u01@example.com", "1", "x", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 ロール: 整数を入力してください。"), result.errors());
    }

    // ---- 編集: 入力値チェック（パスワード・条件付き） ----

    @Test
    void edit_failsValidation_whenPasswordRequiredOnAdd() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 パスワード: ユーザ追加の場合、必須項目です。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenPasswordTooShort() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", "Ab1!")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 パスワード: 8文字以上で入力してください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenPasswordLacksCategories() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service.edit(
                csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", "abcdefgh")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 パスワード: 大文字小文字数字記号のうち３つ以上含めてください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenPasswordTooLong() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        String longPassword = "Aa1!".repeat(16);
        UserEditResult result = service.edit(
                csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", longPassword)), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 パスワード: 60文字以内で入力してください。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenPasswordNotHalfWidth() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service.edit(
                csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", "Passw0rd！")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 パスワード: 使用不可能な文字が含まれています。"), result.errors());
    }

    @Test
    void edit_failsValidation_whenUpdatePasswordInvalid() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result = service
                .edit(csv(row("2", "U01", "山田太郎", "u01@example.com", "1", "0", "Ab1!")), "ADMIN");
        assertEquals(List.of("0002 - データ1行目 パスワード: 8文字以上で入力してください。"), result.errors());
    }

    @Test
    void edit_reportsErrorsByColumnOrder_whenSameRowHasMultiple() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        UserEditResult result =
                service.edit(csv(row("", "", "山田太郎", "u01@example.com", "1", "0", "")), "ADMIN");
        assertEquals(
                List.of("0002 - データ1行目 編集属性: この項目は入力が必要です。", "0002 - データ1行目 ユーザID: この項目は入力が必要です。"),
                result.errors());
    }

    // ---- 編集: 業務エラー ----

    @Test
    void edit_failsBusiness_whenAddExistingUser() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U01"))
                .thenReturn(user("U01", "既存", "exist@example.com", 1, "0", "h"));

        UserEditResult result = service.edit(
                csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", VALID_PASSWORD)), "ADMIN");

        assertEquals(UserEditFailureType.BUSINESS, result.failureType());
        assertEquals("1行目で異常終了しました。", result.errorTitle());
        assertEquals(List.of("ユーザIDU01は既に存在します。"), result.errors());
        verify(usersMapper, never()).insert(any());
    }

    @Test
    void edit_failsBusiness_whenUpdateMissingUser() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U01")).thenReturn(null);

        UserEditResult result = service.edit(
                csv(row("2", "U01", "山田太郎", "u01@example.com", "1", "0", VALID_PASSWORD)), "ADMIN");

        assertEquals(UserEditFailureType.BUSINESS, result.failureType());
        assertEquals(List.of("存在しないユーザ(U01)です。"), result.errors());
    }

    @Test
    void edit_failsBusiness_whenUpdateAffectsNoRow() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U01"))
                .thenReturn(user("U01", "旧名", "old@example.com", 1, "0", "OLDHASH"));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("NEWHASH");
        when(usersMapper.update(any())).thenReturn(0);

        UserEditResult result = service.edit(
                csv(row("2", "U01", "山田太郎", "u01@example.com", "1", "0", VALID_PASSWORD)), "ADMIN");

        assertEquals(List.of("他のユーザがデータを更新中です。"), result.errors());
    }

    @Test
    void edit_failsBusiness_whenDeleteSelf() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);

        UserEditResult result = service
                .edit(csv(row("3", "ADMIN", "管理者太郎", "admin@example.com", "1", "9", "")), "ADMIN");

        assertEquals(UserEditFailureType.BUSINESS, result.failureType());
        assertEquals(List.of("自分のデータは削除できません。"), result.errors());
        verify(usersMapper, never()).deleteByUserId(anyString());
    }

    @Test
    void edit_failsBusiness_whenDeleteMissingUser() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U02")).thenReturn(null);

        UserEditResult result = service
                .edit(csv(row("3", "U02", "佐藤花子", "u02@example.com", "1", "0", "")), "ADMIN");

        assertEquals(List.of("存在しないユーザ(U02)です。"), result.errors());
    }

    @Test
    void edit_failsBusiness_whenDeleteAffectsNoRow() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U02"))
                .thenReturn(user("U02", "佐藤花子", "u02@example.com", 1, "0", "h"));
        when(usersMapper.deleteByUserId("U02")).thenReturn(0);

        UserEditResult result = service
                .edit(csv(row("3", "U02", "佐藤花子", "u02@example.com", "1", "0", "")), "ADMIN");

        assertEquals(List.of("他のユーザがデータを更新中です。"), result.errors());
    }

    @Test
    void edit_stopsAtFirstBusinessError_andSkipsLaterRows() {
        when(departmentReferenceService.existsDepartment(1)).thenReturn(true);
        when(usersMapper.findByUserId("U01"))
                .thenReturn(user("U01", "既存", "exist@example.com", 1, "0", "h"));

        UserEditResult result = service
                .edit(csv(row("1", "U01", "山田太郎", "u01@example.com", "1", "0", VALID_PASSWORD),
                        row("3", "U02", "佐藤花子", "u02@example.com", "1", "0", "")), "ADMIN");

        assertEquals(UserEditFailureType.BUSINESS, result.failureType());
        assertEquals("1行目で異常終了しました。", result.errorTitle());
        verify(usersMapper, never()).deleteByUserId(anyString());
    }

    // ---- CSV 生成 ----

    @Test
    void toCsv_writesBomHeaderAndBody_withZeroEditAttributeAndEmptyPassword() {
        byte[] csv = service.toCsv(List.of(user("U01", "山田太郎", "u01@example.com", 4, "9", "HASH")));
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertEquals(HEADER + "\r\n0,U01,山田太郎,u01@example.com,4,9,\r\n", text);
    }

    @Test
    void toCsv_writesHeaderOnly_whenNoRecords() {
        byte[] csv = service.toCsv(List.of());
        String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertEquals(HEADER + "\r\n", text);
    }
}
