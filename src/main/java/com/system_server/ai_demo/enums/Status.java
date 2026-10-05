package com.system_server.ai_demo.enums;

import com.system_server.ai_demo.commons.code.CodeEnum;

/**
 * ステータス（案件情報のステータスを表す区分）。
 */
public enum Status implements CodeEnum {

    OPEN("OPEN", "オープン"), NEGOTIATING("NEGOTIATING", "交渉中"), CLOSED("CLOSED", "クローズ");

    private final String code;
    private final String label;

    Status(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
