package com.system_server.ai_demo.commons.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.system_server.ai_demo.enums.Role;
import com.system_server.ai_demo.enums.Status;
import org.junit.jupiter.api.Test;

class CodeEnumsTest {

    @Test
    void fromCode_resolvesByHeldCode_notName() {
        assertEquals(Status.OPEN, CodeEnums.fromCode(Status.class, "OPEN"));
        assertEquals(Role.GENERAL_USER, CodeEnums.fromCode(Role.class, "0"));
        assertEquals(Role.SYSTEM_ADMIN, CodeEnums.fromCode(Role.class, "9"));
    }

    @Test
    void fromCode_throws_whenUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> CodeEnums.fromCode(Role.class, "X"));
        // 定数名（SYSTEM_ADMIN）は区分コードではないため解決されない
        assertThrows(IllegalArgumentException.class,
                () -> CodeEnums.fromCode(Role.class, "SYSTEM_ADMIN"));
    }

    @Test
    void fromCodeOrNull_returnsNull_whenNull() {
        assertNull(CodeEnums.fromCodeOrNull(Status.class, null));
    }

    @Test
    void fromCodeOrNull_resolves_whenPresent() {
        assertEquals(Status.CLOSED, CodeEnums.fromCodeOrNull(Status.class, "CLOSED"));
    }

    @Test
    void fromCodeOrNull_throws_whenUnknownNonNull() {
        assertThrows(IllegalArgumentException.class,
                () -> CodeEnums.fromCodeOrNull(Status.class, "X"));
    }
}
