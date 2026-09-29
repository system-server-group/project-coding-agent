package com.system_server.ai_demo.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnumsTest {

    @Test
    void status_codeAndLabel() {
        assertEquals("OPEN", Status.OPEN.getCode());
        assertEquals("オープン", Status.OPEN.getLabel());
        assertEquals("NEGOTIATING", Status.NEGOTIATING.getCode());
        assertEquals("交渉中", Status.NEGOTIATING.getLabel());
        assertEquals("CLOSED", Status.CLOSED.getCode());
        assertEquals("クローズ", Status.CLOSED.getLabel());
    }

    @Test
    void bpRecruitment_codeAndLabel() {
        assertEquals("REQUIRED", BpRecruitment.REQUIRED.getCode());
        assertEquals("必要", BpRecruitment.REQUIRED.getLabel());
        assertEquals("NOT_REQUIRED", BpRecruitment.NOT_REQUIRED.getCode());
        assertEquals("不要", BpRecruitment.NOT_REQUIRED.getLabel());
    }

    @Test
    void contractType_codeAndLabel() {
        assertEquals("CONTRACT", ContractType.CONTRACT.getCode());
        assertEquals("請負", ContractType.CONTRACT.getLabel());
        assertEquals("QUASI_MANDATE", ContractType.QUASI_MANDATE.getCode());
        assertEquals("準委任", ContractType.QUASI_MANDATE.getLabel());
        assertEquals("DISPATCH", ContractType.DISPATCH.getCode());
        assertEquals("派遣", ContractType.DISPATCH.getLabel());
    }

    @Test
    void role_codeIsDigitString_notName() {
        assertEquals("0", Role.GENERAL_USER.getCode());
        assertEquals("一般ユーザ", Role.GENERAL_USER.getLabel());
        assertEquals("9", Role.SYSTEM_ADMIN.getCode());
        assertEquals("システム管理者", Role.SYSTEM_ADMIN.getLabel());
    }

    @Test
    void editAttribute_codeIsDigitString_notName() {
        assertEquals("0", EditAttribute.NONE.getCode());
        assertEquals("何もしない", EditAttribute.NONE.getLabel());
        assertEquals("1", EditAttribute.ADD.getCode());
        assertEquals("ユーザの追加", EditAttribute.ADD.getLabel());
        assertEquals("2", EditAttribute.UPDATE.getCode());
        assertEquals("ユーザの更新", EditAttribute.UPDATE.getLabel());
        assertEquals("3", EditAttribute.DELETE.getCode());
        assertEquals("ユーザの削除", EditAttribute.DELETE.getLabel());
    }
}
