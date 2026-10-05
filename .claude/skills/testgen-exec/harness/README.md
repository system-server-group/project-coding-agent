# E2E ハーネスキット（転写用）

E2E 証跡ハーネスの正本。キットは**中立パッケージ `testgen.e2e.support`** で書かれており、
プロジェクトを問わずそのまま転写できる。プロジェクトに E2E ハーネスが無い場合、testgen-exec が

- Java 一式を `src/test/java/testgen/e2e/support/`（`evidence/` サブパッケージ含む）へ**そのまま**、
- Gradle 設定 `testgen.e2e.gradle.kts` をプロジェクトルート直下へ

転写し、`build.gradle.kts` に `apply(from = "testgen.e2e.gradle.kts")` を 1 行追記して使う。
あわせて **糊クラス（下記）を生成**し、VCS 設定を testgen-shared「実施単位・配置規範」の
VCS 整備の定めに従い整備する（無ければ追記）。

## 原則

- **キットは特定プロジェクトに依存しない**。プロジェクトの機能・テーブル・画面に対応するコードも、
  プロジェクトのパッケージ・クラスへの参照も含まない。保守は testgen-shared「保守・可搬性の規律」に従う。
- キット本体は転写時に**一切書き換えない**（パッケージ宣言も含めそのまま）。プロジェクト適合は
  `// [PROJECT]` マーカーの箇所だけ:
  1. `testgen.e2e.gradle.kts` のビューア生成スクリプトのパス: スキルの設置場所に合わせる
- アプリ固有の接合はすべて**糊クラス**が担う（キットへ還元する変更にアプリ依存を持ち込まない）。
- ハーネス専用の依存（Playwright・ArchUnit・Testcontainers・JUnit Platform launcher）は
  `testgen.e2e.gradle.kts` が宣言する。前提: java プラグイン適用済みの Spring Boot プロジェクト
  （バージョン省略の依存は dependency-management で解決する）・JUnit 5・Spring Boot Test・logback。
- 転写物とキットの差分は乖離であり、キット側を正として追随する。差分の原因がキット側の更新で
  ある場合は、キットを正として**再転写**し、部品シグネチャ変更の影響（コンパイル不能となる既存
  テスト・置換が必要な部品呼び出し）を一覧にして報告し、合意の上でテスト側を改修する。
- E2E テストコード（糊クラス・シーダー・Page Object・テストクラス）は **`testgen.e2e` 配下**に置く
  （`EvidenceV2GuardrailTest` の ArchUnit 解析対象が `testgen.e2e` 固定のため）。
- テストクラスは対象機能を **`@E2eFeature("<機能フォルダ名>")`** で宣言する。機能絞り込み
  （`-Pe2e.features`）は `E2eFeatureSelection`（ExecutionCondition）がテストインスタンス生成・
  Spring コンテキスト起動より前にこの注釈で判定するため、選択外の機能はアプリ・DBコンテナを
  起動せずにスキップされる（`featureName()` は注釈を読む final メソッドでありオーバーライド不可）。

## 糊クラス（exec がプロジェクトごとに生成する）

キットとアプリの接合を一手に引き受ける抽象クラス。`E2eBaseTest` を継承し、テストクラスは
糊クラスを継承する。E2E テストはアプリのパッケージ外にあるため、`@SpringBootTest` の
`classes` を**必ず明示**する。

```java
package testgen.e2e;

@SpringBootTest(classes = <アプリ起動クラス>.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(<プロジェクトの Testcontainers 設定クラス>.class)
@ActiveProfiles("e2e")
public abstract class ProjectE2eTest extends E2eBaseTest {

    @Override
    protected String appRootPackage() {
        return "<アプリのルートパッケージ>"; // 証跡ログの取捨に使う
    }

    // 必要ならオーバーレイのレイアウトセレクタを上書きする（既定 .app-shell / .center-screen。
    // 該当要素が無ければ何もしないため、上書きは任意）。
    // @Override protected String overlayShellSelector() { return "..."; }
    // @Override protected String overlayCenterSelector() { return "..."; }
}
```

## キットに含まれないもの（exec がプロジェクトごとに作る成果物）

