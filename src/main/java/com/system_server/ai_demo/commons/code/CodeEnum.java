package com.system_server.ai_demo.commons.code;

/**
 * DB に区分コードで永続化される列挙型が実装する共通インターフェース。
 *
 * <p>
 * 区分コード（DB 格納用）とラベル（画面・CSV 表示用の論理名）を返す。区分コードの取得・永続化・API 入出力・変換は、すべて {@link #getCode()} が返す値で行う。
 */
public interface CodeEnum {

    /**
     * DB 格納・API 入出力に用いる区分コード（型情報定義書の「定数物理名」の値）を返す。
     *
     * @return 区分コード
     */
    String getCode();

    /**
     * 画面・CSV 表示に用いるラベル（論理名）を返す。
     *
     * @return ラベル
     */
    String getLabel();
}
