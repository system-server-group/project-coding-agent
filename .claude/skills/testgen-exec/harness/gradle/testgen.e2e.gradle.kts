// =============================================================================
// E2E テストハーネスの Gradle 設定（testgen-exec キット）
//
// 正本はスキル同梱キット（harness/gradle/testgen.e2e.gradle.kts）。プロジェクトへは
// ルート直下の testgen.e2e.gradle.kts として転写し、build.gradle.kts から
//     apply(from = "testgen.e2e.gradle.kts")
// の 1 行で取り込む。キット本体のロジックは書き換えない（適合は [PROJECT] のみ）。
//
// 前提:
//   - java プラグイン適用済みの Spring Boot プロジェクト（バージョン省略の依存は
//     Spring Boot の dependency-management で解決する）
//   - 本スクリプトの適用より後で test タスクの useJUnitPlatform を再設定して
//     excludeTags を上書きしないこと（e2e タグ除外が失われる）
// =============================================================================

// --- ハーネスが必要とする依存（テスト専用） ---
dependencies {
    // ブラウザ操作・証跡撮影（検証部品 E2eVerify ほか）
    "testImplementation"("com.microsoft.playwright:playwright:1.49.0")
    // 証跡ガードレール: @EvidenceV2 テストでの素のアサーション・Locator DOM 読取の直呼びを
    // 禁止し、検証部品経由を構造で強制する。1.3.x は ASM が Java 25 のクラスファイル
    // (major 69) を読めず対象 0 件になるため 1.4.1 以上を使う。
    "testImplementation"("com.tngtech.archunit:archunit-junit5:1.4.1")
    // 偽 SMTP（MailHog）コンテナの起動（E2eMailServer）
    "testImplementation"("org.testcontainers:testcontainers")
    // JUnit Platform の起動（Gradle はテスト実行時に明示の宣言を要求する）
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// 既定の test は E2E (e2e タグ) を除外する。ブラウザ・Docker を要する E2E は
// 専用の e2eTest タスクで実行する。
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("e2e")
    }
}

// --- 実行対象の指定 ---

// テスト実施単位（docs/tests/テストケース仕様/<実施単位>/）。仕様・データ・ベースライン・証跡は
// すべてこの配下で一式に閉じる。意図しない実施単位への実行・証跡生成を防ぐため既定値は設けず、
// -Pe2e.target=<実施単位> で必ず指定する。
val e2eTarget: String? = providers.gradleProperty("e2e.target").orNull

// 実施単位の中で対象機能を絞る（機能フォルダ名のカンマ区切り）。未指定なら実施単位の全機能。
// 例: ./gradlew e2eEvidence -Pe2e.target=<実施単位> -Pe2e.features=<機能名>
val e2eFeatures: String? = providers.gradleProperty("e2e.features").orNull

// エビデンス生成時の操作ディレイ (slowMo, ms)。未指定なら 500（受け入れ用の見やすい動画）。
// 例: -Pe2e.slow-mo-ms=0 で slowMo を切りつつ証跡を取得できる（flaky 調査等）。
val e2eEvidenceSlowMo: String = providers.gradleProperty("e2e.slow-mo-ms").orNull ?: "500"

// --- テストケース仕様の配置・命名（テスト規約が定める） ---
val e2eSpecRoot = "docs/tests/テストケース仕様"
val e2eEvidenceDirName = "エビデンス"
val e2eSpecGlob = "*_テスト仕様書.xlsx"
val e2eDataDirName = "data"

// [PROJECT] ビューア生成ツール（testgen-exec スキル同梱）の場所。スキルの設置場所に合わせる。
val e2eEvidenceIndexScript =
    ".claude/skills/testgen-exec/scripts/e2e_evidence/e2e_evidence_index.py"

// 実施単位の指定を強制する。テスト実行と index 生成が同じ指定を見るため、両者の対象がずれない。
val requireE2eTarget: () -> String = {
    e2eTarget ?: throw GradleException(
        "テスト実施単位を指定してください（例: -Pe2e.target=<実施単位>）。")
}

