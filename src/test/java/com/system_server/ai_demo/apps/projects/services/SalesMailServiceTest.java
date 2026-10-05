package com.system_server.ai_demo.apps.projects.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.system_server.ai_demo.apps.projects.models.SalesMailCaseInfo;
import com.system_server.ai_demo.apps.projects.models.SalesMailOperation;
import com.system_server.ai_demo.config.AiDemoProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SalesMailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final AiDemoProperties properties = new AiDemoProperties();
    private final SalesMailService service = new SalesMailService(mailSender, properties);

    private static SalesMailCaseInfo fullCaseInfo() {
        return new SalesMailCaseInfo("P0001", "件名X", "取引先X", "商流X", "概要1\n概要2", "工程X",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31), "Java", "3", "1", "必要", "詳細X",
                "東京", "80万", "準委任", "オープン", "備考1\n備考2",
                OffsetDateTime.of(2026, 6, 26, 14, 5, 6, 0, ZoneOffset.ofHours(9)), "営業部", "山田太郎");
    }

    private SimpleMailMessage capture() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void send_buildsExpectedSubjectAndBodyLayout_forRegister() {
        service.send(SalesMailOperation.REGISTER, fullCaseInfo(), "本文テキスト", "taro@example.com",
                true, "http://host/projects/P0001");

        SimpleMailMessage message = capture();
        assertEquals("【営業情報】(新規) 件名X", message.getSubject());
        assertArrayEquals(new String[] {"taro@example.com"}, message.getTo());
        assertEquals("taro@example.com", message.getFrom());

        String expected = String.join("\n",
                List.of("案件情報が新規登録されました", "http://host/projects/P0001 (添付ファイルの登録あり)", "", "本文テキスト",
                        "---------------------------------------------", "● 案件情報", "",
                        "【管理コード】  P0001", "【件名】    件名X", "【取引先】  取引先X", "【商流】    商流X",
                        "【案件概要】    概要1", "概要2", "【工程】    工程X", "【開始日】  2026年06月01日",
                        "【終了日】  2026年12月31日", "【スキル】  Java", "【人数】    3", "【残数】    1",
                        "【ＢＰ募集(要否)】  必要", "【ＢＰ募集(詳細)】  詳細X", "【作業場所】    東京", "【見込単価】    80万",
                        "【契約種別】    準委任", "【ステータス】  オープン", "【備考】    備考1", "備考2",
                        "【登録日時】    2026年06月26日 14時05分06秒", "【登録部署】    営業部", "【登録者】  山田太郎",
                        "---------------------------------------------"));
        assertEquals(expected, message.getText());
    }

    @Test
    void send_buildsUpdateSubjectAndHeader_forUpdate() {
        service.send(SalesMailOperation.UPDATE, fullCaseInfo(), "本文", "taro@example.com", false,
                "http://host/projects/P0001");

        SimpleMailMessage message = capture();
        assertEquals("【営業情報】(更新) 件名X", message.getSubject());
        assertTrue(message.getText().startsWith("案件情報が更新されました\n"));
    }

    @Test
    void send_omitsAttachmentNote_whenNoAttachment() {
        service.send(SalesMailOperation.REGISTER, fullCaseInfo(), "本文", "taro@example.com", false,
                "http://host/projects/P0001");

        String body = capture().getText();
        assertTrue(body.contains("http://host/projects/P0001 \n"));
        assertTrue(!body.contains("(添付ファイルの登録あり)"));
    }

    @Test
    void send_blanksNullValues() {
        SalesMailCaseInfo info = new SalesMailCaseInfo(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        service.send(SalesMailOperation.REGISTER, info, null, "taro@example.com", false, null);

        SimpleMailMessage message = capture();
        String body = message.getText();
        assertTrue(body.contains("【管理コード】  \n"));
        assertTrue(body.contains("【開始日】  \n"));
        assertTrue(body.contains("【登録日時】    \n"));
        assertEquals("【営業情報】(新規) ", message.getSubject());
    }

    @Test
    void send_usesFromAddress_whenRecipientsEmpty() {
        service.send(SalesMailOperation.REGISTER, fullCaseInfo(), "本文", "taro@example.com", false,
                "http://host/x");
        assertArrayEquals(new String[] {"taro@example.com"}, capture().getTo());
    }

    @Test
    void send_usesConfiguredRecipients_whenPresent() {
        properties.getMail().setRecipients(List.of("sales1@example.com", "sales2@example.com"));

        service.send(SalesMailOperation.REGISTER, fullCaseInfo(), "本文", "taro@example.com", false,
                "http://host/x");

        assertArrayEquals(new String[] {"sales1@example.com", "sales2@example.com"},
                capture().getTo());
    }

    @Test
    void send_doesNotThrow_whenMailSenderFails() {
        doThrow(new MailSendException("smtp down")).when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> service.send(SalesMailOperation.REGISTER, fullCaseInfo(), "本文",
                "taro@example.com", false, "http://host/x"));
    }
}
