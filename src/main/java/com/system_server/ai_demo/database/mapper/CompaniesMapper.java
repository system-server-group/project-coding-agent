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
}
