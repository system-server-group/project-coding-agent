package com.system_server.ai_demo.apps.projects.models;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 案件情報更新画面で入力される更新内容。案件情報の入力項目（{@link ProjectRegisterForm} と共通）に加え、対象の管理コードと
 * 楽観排他の照合に用いる読込時更新日時を保持する。
 *
 * <p>
 * 共通の入力項目・入力値チェック（件名必須・各最大文字数・使用文字・日付形式・日付下限）は親クラスから継承する。追加の添付ファイルは 親の {@code attachment}
 * を用いる（1回の更新で1件）。
 */
@Getter
@Setter
public class ProjectUpdateForm extends ProjectRegisterForm {

    /** 管理コード（更新対象。パス変数と一致）。 */
    private Integer managementCode;

    /** 読込時更新日時（画面読込時点の更新日時。楽観排他の照合に用いる）。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime loadedUpdatedAt;
}
