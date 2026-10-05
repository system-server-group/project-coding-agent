package com.system_server.ai_demo.database.mapper;

import com.system_server.ai_demo.database.entity.UsersEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ユーザマスタ（users）の 1 表専用データアクセス。
 */
@Mapper
public interface UsersMapper {

    /**
     * ユーザマスタの全件をユーザID昇順で取得する。
     *
     * @return ユーザマスタレコードの一覧
     */
    List<UsersEntity> findAll();

    /**
     * ユーザIDを指定してユーザマスタレコードを 1 件取得する（認証・存在確認用）。
     *
     * @param userId ユーザID
     * @return ユーザマスタレコード、存在しない場合は {@code null}
     */
    UsersEntity findByUserId(@Param("userId") String userId);

    /**
     * ユーザマスタレコードを 1 件登録する（ユーザIDは入力値を使用）。
     *
     * @param user 登録するユーザマスタレコード
     * @return 登録件数
     */
    int insert(UsersEntity user);

    /**
     * ユーザマスタレコードを更新する（ユーザマスタは版列が無く楽観排他条件は付さない）。
     *
     * @param user 更新するユーザマスタレコード
     * @return 更新件数
     */
    int update(UsersEntity user);

    /**
     * ユーザIDを指定してユーザマスタレコードを 1 件削除する。
     *
     * @param userId ユーザID
     * @return 削除件数
     */
    int deleteByUserId(@Param("userId") String userId);
}
