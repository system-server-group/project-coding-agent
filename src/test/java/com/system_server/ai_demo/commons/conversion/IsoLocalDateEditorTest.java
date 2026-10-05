package com.system_server.ai_demo.commons.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IsoLocalDateEditorTest {

    @Test
    void setAsText_parsesIsoDate() {
        IsoLocalDateEditor editor = new IsoLocalDateEditor();
        editor.setAsText("2026-06-01");
        assertEquals(LocalDate.of(2026, 6, 1), editor.getValue());
    }

    @Test
    void setAsText_trimsSurroundingWhitespace() {
        IsoLocalDateEditor editor = new IsoLocalDateEditor();
        editor.setAsText("  2026-06-01  ");
        assertEquals(LocalDate.of(2026, 6, 1), editor.getValue());
    }

    @Test
    void setAsText_treatsEmptyOrNullAsNull() {
        IsoLocalDateEditor editor = new IsoLocalDateEditor();
        editor.setAsText("");
        assertNull(editor.getValue());
        editor.setAsText(null);
        assertNull(editor.getValue());
    }

    @Test
    void setAsText_throwsIllegalArgument_whenNotIsoDate() {
        IsoLocalDateEditor editor = new IsoLocalDateEditor();
        assertThrows(IllegalArgumentException.class, () -> editor.setAsText("not-a-date"));
    }

    @Test
    void setAsText_throwsIllegalArgument_whenNonexistentDate() {
        IsoLocalDateEditor editor = new IsoLocalDateEditor();
        assertThrows(IllegalArgumentException.class, () -> editor.setAsText("2026-02-30"));
    }

    @Test
    void getAsText_printsIsoDate() {
        IsoLocalDateEditor editor = new IsoLocalDateEditor();
        editor.setValue(LocalDate.of(2026, 6, 1));
        assertEquals("2026-06-01", editor.getAsText());
    }

    @Test
    void getAsText_returnsEmptyForNull() {
        IsoLocalDateEditor editor = new IsoLocalDateEditor();
        editor.setValue(null);
        assertEquals("", editor.getAsText());
    }
}
