package com.system_server.ai_demo;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * テスト用の PostgreSQL コンテナ。
     *
     * <ul>
     * <li>開発/本番と同じ postgres:18-alpine を使い、バージョン差異による挙動のズレを防ぐ</li>
     * <li>データ領域 (/var/lib/postgresql) を tmpfs に載せ、ディスクへ永続化せずメモリ上で動かす。 PG18
     * はメジャーバージョン別サブディレクトリ運用のため親ディレクトリごと tmpfs 化する</li>
     * <li>クラッシュ耐性は不要なため fsync 等を無効化し I/O オーバーヘッドを削減する</li>
     * <li>公開ポートは Testcontainers が空きポートへ自動割当するため、Compose の 5433 とは衝突しない</li>
     * </ul>
     *
     * <p>
     * {@code @ServiceConnection} によりコンテナの接続情報が {@code spring.datasource.*}
     * より優先されるため、テスト時は本コンテナへ自動接続される。
     */
    // コンテナは Spring (Testcontainers 連携) が起動/停止を管理する @Bean のため、本メソッドでは閉じない
    @SuppressWarnings("resource")
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
                .withTmpFs(Map.of("/var/lib/postgresql", "rw")).withCommand("postgres", "-c",
                        "fsync=off", "-c", "full_page_writes=off", "-c", "synchronous_commit=off");
    }
}
