package testgen.e2e.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * E2E テストクラスが対象とする機能（機能フォルダ名）の宣言。
 *
 * <p>
 * {@link E2eBaseTest#featureName()} の正本であり、証跡の出力先（{@code <実施単位>/<機能名>/エビデンス/}）と
 * 機能絞り込み（{@code -Pe2e.features}）の判定に使う。判定はテストインスタンス生成・Spring コンテキスト
 * 起動より前（{@link E2eFeatureSelection}）に行われるため、選択外の機能はアプリ・データベースコンテナを
 * 一切起動せずにスキップされる（メソッドのオーバーライドでは起動前に機能名を読めないため、注釈で宣言する）。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface E2eFeature {

    /**
     * 機能名（テスト実施単位配下の機能フォルダ名に一致させる）。
     *
     * @return 機能名
     */
    String value();
}