- **糊クラス**: 上記 `ProjectE2eTest`（アプリ起動・Testcontainers 設定・アプリルートパッケージの供給）。
- **シーダー**: `E2eBaseTest.resetBaseline()` の実装。`TRUNCATE ... RESTART IDENTITY CASCADE` →
  ベースライン正本 CSV の投入 → 自動採番列を持つテーブルへ `E2eSeedSupport.moveSequencePastSeedBand`。
  CSV の読取・検証は `E2eSeedSupport`（`parseCsv`＝RFC4180・`requireHeader`＝列名照合・
  `parseSeedId`＝一意性/帯域の機械強制）を使い、列はテーブル定義（DDL）に一致させる。
  自動採番の列も CSV に明示し `OVERRIDING SYSTEM VALUE` で投入する。
  シード帯域の上限は既定値 `E2eSeedSupport.SEED_ID_MAX`（=1000。UI 採番はその次から）。プロジェクトの
  要求で変える場合は糊クラス等に有効値を定数として定義し、`parseSeedId` と `moveSequencePastSeedBand`
  の両方へ同じ値を渡す（キット本体は書き換えない）。
- **Page Object・テストクラス・障害シーム**（機能対応物）。障害シームは、アプリの Bean を委譲で
  ラップするテストプロファイル限定 Bean として作り、障害を注入する分岐で
  `E2eFaultSeams.shouldFire(シーム名)` を呼ぶ（状態管理・発火記録はキット側）。

## 構成

- `support/E2eBaseTest.java` — 基底（Playwright 起動・オーバーレイ・録画・証跡書出・冪等化フック。
  アプリ起動注釈は持たず糊クラスが付与する）
- `support/E2eSpecLayout.java` — 実施単位／機能の配置解決（`e2e.target`／`e2e.features`。仕様ルートの
  既定は testgen-shared「実施単位・配置規範」と同値で、`e2e.spec-root` で上書き可）
- `support/E2eEvidence.java`・`support/E2eResultRecorder.java` — 証跡モード判定・ケース結果記録
- `support/E2eSeedSupport.java` — シード CSV の汎用支援（ID 帯域上限の既定値 `SEED_ID_MAX` を含む。
  有効値はプロジェクト側から引数で渡して変えられる）
- `support/E2eFaultSeams.java` — 障害シームの状態管理（ケース単位トグル・発火後自動解除・
  ベースラインリセット時に全解除・発火記録。委譲シーム Bean 自体は機能対応物として exec が作る）
- `support/E2eMailServer.java`・`support/E2eMailbox.java` — 偽 SMTP（MailHog）補助
- `support/evidence/E2eVerify.java` — 検証・操作部品（部品カタログは SKILL.md）
- `support/evidence/E2eGridAdapter.java`・`E2eAgGridAdapter.java` — 仮想化グリッドの構造契約
  （ロケータ供給＋列幅ドラッグ操作。値の読取・突合は部品側）と ag-grid 実装。グリッドライブラリ
  固有のセレクタと内部挙動（リサイズ機構等）はアダプタへ隔離し、部品・テストコードに持ち込まない
  （テスト側での `page.mouse()` によるリサイズドラッグの手組みは禁止）。別ライブラリのプロジェクトは
  アダプタのみプロジェクト側（`testgen.e2e` 配下）で実装して差し替える
- `support/evidence/E2eFrameService.java`・`E2eStepRecorder.java`・`E2eLogCollector.java` — フレーム撮影・
  steps.json・証跡ログ（アプリのルートパッケージはコンストラクタ引数＝糊クラス経由）
- `support/evidence/EvidenceV2.java`・`E2eRawDom.java`・`EvidenceV2GuardrailTest.java` — マーカー注釈
  （テストクラス識別／キット外の生 DOM アクセス例外宣言）と ArchUnit ガードレール（解析対象
  `testgen.e2e` 固定。テストクラスは DOM 読取全面禁止・キット外は `@E2eRawDom` 付きメソッドのみ
  生 DOM アクセス可・素アサーションはキット外全面禁止）
- `gradle/testgen.e2e.gradle.kts` — Gradle 設定（E2E 専用依存・`e2e` タグ除外・`e2eTest`／`e2eEvidence`／
  `e2eEvidenceIndex` タスク・ビューア生成スクリプトの呼び出し）

ビルド設定は転写物 `testgen.e2e.gradle.kts`（プロジェクトルート直下）が持ち、`build.gradle.kts` 側は
`apply(from = "testgen.e2e.gradle.kts")` の 1 行だけになる。注意: この適用より後で `test` タスクの
`useJUnitPlatform` を再設定して `excludeTags` を上書きしないこと（`e2e` タグ除外が失われる）。
