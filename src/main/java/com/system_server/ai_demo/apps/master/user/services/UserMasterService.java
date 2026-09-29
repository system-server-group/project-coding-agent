package com.system_server.ai_demo.apps.master.user.services;

import com.system_server.ai_demo.apps.master.common.csv.CsvFormatException;
import com.system_server.ai_demo.apps.master.common.csv.CsvSupport;
import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.user.models.UserCsvRecord;
import com.system_server.ai_demo.apps.master.user.models.UserEditResult;
import com.system_server.ai_demo.commons.code.CodeEnums;
import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import com.system_server.ai_demo.enums.EditAttribute;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * ユーザマスタ管理サービス。全件取得と、CSV による編集（行ごとの編集属性に従う追加・更新・削除）を行う。
 *
 * <p>
 * 編集はファイル形式チェック・入力値チェック・業務エラー判定を行い、違反・異常時は編集を実施せず編集前の状態に戻す（業務エラー はトランザクションをロールバックする）。CSV
 * の汎用処理（形式チェック・読取・生成・エラー整形）は {@link CsvSupport} に委譲する。
 */
@Service
public class UserMasterService {

    private static final int MAX_USER_ID_LENGTH = 60;
    private static final int MAX_USER_NAME_LENGTH = 30;
    private static final int MAX_EMAIL_LENGTH = 60;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 60;
    private static final int REQUIRED_PASSWORD_CATEGORIES = 3;

    /** CSV ファイルのヘッダー行（＝テーブル列ヘッダーの値、入力値チェックの列ヘッダー名と一致）。 */
    private static final String CSV_HEADER = "編集属性,ユーザID,ユーザ名,メールアドレス,部署ID,ロール,パスワード";

    private static final String COLUMN_EDIT_ATTRIBUTE = "編集属性";
    private static final String COLUMN_USER_ID = "ユーザID";
    private static final String COLUMN_USER_NAME = "ユーザ名";
    private static final String COLUMN_EMAIL = "メールアドレス";
    private static final String COLUMN_DEPARTMENT_ID = "部署ID";
    private static final String COLUMN_ROLE = "ロール";
    private static final String COLUMN_PASSWORD = "パスワード";

    private static final String MSG_EDIT_ATTRIBUTE_RANGE =
            "0:何もしない, 1:追加, 2:更新, 3:削除のいずれかで入力してください";
    private static final String MSG_ROLE_RANGE = "0:一般ユーザ, 9:システム管理者のいずれかで入力してください。";
    private static final String MSG_DEPARTMENT_NOT_EXIST = "部署IDはマスタに存在する値を設定してください";
    private static final String MSG_PASSWORD_REQUIRED_ON_ADD = "ユーザ追加の場合、必須項目です。";
    private static final String MSG_PASSWORD_COMPLEXITY = "大文字小文字数字記号のうち３つ以上含めてください。";

