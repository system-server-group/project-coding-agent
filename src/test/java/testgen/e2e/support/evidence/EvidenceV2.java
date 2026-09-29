package testgen.e2e.support.evidence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 検証部品による証跡方式（コマ送りフレーム＋steps.json）で実装した E2E テストクラスに付けるマーカー。
 *
 * <p>
 * 付与したクラスは {@link EvidenceV2GuardrailTest} のガードレール（素のアサーション・Locator の DOM 読取直呼びの禁止＝検証は
 * {@link E2eVerify} 部品経由のみ）の対象になる。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EvidenceV2 {
}
