package com.system_server.ai_demo.apps.master.department.services;

import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvFormatException;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.apps.master.common.csv.CsvSupport;
import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import com.system_server.ai_demo.database.mapper.DepartmentsMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 部署マスタ管理サービス。全件取得と、CSV による洗い替え（全件削除して再登録）を行う。
 *
 * <p>
 * 洗い替えはファイル形式チェック・入力値チェックを行い、違反時は DB を変更せずエラー明細を返す。DB 処理での例外時はロール
 * バックして例外を送出する（呼び出し側でサーバー内部エラーとする）。CSV の汎用処理は {@link CsvSupport} に委譲する。
 */
@Service
public class DepartmentMasterService {

    private static final int COLUMN_COUNT = 2;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MIN_ID = 1;
    private static final int MAX_ID = Integer.MAX_VALUE;

    /** CSV ファイルのヘッダー（＝テーブル列ヘッダーの値。「部署ID」の「ID」は半角）。 */
    private static final String CSV_HEADER = "部署ID,部署名";

    /** エラーメッセージ用の列ヘッダー名（入力値チェック一覧の列ヘッダー名）。 */
    private static final String COLUMN_ID = "部署ID";
    private static final String COLUMN_NAME = "部署名";

    private final DepartmentsMapper departmentsMapper;

    public DepartmentMasterService(DepartmentsMapper departmentsMapper) {
        this.departmentsMapper = departmentsMapper;
    }

    /**
     * 部署マスタの全件を部署ID昇順で取得する。
     *
     * @return 部署マスタレコードの一覧
     */
    public List<DepartmentsEntity> getAllDepartments() {
        return departmentsMapper.findAll();
    }

    /**
     * CSV ファイルで部署マスタを洗い替える（全件削除して再登録）。
     *
     * @param csvFile アップロードされた CSV ファイル
     * @return 洗い替え結果（成功、または失敗種別＋エラー明細）
     */
    @Transactional
    public CsvReplaceResult replaceAll(MultipartFile csvFile) {
        List<String[]> rows;
        try {
            rows = CsvSupport.parse(csvFile, COLUMN_COUNT);
        } catch (CsvFormatException ex) {
            return CsvReplaceResult.ofFailure(CsvFailureType.FORMAT, List.of(ex.getMessage()));
        }

        List<String> errors = validateRows(rows);
        if (!errors.isEmpty()) {
            return CsvReplaceResult.ofFailure(CsvFailureType.VALIDATION, errors);
        }

        departmentsMapper.deleteAll();
        for (int i = 1; i < rows.size(); i++) {
            String[] fields = rows.get(i);
            DepartmentsEntity entity = new DepartmentsEntity();
            entity.setDepartmentId(Integer.parseInt(fields[0]));
            entity.setDepartmentName(fields[1]);
            departmentsMapper.insert(entity);
        }
        return CsvReplaceResult.ofSuccess();
    }

    /**
     * 部署マスタレコードの一覧を CSV（UTF-8 BOM 付き・CRLF）に変換する。ヘッダー行＋ボディ行（1行1レコード）。
     *
     * @param departments 出力対象（呼び出し側で並び替え済み）
     * @return CSV のバイト列
     */
    public byte[] toCsv(List<DepartmentsEntity> departments) {
        List<String[]> rows = new ArrayList<>();
        for (DepartmentsEntity department : departments) {
            rows.add(new String[] {String.valueOf(department.getDepartmentId()),
                    department.getDepartmentName()});
        }
        return CsvSupport.toCsv(CSV_HEADER, rows);
    }

    private static List<String> validateRows(List<String[]> rows) {
        List<String> errors = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] fields = rows.get(i);
            int dataLineNumber = i;
            int csvLineNumber = i + 1;
            String idMessage = CsvSupport.validateIntegerId(fields[0], seenIds, MIN_ID, MAX_ID);
            if (idMessage != null) {
                errors.add(CsvSupport.formatError(csvLineNumber, dataLineNumber, COLUMN_ID,
                        idMessage));
            }
            String nameMessage = CsvSupport.validateFullWidthName(fields[1], MAX_NAME_LENGTH);
            if (nameMessage != null) {
                errors.add(CsvSupport.formatError(csvLineNumber, dataLineNumber, COLUMN_NAME,
                        nameMessage));
            }
        }
        return errors;
    }
}