    private final UsersMapper usersMapper;
    private final DepartmentReferenceService departmentReferenceService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 依存を注入して生成する。
     *
     * @param usersMapper ユーザマスタ データアクセス
     * @param departmentReferenceService 部署参照サービス（部署ID存在判定）
     * @param passwordEncoder パスワードハッシュ化器（BCrypt $2a／ラウンド10）
     */
    public UserMasterService(UsersMapper usersMapper,
            DepartmentReferenceService departmentReferenceService,
            PasswordEncoder passwordEncoder) {
        this.usersMapper = usersMapper;
        this.departmentReferenceService = departmentReferenceService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * ユーザマスタの全件をユーザID昇順で取得する。
     *
     * @return ユーザマスタレコードの一覧
     */
    public List<UsersEntity> getAllUsers() {
        return usersMapper.findAll();
    }

    /**
     * アップロードされた CSV に従いユーザマスタを編集する（行ごとの編集属性で追加・更新・削除）。
     *
     * <p>
     * 形式チェック・入力値チェック違反時は編集を行わずエラー明細を返す。業務エラー発生時はトランザクションをロールバックして
     * 編集前の状態に戻し、業務エラー結果を返す。上記以外のデータベース例外は伝播させ、呼び出し側でサーバー内部エラーとする。
     *
     * @param csvFile アップロードされた CSV ファイル
     * @param loginUserId ログイン中ユーザID（自己削除禁止の判定に用いる）
     * @return 編集結果（成功、または失敗種別＋エラー明細）
     */
    @Transactional
    public UserEditResult edit(MultipartFile csvFile, String loginUserId) {
        List<String[]> rows;
        try {
            rows = CsvSupport.parse(csvFile, UserCsvRecord.COLUMN_COUNT);
        } catch (CsvFormatException ex) {
            return UserEditResult.ofFormatFailure(List.of(ex.getMessage()));
        }

        List<UserCsvRecord> records = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            records.add(UserCsvRecord.of(rows.get(i)));
        }

        List<String> errors = validateRecords(records);
        if (!errors.isEmpty()) {
            return UserEditResult.ofValidationFailure(errors);
        }

        try {
            applyEdits(records, loginUserId);
        } catch (UserBusinessException ex) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            return UserEditResult.ofBusinessFailure(ex.getDataLineNumber() + "行目で異常終了しました。",
                    ex.getMessage());
        }
        return UserEditResult.ofSuccess();
    }

    /**
     * ユーザマスタレコードの一覧を CSV（UTF-8 BOM 付き・CRLF）に変換する。編集属性は全行「0」、パスワードは空欄とする。
     *
     * @param users 出力対象（呼び出し側で並び替え済み）
     * @return CSV のバイト列
     */
    public byte[] toCsv(List<UsersEntity> users) {
        List<String[]> rows = new ArrayList<>();
        for (UsersEntity user : users) {
            rows.add(new String[] {"0", user.getUserId(), user.getUserName(), user.getEmail(),
                    String.valueOf(user.getDepartmentId()), user.getRole(), ""});
        }
        return CsvSupport.toCsv(CSV_HEADER, rows);
    }

    private List<String> validateRecords(List<UserCsvRecord> records) {
        List<String> errors = new ArrayList<>();
        Set<String> seenUserIds = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            UserCsvRecord record = records.get(index);
            int dataLineNumber = index + 1;
            int csvLineNumber = index + 2;
            addError(errors, csvLineNumber, dataLineNumber, COLUMN_EDIT_ATTRIBUTE,
                    validateEditAttribute(record.editAttribute()));
            addError(errors, csvLineNumber, dataLineNumber, COLUMN_USER_ID,
                    validateUserId(record.userId(), seenUserIds));
            addError(errors, csvLineNumber, dataLineNumber, COLUMN_USER_NAME,
                    validateUserName(record.userName()));
            addError(errors, csvLineNumber, dataLineNumber, COLUMN_EMAIL,
                    validateEmail(record.email()));
            addError(errors, csvLineNumber, dataLineNumber, COLUMN_DEPARTMENT_ID,
                    validateDepartmentId(record.departmentId()));
            addError(errors, csvLineNumber, dataLineNumber, COLUMN_ROLE,
                    validateRole(record.role()));
            addError(errors, csvLineNumber, dataLineNumber, COLUMN_PASSWORD,
                    validatePassword(record.editAttribute(), record.password()));
        }
        return errors;
    }

    private static void addError(
            List<String> errors,
            int csvLineNumber,
            int dataLineNumber,
            String column,
            String message) {
        if (message != null) {
            errors.add(CsvSupport.formatError(csvLineNumber, dataLineNumber, column, message));
        }
    }

    private static String validateEditAttribute(String raw) {
        if (isEmpty(raw)) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (!raw.matches("-?\\d+")) {
            return CheckMessages.INTEGER_VALUE;
        }
        int value = parseIntOrMin(raw);
        if (value < 0 || value > 3) {
            return MSG_EDIT_ATTRIBUTE_RANGE;
        }
        return null;
    }

    private static String validateUserId(String raw, Set<String> seenUserIds) {
        if (isEmpty(raw)) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (raw.codePointCount(0, raw.length()) > MAX_USER_ID_LENGTH) {
            return CheckMessages.MAX_LENGTH.replace("{max}", String.valueOf(MAX_USER_ID_LENGTH));
        }
        if (!raw.chars().allMatch(UserMasterService::isHalfWidthAlnum)) {
            return CheckMessages.ALLOWED_CHARACTERS;
        }
        if (!seenUserIds.add(raw)) {
            return "重複して設定しないでください(" + raw + ")";
        }
        return null;
    }

    private static String validateUserName(String raw) {
        if (isEmpty(raw)) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (raw.codePointCount(0, raw.length()) > MAX_USER_NAME_LENGTH) {
            return CheckMessages.MAX_LENGTH.replace("{max}", String.valueOf(MAX_USER_NAME_LENGTH));
        }
        if (!raw.codePoints().allMatch(UserMasterService::isDefaultAllowed)) {
            return CheckMessages.ALLOWED_CHARACTERS;
        }
        return null;
    }

    private static String validateEmail(String raw) {
        if (isEmpty(raw)) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (raw.codePointCount(0, raw.length()) > MAX_EMAIL_LENGTH) {
            return CheckMessages.MAX_LENGTH.replace("{max}", String.valueOf(MAX_EMAIL_LENGTH));
        }
        if (!raw.chars().allMatch(UserMasterService::isHalfWidthPrintable)) {
            return CheckMessages.ALLOWED_CHARACTERS;
        }
        return null;
    }

    private String validateDepartmentId(String raw) {
        if (isEmpty(raw)) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (!raw.matches("-?\\d+")) {
            return CheckMessages.INTEGER_VALUE;
        }
        Integer departmentId;
        try {
            departmentId = Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return MSG_DEPARTMENT_NOT_EXIST;
        }
        if (!departmentReferenceService.existsDepartment(departmentId)) {
            return MSG_DEPARTMENT_NOT_EXIST;
        }
        return null;
    }

    private static String validateRole(String raw) {
        if (isEmpty(raw)) {
            return CheckMessages.REQUIRED_INPUT;
        }
        if (!raw.matches("-?\\d+")) {
            return CheckMessages.INTEGER_VALUE;
        }
        if (!"0".equals(raw) && !"9".equals(raw)) {
            return MSG_ROLE_RANGE;
        }
        return null;
    }

    private static String validatePassword(String editAttribute, String password) {
        boolean add = "1".equals(editAttribute);
        boolean update = "2".equals(editAttribute);
        boolean blank = isEmpty(password);
        if (add && blank) {
            return MSG_PASSWORD_REQUIRED_ON_ADD;
        }
        if (!add && !(update && !blank)) {
            return null;
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_PASSWORD_LENGTH) {
            return CheckMessages.MIN_LENGTH.replace("{min}", String.valueOf(MIN_PASSWORD_LENGTH));
        }
        if (length > MAX_PASSWORD_LENGTH) {
            return CheckMessages.MAX_LENGTH.replace("{max}", String.valueOf(MAX_PASSWORD_LENGTH));
        }
        if (!password.chars().allMatch(UserMasterService::isHalfWidthPrintable)) {
            return CheckMessages.ALLOWED_CHARACTERS;
        }
        if (countCharacterCategories(password) < REQUIRED_PASSWORD_CATEGORIES) {
            return MSG_PASSWORD_COMPLEXITY;
        }
        return null;
    }

    private void applyEdits(List<UserCsvRecord> records, String loginUserId) {
        for (int index = 0; index < records.size(); index++) {
            UserCsvRecord record = records.get(index);
            int dataLineNumber = index + 1;
            EditAttribute editAttribute =
                    CodeEnums.fromCode(EditAttribute.class, record.editAttribute());
            switch (editAttribute) {
                case NONE -> {
                    // 何もしない
                }
                case ADD -> addUser(record, dataLineNumber);
                case UPDATE -> updateUser(record, dataLineNumber);
                case DELETE -> deleteUser(record, loginUserId, dataLineNumber);
                default -> {
                    // 入力値チェック通過済みのため到達しない
                }
            }
        }
    }

    private void addUser(UserCsvRecord record, int dataLineNumber) {
        if (usersMapper.findByUserId(record.userId()) != null) {
            throw new UserBusinessException(dataLineNumber,
                    "ユーザID" + record.userId() + "は既に存在します。");
        }
        UsersEntity entity = toEntity(record);
        entity.setPassword(passwordEncoder.encode(record.password()));
        usersMapper.insert(entity);
    }

    private void updateUser(UserCsvRecord record, int dataLineNumber) {
        UsersEntity existing = usersMapper.findByUserId(record.userId());
        if (existing == null) {
            throw new UserBusinessException(dataLineNumber, "存在しないユーザ(" + record.userId() + ")です。");
        }
        UsersEntity entity = toEntity(record);
        if (isEmpty(record.password())) {
            entity.setPassword(existing.getPassword());
        } else {
            entity.setPassword(passwordEncoder.encode(record.password()));
        }
        if (usersMapper.update(entity) == 0) {
            throw new UserBusinessException(dataLineNumber, "他のユーザがデータを更新中です。");
        }
    }

    private void deleteUser(UserCsvRecord record, String loginUserId, int dataLineNumber) {
        if (record.userId().equals(loginUserId)) {
            throw new UserBusinessException(dataLineNumber, "自分のデータは削除できません。");
        }
        if (usersMapper.findByUserId(record.userId()) == null) {
            throw new UserBusinessException(dataLineNumber, "存在しないユーザ(" + record.userId() + ")です。");
        }
        if (usersMapper.deleteByUserId(record.userId()) == 0) {
            throw new UserBusinessException(dataLineNumber, "他のユーザがデータを更新中です。");
        }
    }

    private static UsersEntity toEntity(UserCsvRecord record) {
        UsersEntity entity = new UsersEntity();
        entity.setUserId(record.userId());
        entity.setUserName(record.userName());
        entity.setEmail(record.email());
        entity.setDepartmentId(Integer.valueOf(record.departmentId()));
        entity.setRole(record.role());
        return entity;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static int parseIntOrMin(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean isHalfWidthAlnum(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9') || (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z');
    }

    private static boolean isHalfWidthPrintable(int codePoint) {
        return codePoint >= 0x20 && codePoint <= 0x7E;
    }

    private static boolean isDefaultAllowed(int codePoint) {
        if (codePoint >= 0x20 && codePoint <= 0x7E) {
            return true;
        }
        if (codePoint >= 0xFF61 && codePoint <= 0xFF9F) {
            return false;
        }
        return codePoint > 0x9F;
    }

    private static int countCharacterCategories(String value) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (int codePoint : value.codePoints().toArray()) {
            if (codePoint >= 'A' && codePoint <= 'Z') {
                upper = true;
            } else if (codePoint >= 'a' && codePoint <= 'z') {
                lower = true;
            } else if (codePoint >= '0' && codePoint <= '9') {
                digit = true;
            } else {
                symbol = true;
            }
        }
        int count = 0;
        for (boolean present : new boolean[] {upper, lower, digit, symbol}) {
            if (present) {
                count++;
            }
        }
        return count;
    }
}
