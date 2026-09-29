package com.system_server.ai_demo.commons.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SpaceTrimEditorTest {

    @Test
    void setAsText_removesSurroundingHalfAndFullWidthSpaces() {
        SpaceTrimEditor editor = new SpaceTrimEditor();
        editor.setAsText(" 　A 　B 　");
        assertEquals("A 　B", editor.getValue());
    }

    @Test
    void setAsText_keepsInnerSpacesOnly() {
        SpaceTrimEditor editor = new SpaceTrimEditor();
        editor.setAsText("　　 A　B C 　　");
        assertEquals("A　B C", editor.getValue());
    }

    @Test
    void setAsText_turnsSpaceOnlyIntoEmpty() {
        SpaceTrimEditor editor = new SpaceTrimEditor();
        editor.setAsText(" 　 　");
        assertEquals("", editor.getValue());
    }

    @Test
    void setAsText_keepsEmptyAsEmpty() {
        SpaceTrimEditor editor = new SpaceTrimEditor();
        editor.setAsText("");
        assertEquals("", editor.getValue());
    }

    @Test
    void setAsText_treatsNullAsNull() {
        SpaceTrimEditor editor = new SpaceTrimEditor();
        editor.setAsText(null);
        assertNull(editor.getValue());
    }

    @Test
    void setAsText_doesNotRemoveTabsAndNewlines() {
        SpaceTrimEditor editor = new SpaceTrimEditor();
        editor.setAsText("1行目\n2行目\n");
        assertEquals("1行目\n2行目\n", editor.getValue());
        editor.setAsText("\tA\t");
        assertEquals("\tA\t", editor.getValue());
    }

    @Test
    void setAsText_removesSpacesButStopsAtNewline() {
        SpaceTrimEditor editor = new SpaceTrimEditor();
        editor.setAsText("　A\n 　");
        assertEquals("A\n", editor.getValue());
    }
}
