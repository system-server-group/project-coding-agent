package testgen.e2e.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 機能絞り込み（{@code -Pe2e.features}）の実行条件。選択外の機能のテストクラスを、テストインスタンス
 * 生成・Spring コンテキスト起動より<b>前</b>に無効化する。
 *
 * <p>
 * かつてはコンテキスト起動後の assumption でスキップしていたため、選択外の機能でもアプリ＋データベース
 * コンテナの起動だけは毎回行われ、その起動が環境要因（コンテナ接続の一時失敗等）で失敗すると、実行対象で
 * ない機能の全ケースが失敗として計上されていた。本条件は JUnit がコンテナ・テストの実行可否を決める段階
 * （Spring 拡張がコンテキストを作るより前）で評価されるため、選択外の機能は何も初期化せずスキップになる。
 *
 * <p>
 * 機能名は {@link E2eFeature} 注釈から読む。注釈の無いクラス（基底・糊クラス等）は本条件では判定せず
 * 有効のままとする（具象テストクラスの注釈漏れは {@link E2eBaseTest#featureName()} が実行時に検出する）。
 */
public final class E2eFeatureSelection implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        E2eFeature feature = context.getTestClass()
                .map(type -> type.getAnnotation(E2eFeature.class)).orElse(null);
        if (feature == null) {
            return ConditionEvaluationResult.enabled("機能注釈（@E2eFeature）なし: 絞り込み判定の対象外");
        }
        if (E2eSpecLayout.isSelected(feature.value())) {
            return ConditionEvaluationResult.enabled("実行対象の機能: " + feature.value());
        }
        return ConditionEvaluationResult.disabled("選択外の機能のためスキップします: " + feature.value());
    }
}
