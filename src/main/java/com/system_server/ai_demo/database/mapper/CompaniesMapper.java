package com.system_server.ai_demo.database.mapper;

import com.system_server.ai_demo.database.entity.CompaniesEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会社マスタ（companies）の 1 表専用データアクセス。
 */
@Mapper
public interface CompaniesMapper {

    /**
     * 会社マスタの全件を会社ID昇順で取得する。
     *
     * @return 会社マスタレコードの一覧
     */
    List<CompaniesEntity> findAll();

    /**
     * 会社マスタの全件を削除する（洗い替えの初期化）。
     *
     * @return 削除件数
     */
    int deleteAll();

    /**
     * 会社マスタレコードを 1 件登録する（会社IDは入力値を使用）。
     *
     * @param company 登録する会社マスタレコード
     * @return 登録件数
     */
    int insert(CompaniesEntity company);
}
