package testgen.e2e.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * アプリケーションが送信するメールの受信検証に用いる、テスト専用の偽 SMTP サーバ（MailHog）を供給する {@link TestConfiguration}。
 *
 * <p>
 * E2E で「メールが届く／届かない」を人が受け入れ確認できるよう、実際の送信を握りつぶすダミー設定ではなく、受信して 内容（件名・宛先・本文）を検証できる偽 SMTP
 * を立てる。開発環境（{@code docker-compose.yml}）と同じ MailHog イメージを Testcontainers で起動し、起動・停止は
 * Spring（Testcontainers 連携）が管理する。
 *
 * <p>
 * 公開ポートは Testcontainers が空きポートへ自動割当するため、Compose の 1025/8025 とは衝突しない。割当後の接続先は
 * {@link DynamicPropertyRegistrar} で {@code spring.mail.*} へ反映し、{@code application-e2e.yml} の
 * ダミー設定を上書きする。本構成を {@code @Import} したテストクラスの ApplicationContext でのみ起動するため、 他機能の E2E には影響しない。
 *
 * <p>
 * 送信元・宛先はログインユーザのメールアドレス（開発用ダミー）であり、秘匿情報は扱わない。
 *
 * @see E2eMailbox
 */
@TestConfiguration
public class E2eMailServer {

    /** 偽 SMTP のイメージ（開発環境の {@code docker-compose.yml} と揃える）。 */
    private static final DockerImageName IMAGE = DockerImageName.parse("mailhog/mailhog:latest");

    /** MailHog のコンテナ内 SMTP ポート。 */
    private static final int SMTP_PORT = 1025;

    /** MailHog のコンテナ内 HTTP API ポート（受信箱の参照・消去に用いる）。 */
    private static final int HTTP_PORT = 8025;

    /**
     * 偽 SMTP サーバ（MailHog）のコンテナを供給する。HTTP API が応答するまで起動を待つ。
     *
     * @return MailHog コンテナ
     */
    // コンテナは Spring (Testcontainers 連携) が起動/停止を管理する @Bean のため、本メソッドでは閉じない
    @SuppressWarnings("resource")
    @Bean
    GenericContainer<?> mailhogContainer() {
        GenericContainer<?> container = new GenericContainer<>(IMAGE);
        container.withExposedPorts(SMTP_PORT, HTTP_PORT)
                .waitingFor(Wait.forHttp("/api/v2/messages").forPort(HTTP_PORT));
        return container;
    }

    /**
     * 起動したコンテナの接続先を {@code spring.mail.*} へ反映する。ポートは自動割当のため、 {@code application-e2e.yml}
     * には固定値を書けず、起動後に動的登録する。
     *
     * @param mailhogContainer MailHog コンテナ
     * @return 接続先を登録する {@link DynamicPropertyRegistrar}
     */
    @Bean
    DynamicPropertyRegistrar mailhogConnectionRegistrar(
            @Qualifier("mailhogContainer") GenericContainer<?> mailhogContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailhogContainer::getHost);
            registry.add("spring.mail.port", () -> mailhogContainer.getMappedPort(SMTP_PORT));
        };
    }

    /**
     * 受信箱を操作するクライアントを供給する。
     *
     * @param mailhogContainer MailHog コンテナ
     * @return 受信箱クライアント
     */
    @Bean
    E2eMailbox e2eMailbox(@Qualifier("mailhogContainer") GenericContainer<?> mailhogContainer) {
        return new E2eMailbox(mailhogContainer.getHost(),
                mailhogContainer.getMappedPort(HTTP_PORT));
    }
}
