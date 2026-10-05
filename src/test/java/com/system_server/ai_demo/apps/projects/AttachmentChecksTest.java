package com.system_server.ai_demo.apps.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class AttachmentChecksTest {

    private static final String MSG_TOO_LARGE = "添付ファイルの容量が大きすぎます(6MBまで)";
    private static final String MSG_NAME_TOO_LONG = "ファイル名は200字までです。";

    private static MultipartFile file(String name, int size) {
        return new MockMultipartFile("attachment", name, "application/octet-stream",
                new byte[size]);
    }

    @Test
    void validate_returnsEmpty_whenNull() {
        // 未選択（null）は対象外
        assertTrue(AttachmentChecks.validate(null).isEmpty());
    }

    @Test
    void validate_returnsEmpty_whenEmpty() {
        // 未選択（空ファイル）は対象外
        MultipartFile empty =
                new MockMultipartFile("attachment", "x.txt", "text/plain", new byte[0]);
        assertTrue(AttachmentChecks.validate(empty).isEmpty());
    }

    @Test
    void validate_acceptsSizeAtLimit() {
        // ちょうど 6MB は許容（境界）
        MultipartFile atLimit = file("a.bin", (int) AttachmentChecks.MAX_SIZE);
        assertTrue(AttachmentChecks.validate(atLimit).isEmpty());
    }

    @Test
    void validate_rejectsSizeOverLimit() {
        // 6MB を 1 バイト超えると違反（境界）
        MultipartFile tooLarge = file("a.bin", (int) AttachmentChecks.MAX_SIZE + 1);
        assertEquals(List.of(MSG_TOO_LARGE), AttachmentChecks.validate(tooLarge));
    }

    @Test
    void validate_acceptsNameAtMaxLength() {
        // ファイル名 200 文字は許容（境界）
        MultipartFile atMax = file("あ".repeat(AttachmentChecks.MAX_NAME_LENGTH), 1);
        assertTrue(AttachmentChecks.validate(atMax).isEmpty());
    }

    @Test
    void validate_rejectsNameOverMaxLength() {
        // ファイル名 201 文字は違反（境界）
        MultipartFile tooLong = file("あ".repeat(AttachmentChecks.MAX_NAME_LENGTH + 1), 1);
        assertEquals(List.of(MSG_NAME_TOO_LONG), AttachmentChecks.validate(tooLong));
    }

    @Test
    void validate_collectsBothViolations() {
        // 容量・ファイル名の両違反を収集する（容量→ファイル名の順）
        MultipartFile bad = file("あ".repeat(AttachmentChecks.MAX_NAME_LENGTH + 1),
                (int) AttachmentChecks.MAX_SIZE + 1);
        assertEquals(List.of(MSG_TOO_LARGE, MSG_NAME_TOO_LONG), AttachmentChecks.validate(bad));
    }
}
