package com.system_server.ai_demo.database.entity;

import lombok.Data;

/**
 * 部署マスタレコード（departments テーブル）のエンティティ。
 */
@Data
public class DepartmentsEntity {

    private Integer departmentId;
    private String departmentName;
}
