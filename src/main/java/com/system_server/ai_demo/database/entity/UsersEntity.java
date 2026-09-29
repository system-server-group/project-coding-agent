package com.system_server.ai_demo.database.entity;

import lombok.Data;

/**
 * ユーザマスタレコード（users テーブル）のエンティティ。
 *
 * <p>
 * {@code role} は区分コード（文字列）を保持する。列挙型への変換はサービス層で行う。
 */
@Data
public class UsersEntity {

    private String userId;
    private String userName;
    private String email;
    private Integer departmentId;
    private String role;
    private String password;
}