// e2eTest / e2eEvidence の共通設定（対象の指定は両方に効かせる）。
val testSourceSet = the<SourceSetContainer>()["test"]
val configureE2eTask: Test.() -> Unit = {
    group = "verification"
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeTags("e2e")
    }
    e2eTarget?.let { systemProperty("e2e.target", it) }
    e2eFeatures?.let { systemProperty("e2e.features", it) }
    // [Windows] 起動経路のどこかで環境変数が欠けていても（環境の欠けた親から起動された
    // Gradle デーモン経由等）、ブラウザ子プロセスが %SystemDrive% を展開できず CWD に
    // literal な %SystemDrive% フォルダを作るのを防ぐ。揃っている環境では同値を渡すだけ（無害）。
    if (System.getProperty("os.name").startsWith("Windows")) {
        val drive = System.getenv("SystemDrive") ?: "C:"
        environment("SystemDrive", drive)
        environment("ALLUSERSPROFILE", System.getenv("ALLUSERSPROFILE") ?: "$drive\\ProgramData")
        environment("ProgramData", System.getenv("ProgramData") ?: "$drive\\ProgramData")
    }
    // ハーネス側でも必須だが、テスト起動（コンテナ・ブラウザ）の前に分かりやすく落とす。
    doFirst { requireE2eTarget() }
    shouldRunAfter("test")
}

tasks.register<Test>("e2eTest") {
    description = "E2E (Playwright) テストを実行する（ブラウザ・Docker が必要）"
    configureE2eTask()
}

// エビデンス生成モード。slowMo を有効化して受け入れ用の見やすい動画・証跡を生成する。
// 通常の検証は e2eTest（slowMo=0・高速）を使い、本タスクは証跡が必要なときに実行する。
tasks.register<Test>("e2eEvidence") {
    description = "E2E をエビデンス生成モード（slowMo 有効）で実行し、受け入れ用の証跡を生成する"
    configureE2eTask()
    systemProperty("e2e.slow-mo-ms", e2eEvidenceSlowMo)
    // 証跡モードを有効化する（スクリーンショット・動画・トレースを出力）。
    systemProperty("e2e.evidence", "true")
    // 証跡を毎回作り直すため、UP-TO-DATE でスキップさせない。
    outputs.upToDateWhen { false }
    // 証跡出力後に受け入れ用 index.html（仕様書本文＋証跡）を生成する。
    finalizedBy("e2eEvidenceIndex")
}

// index 生成の対象。テスト実行とまったく同じ指定（実施単位＋機能）から決めるため、実行した対象と
// 一致し、別の実施単位・実行していない機能の index.html を作り直すことがない。
// 実施単位が未指定のときは空にしておき、タスク実行時に requireE2eTarget() で落とす。
val e2eEvidenceIndexTargets: List<String> = if (e2eTarget == null) {
    emptyList()
} else {
    val unit = "$e2eSpecRoot/$e2eTarget"
    val features = e2eFeatures?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
    if (features.isNullOrEmpty()) listOf(unit) else features.map { "$unit/$it" }
}

// E2E エビデンスの受け入れ用 index.html を、テスト仕様書(xlsx)の本文（ケース名・操作手順・
// 期待する結果）とともに 1ケース＝1画面で確認できる形で生成する（要 Python + openpyxl）。
tasks.register<Exec>("e2eEvidenceIndex") {
    group = "verification"
    description = "E2E エビデンスの受け入れ用 index.html を仕様書(xlsx)本文とともに生成する"
    commandLine(
        listOf("python", e2eEvidenceIndexScript) + e2eEvidenceIndexTargets + listOf(
            "--evidence-dir-name", e2eEvidenceDirName,
            "--spec-glob", e2eSpecGlob,
            "--data-dir-name", e2eDataDirName,
        )
    )
    environment("PYTHONUTF8", "1")
    // 終了コードで扱いを分ける（3=実行環境の不備は警告のみとし、証跡本体の生成は妨げない）。
    isIgnoreExitValue = true
    doFirst { requireE2eTarget() }
    doLast {
        val exitValue = executionResult.get().exitValue
        if (exitValue == 3) {
            logger.warn("警告: index.html を生成できませんでした（Python 環境の不備）。証跡本体は生成済みです。")
        } else if (exitValue != 0) {
            throw GradleException("index.html の生成に失敗しました（終了コード $exitValue）。")
        }
    }
}
