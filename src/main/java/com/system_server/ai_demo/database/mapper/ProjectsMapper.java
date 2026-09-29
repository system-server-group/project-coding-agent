package com.system_server.ai_demo.database.mapper;

import com.system_server.ai_demo.apps.projects.models.ProjectSearchQuery;
import com.system_server.ai_demo.database.entity.ProjectsEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 案件情報（projects）の 1 表専用データアクセス。
 */
@Mapper
public interface ProjectsMapper {

    /**
     * 検索条件に合致する案件情報を全件取得する（既定の並び順は管理コードの降順）。
     *
     * <p>
     * 入力のある条件のみ適用し、複数条件は AND 結合、条件皆無なら全件を対象とする。キーワードは指定された検索対象列のいずれかに 部分一致（対象列間は
     * OR）、ステータスは指定値のいずれかに一致（OR）、開始日／終了日／登録日時は各々が下限以上かつ上限以下
     * （登録日時は日付単位で比較）、登録者／登録部署／取引先は部分一致、BP募集限定の指定時は BP募集(要否) が「必要」に限定する。
     *
     * @param query 検索クエリ（正規化済み）
     * @return 条件に合致する案件情報の一覧（管理コード降順）
     */
    List<ProjectsEntity> search(ProjectSearchQuery query);

    /**
     * 管理コードを指定して案件情報を 1 件取得する。
     *
     * @param managementCode 管理コード
     * @return 案件情報、存在しない場合は {@code null}
     */
    ProjectsEntity findByManagementCode(@Param("managementCode") Integer managementCode);

    /**
     * 案件情報を 1 件登録する（管理コードはテーブルの自動採番。採番値は引数の {@code managementCode} に設定される）。
     *
     * @param project 登録する案件情報
     * @return 登録件数
     */
    int insert(ProjectsEntity project);

    /**
     * 案件情報を更新する（楽観排他）。
     *
     * <p>
     * {@code managementCode} が一致し、かつ行の更新日時が {@code loadedUpdatedAt}（読込時点の更新日時）に一致する行のみを
     * 更新する。不一致時は更新件数 0 となり競合と判定する。登録者・登録部署・登録日時は変更しない。
     *
     * @param project 更新する案件情報（{@code updatedAt} は書き込む新しい更新日時＝更新時の現在時刻）
     * @param loadedUpdatedAt 排他判定に用いる読込時点の更新日時
     * @return 更新件数（競合時は 0）
     */
    int update(
            @Param("project") ProjectsEntity project,
            @Param("loadedUpdatedAt") OffsetDateTime loadedUpdatedAt);
}
