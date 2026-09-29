package com.system_server.ai_demo.apps.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 共通レイアウト（共通ヘッダー）を使用する画面コントローラーに付与するマーカー。
 *
 * <p>
 * 本注釈が付いたコントローラーの画面応答に対してのみ {@code CommonLayoutInterceptor} がヘッダー表示用のモデル属性
 * （ユーザ名・部署名・管理者判定）を供給する。REST API コントローラーには付与しない。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommonLayout {
}
