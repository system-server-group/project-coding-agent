package com.system_server.ai_demo.commons.validation;

import java.util.Map;

/**
 * 入力値チェック種別の優先順位（入力値チェック一覧の番号に対応。小さいほど優先）を一元管理する共通部品。
 *
 * <p>
 * 同一項目に複数違反がある場合に「一覧で最小番号のメッセージ1件」を採用するための順位付けを、API（{@code errors[]}）と画面
 * フォーム（{@code BindingResult}）の双方で共有する。順位はチェック種別（注釈名）で引く。機能固有の項目名・項目順は持たない。
 */
public final class CheckRank {

    /** 既定順位（未知のチェック種別。最も後回し）。 */
    public static final int DEFAULT = Integer.MAX_VALUE;

    private static final Map<String, Integer> RANK =
            Map.of("NotBlank", 1, "NotNull", 1, "NotEmpty", 1, "Size", 3, "AllowedCharacters", 5,
                    "Min", 7, "Max", 8, "DateInBounds", 9, "DateRange", 10);

    private CheckRank() {}

    /**
     * チェック種別（注釈名）の優先順位を返す。
     *
     * @param checkName チェック種別の注釈名（{@code null} 可）
     * @return 優先順位（小さいほど優先。未知・{@code null} は {@link #DEFAULT}）
     */
    public static int rankOf(String checkName) {
        if (checkName == null) {
            return DEFAULT;
        }
        return RANK.getOrDefault(checkName, DEFAULT);
    }
}
