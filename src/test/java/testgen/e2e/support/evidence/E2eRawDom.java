package testgen.e2e.support.evidence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * キット外（Page Object・糊クラス等）のメソッドに、生 DOM アクセス（{@code evaluate}・
 * テキスト/値/属性の読取）を例外的に許可するマーカー。
 *
 * <p>
 * 検証部品を通らない DOM 読取は「見えない検証」（読取値の machineEquals 転記等）を生み、証跡の
 * 可視性を失わせるため、ArchUnit ガードレール（{@link EvidenceV2GuardrailTest}）が本注釈の無い
 * 生 DOM アクセスをビルド失敗にする。テストクラス（{@link EvidenceV2} 付与）では本注釈による
 * 例外も認めない（テストは部品呼び出しだけを書く）。
 *
 * <p>
 * 付与できるのは、仕様書で<b>【機械検証】と宣言された期待結果の実測</b>、または仕様書に明記された
 * <b>バイパス手順・合成操作</b>に対応するメソッドに限る（運用条件は testgen-exec SKILL.md）。
 * {@link #value()} に対応先（仕様書の期待結果・手順）を記し、exec の完了報告で付与一覧を報告する。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface E2eRawDom {

    /**
     * 仕様書のどの【機械検証】期待結果・バイパス手順に対応するか。
     *
     * @return 対応先の説明
     */
    String value();
}
