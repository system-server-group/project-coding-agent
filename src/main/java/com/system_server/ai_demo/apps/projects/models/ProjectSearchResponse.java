package com.system_server.ai_demo.apps.projects.models;

import java.util.List;

/**
 * 案件検索APIの応答。条件に合致した案件情報の一覧を保持する。
 *
 * @param projects 案件情報の一覧（0件の場合は空）
 */
public record ProjectSearchResponse(List<ProjectListDto> projects) {
}
