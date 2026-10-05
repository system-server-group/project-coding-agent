package com.system_server.ai_demo.commons.code;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.system_server.ai_demo.enums.Role;
import com.system_server.ai_demo.enums.Status;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class CodeEnumSerializerTest {

    private static JsonMapper mapper() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(CodeEnum.class, new CodeEnumSerializer());
        return JsonMapper.builder().addModule(module).build();
    }

    @Test
    void serializesAsCodeAndLabel() {
        JsonMapper mapper = mapper();
        assertEquals("{\"code\":\"OPEN\",\"label\":\"オープン\"}",
                mapper.writeValueAsString(Status.OPEN));
        // 区分コードが数字文字列でも保持コードで出力される
        assertEquals("{\"code\":\"0\",\"label\":\"一般ユーザ\"}",
                mapper.writeValueAsString(Role.GENERAL_USER));
    }
}
