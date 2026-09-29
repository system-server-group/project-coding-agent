package com.system_server.ai_demo.apps.projects.models;

/**
 * 営業情報メールの操作区分（登録／更新）。件名の接頭辞とヘッダー文を保持する。
 *
 * <p>
 * サービス定義書では「string（登録／更新）」として表現される 2 値の閉じた集合を、型安全に列挙型で表す。
 */
public enum SalesMailOperation {

    /** 登録（新規）。 */
    REGISTER("(新規)", "案件情報が新規登録されました"),

    /** 更新。 */
    UPDATE("(更新)", "案件情報が更新されました");

    private final String subjectMark;
    private final String headerText;

    SalesMailOperation(String subjectMark, String headerText) {
        this.subjectMark = subjectMark;
        this.headerText = headerText;
    }

    /**
     * 件名の接頭辞（登録「(新規)」／更新「(更新)」）を返す。
     *
     * @return 件名の接頭辞
     */
    public String getSubjectMark() {
        return subjectMark;
    }

    /**
     * 本文先頭のヘッダー文を返す。
     *
     * @return ヘッダー文
     */
    public String getHeaderText() {
        return headerText;
    }
}
