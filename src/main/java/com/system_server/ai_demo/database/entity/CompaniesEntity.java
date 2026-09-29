package com.system_server.ai_demo.database.entity;

import lombok.Data;

/**
 * 会社マスタレコード（companies テーブル）のエンティティ。
 */
@Data
public class CompaniesEntity {

    private Integer companyId;
    private String companyName;
}
