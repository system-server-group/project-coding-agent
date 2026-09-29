package com.system_server.ai_demo.database.mapper;

import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 部署マスタ（departments）の 1 表専用データアクセス。
 */
@Mapper
public interface DepartmentsMapper {

    /**
     * 部署マスタの全件を部署ID昇順で取得する。
     *
     * @return 部署マスタレコードの一覧
     */
    List<DepartmentsEntity> findAll();

    /**
     * 部署マスタの全件を削除する（洗い替えの初期化）。
     *
     * @return 削除件数
     */
    int deleteAll();

    /**
     * 部署マスタレコードを 1 件登録する（部署IDは入力値を使用）。
     *
     * @param department 登録する部署マスタレコード
     * @return 登録件数
     */
    int insert(DepartmentsEntity department);

    /**
     * 部署IDを指定して部署マスタレコードを 1 件取得する（存在チェック用）。
     *
     * @param departmentId 部署ID
     * @return 部署マスタレコード、存在しない場合は {@code null}
     */
    DepartmentsEntity findByDepartmentId(@Param("departmentId") Integer departmentId);
}
