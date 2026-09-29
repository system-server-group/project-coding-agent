package com.system_server.ai_demo.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * アプリ固有プロパティ (prefix: ai-demo)。
 *
 * <p>
 * spring-boot-configuration-processor がビルド時に META-INF/spring-configuration-metadata.json を生成し、IDE
 * 補完/検証に使われる。
 */
@Data
@ConfigurationProperties(prefix = "ai-demo")
public class AiDemoProperties {

    private Mail mail = new Mail();

    @Data
    public static class Mail {
        /** 営業メールに埋め込むベース URL (末尾スラッシュなし)。各プロファイルで上書き。 */
        private String baseUrl;

        /**
         * 営業情報メールの宛先 (本番系の営業部メールリスト)。シークレットのため環境変数から注入し、ソースには保持しない。 空の場合は送信元と同一アドレスを宛先とする
         * (開発系・検証系の挙動)。
         */
        private List<String> recipients = new ArrayList<>();
    }
}
