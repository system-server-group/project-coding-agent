package com.system_server.ai_demo.apps.projects.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 添付削除APIの応答。削除件数を保持する。
 *
 * <p>
 * JSON 応答のフィールド名は【添付ファイルAPI定義書】の出力仕様に合わせて {@code result} とする（Java 上の名称は意味を表す {@code deletedCount}
 * のままとする）。
 *
 * @param deletedCount 削除件数
 */
public record AttachmentDeleteResponse(@JsonProperty("result") int deletedCount) {
}
