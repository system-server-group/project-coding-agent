package com.system_server.ai_demo.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * dev プロファイル限定の Flyway マイグレーション戦略。
 *
 * <p>
 * スキーマ策定中は {@code V1__init.sql} を直接編集するため、適用済みチェックサムとの不一致で 起動時の検証 ({@code validate})
 * が失敗する。本戦略は検証エラー時に dev DB を clean してから 再マイグレートし、現行マイグレーションにスキーマを揃え直す（自己修復）。
 *
 * <p>
 * clean は DB を全消去するため <strong>dev 専用</strong>（{@code @Profile("dev")}）。stg/prd では本 Bean を
 * 構成せず、{@code spring.flyway.clean-disabled} を既定 (true) のままとし、本番稼働後のスキーマ変更は {@code V2,V3…} の追加で行う。
 */
@Configuration
@Profile("dev")
public class FlywayDevConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayDevConfig.class);

    /**
     * 通常は {@code migrate} のみ行い、検証エラー時に限り {@code clean → migrate} で dev DB を作り直す。
     *
     * @return マイグレーション戦略
     */
    @Bean
    public FlywayMigrationStrategy cleanOnValidationErrorMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException ex) {
                log.warn("Flyway 検証エラーのため dev DB を clean して再マイグレートします（データは消去されます）: {}",
                        ex.getMessage());
                flyway.clean();
                flyway.migrate();
            }
        };
    }
}
