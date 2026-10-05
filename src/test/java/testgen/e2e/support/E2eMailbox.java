package testgen.e2e.support;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.StringJoiner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 偽 SMTP（MailHog）の受信箱を HTTP API 越しに操作するテスト専用クライアント。
 *
 * <p>
 * MailHog はコンテナで動く別プロセスのため、受信メールは Java API では取れず HTTP API から取得する。 API が返す件名・本文は MIME
 * エンコード（{@code =?UTF-8?B?...?=}／quoted-printable 等）のままであり、そのまま照合すると
 * 日本語が一致しない。このため生メッセージ（{@code Raw.Data}＝RFC822 全文）を {@link MimeMessage} に読み込み、デコードは jakarta.mail
 * に委ねる。
 *
 * <p>
 * 受信は SMTP 経由で非同期に届くため、件数の確認には {@link #waitForIncomingEmail(long, int)} でポーリング待機する。
 *
 * <p>
 * 送信元・宛先はログインユーザのメールアドレス（開発用ダミー）であり、秘匿情報は扱わない。
 *
 * @see E2eMailServer
 */
public class E2eMailbox {

    /** API 呼び出しのタイムアウト。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** 受信待ちのポーリング間隔（ms）。 */
    private static final long POLL_INTERVAL_MS = 100;

    private final String apiBaseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * MailHog の HTTP API を指すクライアントを生成する。
     *
     * @param host MailHog コンテナのホスト
     * @param httpPort HTTP API のポート（ホスト側へ割り当てられたポート）
     */
    E2eMailbox(String host, int httpPort) {
        this.apiBaseUrl = "http://" + host + ":" + httpPort;
    }

    /**
     * 受信箱の Web UI（MailHog）の URL を返す。
     *
     * <p>
     * 送信メールは画面に現れない検証のため、受け入れ用の証跡では本 UI を実ブラウザで開いて受信内容を映してから 検証する（動画の目視性）。UI の構造は Page
     * Object（{@code MailHogPage}）に閉じ込める。
     *
     * @return 受信箱 Web UI の URL
     */
    public String webUiUrl() {
        return apiBaseUrl + "/";
    }

    /** 受信済みメールを全て消去する（各ケースを空の受信箱から開始するため）。 */
    public void reset() {
        call(HttpRequest.newBuilder(URI.create(apiBaseUrl + "/api/v1/messages")).DELETE());
    }

    /**
     * 指定件数のメールが受信されるまで待つ。
     *
     * @param timeoutMs 待機の上限（ms）
     * @param count 待機する受信件数
     * @return 期限内に指定件数へ達したとき {@code true}
     */
    public boolean waitForIncomingEmail(long timeoutMs, int count) {
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMs).toNanos();
        while (true) {
            if (getReceivedMessages().length >= count) {
                return true;
            }
            if (System.nanoTime() - deadline >= 0) {
                return false;
            }
            sleep();
        }
    }

    /**
     * 受信済みメールを受信順（古い順）で取り出す。
     *
     * @return 受信済みメール
     */
    public MimeMessage[] getReceivedMessages() {
        String response =
                call(HttpRequest.newBuilder(URI.create(apiBaseUrl + "/api/v2/messages")).GET());
        JsonNode items = objectMapper.readTree(response).path("items");
        List<MimeMessage> messages = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            messages.add(parse(items.get(i).path("Raw").path("Data").asString()));
        }
        // MailHog は新しい順に返すため、受信順（古い順）へ揃える。
        Collections.reverse(messages);
        return messages.toArray(new MimeMessage[0]);
    }

    /**
     * メール本文（テキスト）を取り出す。Content-Transfer-Encoding をデコードした本文を返す （{@code getContent()} を用いる。QP/Base64
     * のまま照合しないため）。
     *
     * @param message 受信メッセージ
     * @return デコード済みの本文テキスト
     */
    public static String bodyOf(MimeMessage message) {
        try {
            Object content = message.getContent();
            return content == null ? "" : content.toString();
        } catch (IOException | MessagingException e) {
            throw new IllegalStateException("受信メールの本文取得に失敗しました", e);
        }
    }

    /**
     * 件名を取り出す。
     *
     * @param message 受信メッセージ
     * @return 件名
     */
    public static String subjectOf(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            throw new IllegalStateException("受信メールの件名取得に失敗しました", e);
        }
    }

    /**
     * 宛先（To）を「カンマ区切り」の文字列で取り出す。
     *
     * @param message 受信メッセージ
     * @return 宛先アドレス（カンマ区切り）
     */
    public static String recipientsOf(MimeMessage message) {
        try {
            Address[] recipients = message.getRecipients(Message.RecipientType.TO);
            if (recipients == null) {
                return "";
            }
            StringJoiner joiner = new StringJoiner(", ");
            for (Address recipient : recipients) {
                joiner.add(recipient.toString());
            }
            return joiner.toString();
        } catch (MessagingException e) {
            throw new IllegalStateException("受信メールの宛先取得に失敗しました", e);
        }
    }

    /** 生メッセージ（RFC822 全文）を {@link MimeMessage} へ読み込む。デコードは jakarta.mail に委ねる。 */
    private static MimeMessage parse(String raw) {
        try {
            return new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (MessagingException e) {
            throw new IllegalStateException("受信メールの解析に失敗しました", e);
        }
    }

    /** MailHog の HTTP API を呼び、応答本文を返す。2xx 以外は失敗として扱う。 */
    private String call(HttpRequest.Builder builder) {
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT).build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("偽SMTPのAPI呼び出しが失敗しました: " + request.uri()
                        + " status=" + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException("偽SMTPのAPI呼び出しに失敗しました: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("偽SMTPのAPI呼び出しが中断されました: " + request.uri(), e);
        }
    }

    /** ポーリング間隔だけ待つ。 */
    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("偽SMTPの受信待ちが中断されました", e);
        }
    }
}
