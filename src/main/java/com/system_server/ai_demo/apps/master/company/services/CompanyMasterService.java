package com.system_server.ai_demo.apps.master.company.services;

import com.system_server.ai_demo.apps.master.common.csv.CsvFailureType;
import com.system_server.ai_demo.apps.master.common.csv.CsvFormatException;
import com.system_server.ai_demo.apps.master.common.csv.CsvReplaceResult;
import com.system_server.ai_demo.apps.master.common.csv.CsvSupport;
import com.system_server.ai_demo.database.entity.CompaniesEntity;
import com.system_server.ai_demo.database.mapper.CompaniesMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 会社マスタ管理サービス。全件取得、CSV ダウンロード用の CSV 変換と、CSV による洗い替え（全件削除して再登録）を行う。
 *
 * <p>
 * 洗い替えはファイル形式チェック・入力値チェックを行い、違反時は DB を変更せずエラー明細を返す。DB 処理での例外時はロール
 * バックして例外を送出する（呼び出し側でサーバー内部エラーとする）。CSV の汎用処理は {@link CsvSupport} に委譲する。
 */
@Service
public class CompanyMasterService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MIN_ID = 1;
    private static final int MAX_ID = Integer.MAX_VALUE;

    /** 列ヘッダー名（＝テーブル列ヘッダーの値、入力値チェック一覧の列ヘッダー名。「会社ID」の「ID」は半角）。 */
    private static final String COLUMN_ID = "会社ID";
    private static final String COLUMN_NAME = "会社名";

    /** CSV ファイルの列ヘッダー（列順どおり）。アップロード時の形式チェックとダウンロード時のヘッダー行に用いる。 */
    private static final String[] CSV_HEADER_COLUMNS = {COLUMN_ID, COLUMN_NAME};

    /** CSV ファイルのヘッダー行（カンマ区切り）。 */
    private static final String CSV_HEADER = String.join(",", CSV_HEADER_COLUMNS);

    private final CompaniesMapper companiesMapper;

    public CompanyMasterService(CompaniesMapper companiesMapper) {
        this.companiesMapper = companiesMapper;
    }

    /**
     * 会社マスタの全件を会社ID昇順で取得する。
     *
     * @return 会社マスタレコードの一覧（0 件の場合は空の一覧）
     */
    public List<CompaniesEntity> getAllCompanies() {
        return companiesMapper.findAll();
    }

    /**
     * CSV ファイルで会社マスタを洗い替える（全件削除して再登録）。
     *
     * <p>
     * ファイル形式チェック・入力値チェックの後、CSV のボディ行を会社マスタレコードに読み込み、全件削除してから読み込んだレコードを登録する。
     *
     * @param csvFile アップロードされた CSV ファイル
     * @return 洗い替え結果（成功、または失敗種別＋エラー明細）
     */
    @Transactional
    public CsvReplaceResult replaceAll(MultipartFile csvFile) {
        List<String[]> rows;
        try {
            rows = CsvSupport.parse(csvFile, CSV_HEADER_COLUMNS);
        } catch (CsvFormatException ex) {
            return CsvReplaceResult.ofFailure(CsvFailureType.FORMAT, List.of(ex.getMessage()));
        }

        List<String> errors = validateRows(rows);
        if (!errors.isEmpty()) {
            return CsvReplaceResult.ofFailure(CsvFailureType.VALIDATION, errors);
        }

        List<CompaniesEntity> companies = readRecords(rows);
        companiesMapper.deleteAll();
        for (CompaniesEntity company : companies) {
            companiesMapper.insert(company);
        }
        return CsvReplaceResult.ofSuccess();
    }

    /**
     * 会社マスタレコードの一覧を CSV（UTF-8 BOM 付き・CRLF）に変換する。ヘッダー行＋ボディ行（1行1レコード）。
     *
     * @param companies 出力対象（呼び出し側で並び替え済み）
     * @return CSV のバイト列
     */
    public byte[] toCsv(List<CompaniesEntity> companies) {
        List<String[]> rows = new ArrayList<>();
        for (CompaniesEntity company : companies) {
            rows.add(new String[] {String.valueOf(company.getCompanyId()),
                    company.getCompanyName()});
        }
        return CsvSupport.toCsv(CSV_HEADER, rows);
    }

    /**
     * 入力値チェック通過済みの CSV 行のボディ行を会社マスタレコードに読み込む。列の対応は列順とし、1列目を会社ID、2列目を会社名とする。
     *
     * @param rows ヘッダー行を含む全行（各行は列値の配列）
     * @return 会社マスタレコードの一覧（ボディ行の並び順）
     */
    private static List<CompaniesEntity> readRecords(List<String[]> rows) {
        List<CompaniesEntity> companies = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] fields = rows.get(i);
            CompaniesEntity company = new CompaniesEntity();
            company.setCompanyId(Integer.parseInt(fields[0]));
            company.setCompanyName(fields[1]);
            companies.add(company);
        }
        return companies;
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
