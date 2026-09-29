package testgen.e2e.support.evidence;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

/**
 * E2E テスト資産（{@code testgen.e2e} 配下のキット外コード）のガードレール。
 *
 * <p>
 * 検証部品（{@link E2eVerify}）経由を ArchUnit で強制し、映り・記録の品質を規律でなく構造で担保する。
 * 通常の {@code test} タスクで実行される（ブラウザ・Docker 不要）。
 *
 * <p>
 * 生 DOM アクセス（{@code evaluate}・テキスト/値/属性の読取）をキット外で行うと、検証部品を通らない
 * 「見えない検証」（読取値の machineEquals 転記等）が可能になり証跡の可視性が失われる。このため
 * テストクラス（{@link EvidenceV2} 付与）では読取を全面禁止し、Page Object・糊クラス等では
 * {@link E2eRawDom} を付けたメソッドに限り許可する（運用条件は testgen-exec SKILL.md——仕様書で
 * 【機械検証】と宣言された期待結果・明記されたバイパス手順に対応する場合のみ）。操作に付随する
 * 読取（ドラッグ座標の {@code boundingBox}・冪等判定の {@code count} 等）は Page Object では許容する。
 */
// 解析対象は E2E パッケージ全体。キット・糊クラス・機能対応物（シーダー・Page Object・テストクラス）は
// いずれも testgen.e2e 配下に置く規約のため、転写先を問わず固定でよい。
@AnalyzeClasses(packages = "testgen.e2e")
class EvidenceV2GuardrailTest {

    /** Playwright の生 DOM アクセス（スクリプト実行・テキスト/値/属性の読取）のメソッド名。 */
    private static final Set<String> RAW_DOM_METHODS = Set.of("evaluate", "evaluateHandle",
            "evaluateAll", "evalOnSelector", "evalOnSelectorAll", "textContent", "innerText",
            "innerHTML", "inputValue", "getAttribute", "allTextContents", "allInnerTexts",
            "content");

    /** テストクラスで追加禁止する読取メソッド名（操作付随の読取も部品・Page Object へ寄せる）。 */
    private static final Set<String> TEST_ONLY_FORBIDDEN_READS = Set.of("count", "boundingBox",
            "isVisible", "isHidden", "isChecked", "isDisabled", "isEnabled", "isEditable", "all");

    /** 読取を禁止する Playwright のオーナー型。 */
    private static final Set<String> PLAYWRIGHT_OWNERS = Set.of("com.microsoft.playwright.Locator",
            "com.microsoft.playwright.Page", "com.microsoft.playwright.Frame",
            "com.microsoft.playwright.ElementHandle");

    @ArchTest
    static final ArchRule NO_RAW_ASSERTIONS = noClasses().that()
            .resideInAPackage("testgen.e2e..").and()
            .resideOutsideOfPackage("testgen.e2e.support..").should().dependOnClassesThat()
            .belongToAnyOf(com.microsoft.playwright.assertions.PlaywrightAssertions.class,
                    org.junit.jupiter.api.Assertions.class)
            .because("証跡の検証は E2eVerify 部品経由のみとする（フレーム撮影・ハイライト・"
                    + "見切れ検査・steps.json 記録を構造で担保するため）")
            // ハーネス転写直後（テストクラス作成前）は対象 0 件のため、空集合を許容する。
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule NO_RAW_DOM_READS_IN_TESTS = noClasses().that()
            .areAnnotatedWith(EvidenceV2.class).should()
            .callMethodWhere(new DescribedPredicate<JavaMethodCall>(
                    "Playwright の DOM 読取・スクリプト実行を直接呼ぶ") {
                @Override
                public boolean test(JavaMethodCall call) {
                    if (!PLAYWRIGHT_OWNERS.contains(call.getTargetOwner().getFullName())) {
                        return false;
                    }
                    String name = call.getTarget().getName();
                    return RAW_DOM_METHODS.contains(name)
                            || TEST_ONLY_FORBIDDEN_READS.contains(name);
                }
            })
            .because("テストクラスは意図（部品呼び出し）だけを書く。画面に出ない DOM 読取の検証は "
                    + "E2eVerify の機械検証・代理証跡部品を使い、証跡（steps.json）に実測値を残す"
                    + "（テストクラスでは @E2eRawDom による例外も認めない）")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule NO_UNDECLARED_RAW_DOM_OUTSIDE_KIT = noClasses().that()
            .resideInAPackage("testgen.e2e..").and()
            .resideOutsideOfPackage("testgen.e2e.support..").should()
            .callMethodWhere(new DescribedPredicate<JavaMethodCall>(
                    "@E2eRawDom の無いメソッドから Playwright の DOM 読取・スクリプト実行を呼ぶ") {
                @Override
                public boolean test(JavaMethodCall call) {
                    if (!PLAYWRIGHT_OWNERS.contains(call.getTargetOwner().getFullName())
                            || !RAW_DOM_METHODS.contains(call.getTarget().getName())) {
                        return false;
                    }
                    return !originDeclaresRawDom(call);
                }
            })
            .because("Page Object・糊クラスはロケータの提供と操作だけを担い、DOM の値を読み取って"
                    + "検証材料にしない。生 DOM アクセスは仕様書の【機械検証】・バイパス手順に対応する "
                    + "@E2eRawDom 付きメソッドに限る（運用条件は testgen-exec SKILL.md）")
            .allowEmptyShould(true);

    /** 呼出元メソッド（ラムダの場合は外側のメソッド）が {@link E2eRawDom} を宣言しているか。 */
    private static boolean originDeclaresRawDom(JavaMethodCall call) {
        if (call.getOrigin().isAnnotatedWith(E2eRawDom.class)) {
            return true;
        }
        // ラムダ内の呼び出しは合成メソッド lambda$<外側メソッド名>$n が呼出元になり注釈が見えない
        // ため、名前から外側メソッドを引いて判定する。
        String origin = call.getOrigin().getName();
        if (!origin.startsWith("lambda$")) {
            return false;
        }
        String enclosing = origin.substring("lambda$".length());
        int cut = enclosing.indexOf('$');
        if (cut > 0) {
            enclosing = enclosing.substring(0, cut);
        }
        JavaClass owner = call.getOriginOwner();
        String methodName = enclosing;
        return owner.getMethods().stream().filter(method -> method.getName().equals(methodName))
                .anyMatch(method -> method.isAnnotatedWith(E2eRawDom.class));
    }
}
