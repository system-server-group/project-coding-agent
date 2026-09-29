package testgen.e2e.tests;

import com.microsoft.playwright.options.LoadState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import testgen.e2e.ProjectE2eTest;
import testgen.e2e.data.E2eTestFiles;
import testgen.e2e.pages.CommonHeaderPage;
import testgen.e2e.pages.ErrorPage;
import testgen.e2e.pages.LoginPage;
import testgen.e2e.pages.ProjectEditPage;
import testgen.e2e.pages.ProjectFormPage;
import testgen.e2e.pages.ProjectListPage;
import testgen.e2e.seams.ProjectRegisterFaultSeamConfig;
import testgen.e2e.seams.ProjectUpdateFaultSeamConfig;
import testgen.e2e.support.E2eFeature;
import testgen.e2e.support.E2eMailServer;
import testgen.e2e.support.E2eMailbox;
import testgen.e2e.support.E2eSeedSupport;
import testgen.e2e.support.E2eSpecLayout;
import testgen.e2e.support.evidence.EvidenceV2;

/**
 * 案件情報更新画面（機能 {@code 13_案件情報更新}）の E2E テスト。
 *
 * <p>
 * テストケース仕様書 {@code 13_案件情報更新/13_案件情報更新_テスト仕様書.xlsx} の全ケースを実装する。 添付ファイルのダウンロード・削除（ネイティブ
 * confirm）、別ブラウザによる排他制御、障害シームを用いる。
 */
@EvidenceV2
@Import({E2eMailServer.class, ProjectRegisterFaultSeamConfig.class,
        ProjectUpdateFaultSeamConfig.class})
@E2eFeature("13_案件情報更新")
class ProjectUpdateE2eTest extends ProjectE2eTest {

    /** 一般ユーザ SM0001（営業 太郎／営業部）。 */
    private static final String GENERAL_USER = "SM0001";

    /** ベースラインのユーザに共通の開発用ダミーパスワード。 */
    private static final String PASSWORD = "Passw0rd!";

    /** シードで投入する案件情報の管理コード。 */
    private static final int CODE = 1;

    /** シードの案件情報の更新日時（更新されていないことの確認に用いる）。 */
    private static final String SEEDED_TIMESTAMP = "2026/08/01 10:00:00";

    /** 案件情報のシード CSV（実施単位からの相対パス）。 */
    private static final String SEED_PROJECT = "13_案件情報更新/data/案件情報_シード.csv";

    /** 添付ファイル4件のシード CSV。 */
    private static final String SEED_ATTACH_4 = "13_案件情報更新/data/添付ファイル_4件.csv";

    /** 添付ファイル5件のシード CSV。 */
    private static final String SEED_ATTACH_5 = "13_案件情報更新/data/添付ファイル_5件.csv";

    /** 添付ファイルの素材（46バイト）。 */
    private static final String ATTACH_MATERIAL = "13_案件情報更新/data/添付素材.txt";

    /** 添付として指定する既存の正本ファイル（218バイト）。 */
    private static final String BOUNDARY_CSV = "13_案件情報更新/data/添付ファイル境界.csv";

    /** 必須入力エラー。 */
    private static final String REQUIRED = "この項目は入力が必要です。";

    /** 使用文字エラー。 */
    private static final String ILLEGAL_CHARACTER = "使用不可能な文字が含まれています。";

    /** 半角カタカナを含む使用不可能な文字列。 */
    private static final String HALF_WIDTH_KANA = "ｱｲｳ";

    /** 添付ファイル（6MBちょうど）の名前。 */
    private static final String ATTACH_6MB = "attach-6mb.bin";

    /** 添付ファイル（6MB+1バイト）の名前。 */
    private static final String ATTACH_6MB_PLUS1 = "attach-6mb-plus1.bin";

    /** 添付ファイルの容量違反メッセージ。 */
    private static final String TOO_LARGE = "添付ファイルの容量が大きすぎます(6MBまで)";

    /** 添付ファイル名長の違反メッセージ。 */
    private static final String NAME_TOO_LONG = "ファイル名は200字までです。";

    /** 添付ファイル件数の違反メッセージ。 */
    private static final String TOO_MANY = "登録できる添付ファイルは5件までです。";

    /** ファイル未指定時のファイルピッカーの表示。 */
    private static final String DROP_PLACEHOLDER = "クリックでファイル選択 ／ ドラッグ＆ドロップ";

    /** 画面表示の日時（yyyy/MM/dd HH:mm:ss）の抽出用（sameText の複合表示比較）。 */
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");

    /** 削除確認ダイアログの本文。 */
    private static final String DELETE_CONFIRM = "この添付ファイルを削除しますか？";

    @Autowired
    private E2eMailbox mailbox;

    private LoginPage loginPage;
    private CommonHeaderPage commonHeader;
    private ProjectEditPage editPage;
    private ProjectListPage listPage;
    private ErrorPage errorPage;


    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(page, baseUrl());
        commonHeader = new CommonHeaderPage(page, baseUrl());
        editPage = new ProjectEditPage(page, baseUrl());
        listPage = new ProjectListPage(page, baseUrl());
        errorPage = new ErrorPage(page, baseUrl());
        mailbox.reset();
        insertProjects(unitData(SEED_PROJECT));
        loginPage.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);
    }

    // ---- 1. 画面仕様・初期表示 ----

    @Test
    @DisplayName("No.1-1 案件情報更新画面の初期表示（画面項目と編集保護オンの初期状態）")
    void case1x1() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));

        verify.text("画面タイトル「案件情報更新画面」が表示される", commonHeader.screenTitle(),
                ProjectEditPage.SCREEN_TITLE + " ID: 1");
        verify.checked("「編集保護」のトグルがオンの状態で表示される", editPage.editProtectCheckbox());
        verify.text("画面上部の「戻る」ボタンが表示される", editPage.backButtonTop(), "← 戻る");
        verify.text("画面下部の「戻る」ボタンが表示される", editPage.backButtonBottom(), "← 戻る");
        verify.absent("「編集確定」ボタンは表示されない（編集保護がオンの間は非表示）", editPage.attachmentSectionLabel(),
                editPage.visibleSubmitButton());
        verify.absent("「メール」「メール送信」チェックボックス・メール本文も表示されない", editPage.attachmentSectionLabel(),
                editPage.visibleMailSection());
        verify.absent("削除確認ダイアログとエラーメッセージは表示されない", editPage.attachmentSectionLabel(),
                editPage.dialogs().or(editPage.errorArea()));
    }

    @Test
    @DisplayName("No.1-2 指定された管理コードの案件情報が各画面項目に表示される")
    void case1x2() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));

        verify.text("画面タイトルの後ろに管理コードが「ID: 1」と表示される", editPage.managementCodeBadge(), "ID: 1");
        verify.value("「件名」に「更新対象案件」が表示される", editPage.input(ProjectFormPage.SUBJECT), "更新対象案件");
        verify.value("「ステータス」に「オープン」が表示される", editPage.input(ProjectFormPage.STATUS), "OPEN");
        verify.value("「取引先」に「株式会社アルファ」が表示される", editPage.input(ProjectFormPage.CLIENT), "株式会社アルファ");
        verify.value("「商流」に「一次請け」が表示される", editPage.input(ProjectFormPage.DISTRIBUTION), "一次請け");
        verify.value("「案件概要」に「更新前の概要」が表示される", editPage.input(ProjectFormPage.OVERVIEW), "更新前の概要");
        verify.value("「工程」に「詳細設計」が表示される", editPage.input(ProjectFormPage.PROCESS), "詳細設計");
        verify.value("「開始日」に 2026/09/01 が表示される", editPage.input(ProjectFormPage.START_DATE),
                "2026-09-01");
        verify.value("「終了日」に 2026/12/31 が表示される", editPage.input(ProjectFormPage.END_DATE),
                "2026-12-31");
        verify.value("「スキル」に「Java」が表示される", editPage.input(ProjectFormPage.SKILL), "Java");
        verify.value("「人数」に「3」が表示される", editPage.input(ProjectFormPage.HEADCOUNT), "3");
        verify.value("「残数」に「2」が表示される", editPage.input(ProjectFormPage.REMAINING_COUNT), "2");
        verify.unchecked("「BPの募集が必要です」はチェックなし（不要）である", editPage.input(ProjectFormPage.BP_REQUIRED));
        verify.value("「作業場所」に「東京都港区」が表示される", editPage.input(ProjectFormPage.WORKPLACE), "東京都港区");
        verify.value("「見込単価」に「600000」が表示される", editPage.input(ProjectFormPage.ESTIMATED_PRICE),
                "600000");
        verify.value("「契約種別」に「請負」が表示される", editPage.input(ProjectFormPage.CONTRACT_TYPE),
                "CONTRACT");
        verify.value("「備考」に「更新前の備考」が表示される", editPage.input(ProjectFormPage.NOTE), "更新前の備考");
        verify.text("「登録者」に「営業 太郎」が表示される", editPage.meta("登録者"), "営業 太郎");
        verify.text("「登録部署」に「営業部」が表示される", editPage.meta("登録部署"), "営業部");
        verify.text("「更新者」に「営業 太郎」が表示される", editPage.meta("更新者"), "営業 太郎");
        verify.text("「更新部署」に「営業部」が表示される", editPage.meta("更新部署"), "営業部");
        verify.text("「登録日時」が「2026/08/01 10:00:00」で表示される", editPage.meta("登録日時"), SEEDED_TIMESTAMP);
        verify.text("「更新日時」が「2026/08/01 10:00:00」で表示される", editPage.meta("更新日時"), SEEDED_TIMESTAMP);
    }

    @Test
    @DisplayName("No.1-3 取引先のオートコンプリートに、全会社名が会社IDの昇順で表示される（自由記入用の空欄は選択肢として表示されない）")
    void case1x3() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「取引先」の入力欄をクリックして選択肢を表示する",
                () -> editPage.input(ProjectFormPage.CLIENT).click(),
                editPage.input(ProjectFormPage.CLIENT));

        verify.autocompleteOptions("選択肢に会社名が会社IDの昇順で並ぶ（自由記入用の空欄は選択肢として表示されない）",
                editPage.input(ProjectFormPage.CLIENT), "clientOptions", expectedClientOptions());
    }

    @Test
    @DisplayName("No.1-4 登録済みの添付ファイルが登録日時の昇順で、既定の表示形式で一覧表示される")
    void case1x4() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));

        verify.countIs("「添付ファイル」の一覧に4件が表示される", editPage.attachmentRows(), 4);
        verify.text("1行目が「1. 添付1.txt(46B)」である", editPage.attachmentName(1), "1. 添付1.txt(46B)");
        verify.text("1行目のメタが「営業 太郎(営業部) – 2026/08/01 11:00:00」である", editPage.attachmentMeta(1),
                "営業 太郎(営業部) – 2026/08/01 11:00:00");
        verify.text("2行目が「2. 添付2.txt(46B)」である", editPage.attachmentName(2), "2. 添付2.txt(46B)");
        verify.text("2行目のメタが「営業 太郎(営業部) – 2026/08/02 12:00:00」である", editPage.attachmentMeta(2),
                "営業 太郎(営業部) – 2026/08/02 12:00:00");
        verify.text("3行目が「3. 添付3.txt(46B)」である", editPage.attachmentName(3), "3. 添付3.txt(46B)");
        verify.text("4行目が「4. 添付4.txt(46B)」である", editPage.attachmentName(4), "4. 添付4.txt(46B)");
    }

    @Test
    @DisplayName("No.1-5 管理コードが指定されていない場合、エラー画面にページ未検出エラーが表示される")
    void case1x5() {
        verify.opOpen("1. 管理コードを指定せずに /projects/detail/ を開く",
                () -> errorPage.openUrl("/projects/detail/"));

        verify.text("エラータイトル「404 Not Found」が表示される", errorPage.errorTitle(), "404 Not Found");
        verify.text("エラー内容「ページが見つかりませんでした。」が表示される", errorPage.errorMessage(), "ページが見つかりませんでした。");
        verify.absent("案件情報更新画面の識別要素（画面タイトル「案件情報更新画面」）が表示されない", errorPage.card(),
                commonHeader.screenTitleOf(ProjectEditPage.SCREEN_TITLE));
    }

    @Test
    @DisplayName("No.1-6 登録されていない管理コードの場合、画面タイトルとエラーメッセージ以外の画面項目が非表示になる")
    void case1x6() {
        verify.opOpen("1. /projects/detail/9999 を開く（管理コード 9999 は登録されていない）",
                () -> editPage.open(9999));

        verify.containsText("画面タイトル「案件情報更新画面」が表示される", commonHeader.screenTitle(),
                ProjectEditPage.SCREEN_TITLE);
        verify.text("エラータイトル「存在しない案件情報です」が表示される", editPage.errorTitle(), "存在しない案件情報です");
        verify.text("エラーメッセージ「IDが不正、もしくは削除された案件情報です。」が表示される", editPage.errorMessage(),
                "IDが不正、もしくは削除された案件情報です。");
        verify.absent("「件名」入力欄・「編集保護」トグルなど、画面タイトルとエラーメッセージ以外の" + "画面項目は全て表示されない",
                editPage.errorArea(),
                editPage.input(ProjectFormPage.SUBJECT).or(editPage.editProtectCheckbox()));
    }

    @Test
    @DisplayName("No.1-7 添付ファイルが0件の場合、ファイル一覧を非表示とする")
    void case1x7() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(0));

        verify.absent("「添付ファイル」の一覧が表示されない", editPage.attachmentDropArea(),
                editPage.attachmentList());
    }

    @Test
    @DisplayName("No.1-8 区分項目（ステータス・BP募集(要否)・契約種別）が表示名で表示され、選択肢が既定のとおりである")
    void case1x8() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);

        verify.value("「ステータス」に区分値 OPEN の表示名「オープン」が表示される", editPage.input(ProjectFormPage.STATUS),
                "OPEN");
        verify.value("「契約種別」に区分値 CONTRACT の表示名「請負」が表示される",
                editPage.input(ProjectFormPage.CONTRACT_TYPE), "CONTRACT");
        verify.unchecked("「BPの募集が必要です」は区分値 NOT_REQUIRED（不要）に対応してチェックなしである",
                editPage.input(ProjectFormPage.BP_REQUIRED));
        verify.selectOptions("3. 「ステータス」の選択肢が「オープン」「交渉中」「クローズ」の3つである",
                editPage.input(ProjectFormPage.STATUS), List.of("オープン", "交渉中", "クローズ"));
        verify.selectOptions("4. 「契約種別」の選択肢が「」(空欄)「請負」「準委任」「派遣」の4つである",
                editPage.input(ProjectFormPage.CONTRACT_TYPE), List.of("", "請負", "準委任", "派遣"));
    }

    // ---- 2. 画面イベント処理 ----

    @Test
    @DisplayName("No.2-1 編集保護トグルの切替で編集確定ボタン・メール領域の表示が切り替わる")
    void case2x1() {
        verify.opOpen("1. /projects/detail/1 を開く（編集保護はオン、「編集確定」ボタンとメール領域は非表示）",
                () -> editPage.open(CODE));

        verify.op("2. 「編集保護」のトグルをオフに切り替える", () -> editPage.turnOffEditProtect(),
                editPage.editProtectCheckbox());
        verify.visible("2. 「編集確定」ボタンが現れる", editPage.visibleSubmitButton());
        verify.visible("2. 「メール」「メール送信」チェックボックスとメール本文も現れる", editPage.visibleMailSection());

        verify.transitionHide("「編集確定」ボタンが表示されている", editPage.visibleSubmitButton(),
                "3. 「編集保護」のトグルをオンに戻す", () -> editPage.turnOnEditProtect(),
                editPage.editProtectCheckbox(), "「編集確定」ボタンが消える");
        verify.absent("3. メール領域も消える", editPage.attachmentSectionLabel(),
                editPage.visibleMailSection());
    }

    @Test
    @DisplayName("No.2-2 編集保護がオンの間は【編集保護あり】の項目を入力できず、オフにすると入力できる")
    void case2x2() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));

        verify.machineEquals("手順2の時点で「件名」入力欄が入力不可（disabled または readonly）である", "入力不可",
                editPage.subjectInputEditability());
        verify.value("2. 「件名」の値は「更新対象案件」のまま変わらず、入力できない", editPage.input(ProjectFormPage.SUBJECT),
                "更新対象案件");

        unprotect(3);
        verify.machineEquals("手順3の後は「件名」入力欄が入力可能になっている", "入力可能",
                editPage.subjectInputEditability());
        verify.op("4. 「件名」を「編集後の件名」に書き換える", () -> editPage.fill(ProjectFormPage.SUBJECT, "編集後の件名"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.value("4. 「件名」の値が「編集後の件名」に変わる", editPage.input(ProjectFormPage.SUBJECT), "編集後の件名");
    }

    @Test
    @DisplayName("No.2-3 編集保護をオフにすると、メール送信チェックボックスは初期値の「メール送信あり」で表示される")
    void case2x3() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);

        verify.checked("「メール送信」チェックボックスがチェックありの状態で表示される",
                editPage.input(ProjectFormPage.SEND_MAIL));
        verify.visible("その下にメール本文のテキストエリアが表示される", editPage.visibleMailBody());
    }

    @Test
    @DisplayName("No.2-4 メール送信チェックボックスの切替でメール本文の表示・非表示が切り替わる")
    void case2x4() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.visible("2. メール本文のテキストエリアが表示されている", editPage.visibleMailBody());

        verify.transitionHide("メール本文のテキストエリアが表示されている", editPage.visibleMailBody(),
                "3. 「メール送信」のチェックを外す", () -> editPage.uncheck(ProjectFormPage.SEND_MAIL),
                editPage.input(ProjectFormPage.SEND_MAIL), "メール本文のテキストエリアが消える");

        verify.op("4. もう一度「メール送信」にチェックを入れる", () -> editPage.check(ProjectFormPage.SEND_MAIL),
                editPage.input(ProjectFormPage.SEND_MAIL));
        verify.visible("メール本文のテキストエリアが再び表示される", editPage.visibleMailBody());
    }

    @Test
    @DisplayName("No.2-5 項目を変更した後に編集保護をオンにしても、項目の入力値は編集された内容のままとする")
    void case2x5() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「件名」を「編集後の件名」に書き換える", () -> editPage.fill(ProjectFormPage.SUBJECT, "編集後の件名"),
                editPage.input(ProjectFormPage.SUBJECT));

        verify.transitionHide("「編集確定」ボタンが表示されている", editPage.visibleSubmitButton(),
                "4. 「編集保護」のトグルをオンに戻す", () -> editPage.turnOnEditProtect(),
                editPage.editProtectCheckbox(), "「編集確定」ボタンは非表示になる");
        verify.value("「件名」の表示は「編集後の件名」のままであり、「更新対象案件」には戻らない",
                editPage.input(ProjectFormPage.SUBJECT), "編集後の件名");
    }

    @Test
    @DisplayName("No.2-6 ファイルピッカーで指定した添付ファイルが表示され、1回の更新で登録できるのは1件である")
    void case2x6() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path first = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        final Path second = unitData(BOUNDARY_CSV);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);

        verify.opChooseFile("3. 「添付」のファイルピッカーで attach-6mb.bin を指定する", editPage.attachmentDropArea(),
                first, editPage.attachmentDropArea());
        verify.containsText("3. 「添付」に「attach-6mb.bin(6MB)」が表示される", editPage.attachmentDropArea(),
                "attach-6mb.bin(6MB)");
        verify.absent("3. ファイル形式チェックのエラーメッセージは表示されない", editPage.attachmentDropArea(),
                editPage.attachmentErrorWhenShown());

        verify.opChooseFile("4. 「添付」のファイルピッカーで 添付ファイル境界.csv を指定する", editPage.attachmentDropArea(),
                second, editPage.attachmentDropArea());
        verify.text("「添付」の表示が「添付ファイル境界.csv(218B)」だけになり、" + "「attach-6mb.bin(6MB)」は表示されない",
                editPage.attachmentDropArea().locator(".drop-main"), "添付ファイル境界.csv(218B) ✕ 選択解除");
    }

    @Test
    @DisplayName("No.2-7 【境界値】添付ファイルの容量が6MBを超える場合、ファイル形式チェックに違反する")
    void case2x7() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB_PLUS1, 6291457);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.opChooseFileRejected(
                "3. 「添付」のファイルピッカーで attach-6mb-plus1.bin を指定する" + "（形式チェック違反で選択は却下される）",
                editPage.attachmentDropArea(), file, editPage.attachmentDropArea());

        verify.text("「添付」に表示されるのは「添付ファイルの容量が大きすぎます(6MBまで)」のみである" + "（「ファイル名は200字までです。」は表示されない）",
                editPage.attachmentError(), TOO_LARGE);
        verify.text("指定したファイル名はファイルピッカーに表示されず、ファイルが指定されていない状態の表示に戻る",
                editPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);
    }

    @Test
    @DisplayName("No.2-8 【境界値】添付ファイルの容量が6MBちょうどの場合、容量のファイル形式チェックに違反しない")
    void case2x8() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.opChooseFile("3. 「添付」のファイルピッカーで attach-6mb.bin を指定する", editPage.attachmentDropArea(),
                file, editPage.attachmentDropArea());

        verify.containsText("「添付」のファイルピッカーに「attach-6mb.bin(6MB)」が表示される",
                editPage.attachmentDropArea(), "attach-6mb.bin(6MB)");
        verify.absent("「添付ファイルの容量が大きすぎます(6MBまで)」は表示されない", editPage.attachmentDropArea(),
                editPage.attachmentErrorWhenShown());
    }

    @Test
    @DisplayName("No.2-9 【境界値】登録済みの添付ファイルが5件の場合、件数のファイル形式チェックに違反する")
    void case2x9() {
        insertAttachments(unitData(SEED_ATTACH_5));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く（「添付ファイル」の一覧に5件が表示されている）",
                () -> openDetailWithAttachments(5));
        verify.countIs("1. 「添付ファイル」の一覧に5件が表示されている", editPage.attachmentRows(), 5);
        unprotect(2);
        verify.opChooseFileRejected(
                "3. 「添付」のファイルピッカーで attach-6mb.bin を指定する" + "（件数チェック違反で選択は却下される）",
                editPage.attachmentDropArea(), file, editPage.attachmentDropArea());

        verify.text("「添付」に表示されるのは「登録できる添付ファイルは5件までです。」のみである" + "（「添付ファイルの容量が大きすぎます(6MBまで)」は表示されない）",
                editPage.attachmentError(), TOO_MANY);
        verify.text("指定したファイル名はファイルピッカーに表示されず、ファイルが指定されていない状態の表示に戻る",
                editPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);
    }

    @Test
    @DisplayName("No.2-10 【境界値】登録済みの添付ファイルが4件の場合、件数のファイル形式チェックに違反しない")
    void case2x10() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く（「添付ファイル」の一覧に4件が表示されている）",
                () -> openDetailWithAttachments(4));
        verify.countIs("1. 「添付ファイル」の一覧に4件が表示されている", editPage.attachmentRows(), 4);
        unprotect(2);
        verify.opChooseFile("3. 「添付」のファイルピッカーで attach-6mb.bin を指定する", editPage.attachmentDropArea(),
                file, editPage.attachmentDropArea());

        verify.containsText("「添付」のファイルピッカーに「attach-6mb.bin(6MB)」が表示される",
                editPage.attachmentDropArea(), "attach-6mb.bin(6MB)");
        verify.absent("「登録できる添付ファイルは5件までです。」は表示されない", editPage.attachmentDropArea(),
                editPage.attachmentErrorWhenShown());
    }

    @Test
    @DisplayName("No.2-11 【境界値】添付ファイル名が201文字の場合、ファイル形式チェックに違反する")
    void case2x11() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofNameLength(201);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.opChooseFileRejected(
                "3. 「添付」のファイルピッカーで、ファイル名が201文字のファイルを指定する" + "（形式チェック違反で選択は却下される）",
                editPage.attachmentDropArea(), file, editPage.attachmentDropArea());

        verify.text("「添付」のファイルピッカーに「ファイル名は200字までです。」が表示される", editPage.attachmentError(),
                NAME_TOO_LONG);
        verify.text("指定したファイル名はファイルピッカーに表示されず、ファイルが指定されていない状態の表示に戻る",
                editPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);
    }

    @Test
    @DisplayName("No.2-12 【境界値】添付ファイル名が200文字の場合、ファイル名長のファイル形式チェックに違反しない")
    void case2x12() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofNameLength(200);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.opChooseFile("3. 「添付」のファイルピッカーで、ファイル名が200文字のファイルを指定する",
                editPage.attachmentDropArea(), file, editPage.attachmentDropArea());

        verify.containsText("「添付」のファイルピッカーに指定したファイル名が表示される", editPage.attachmentDropArea(),
                file.getFileName().toString());
        verify.containsText("「添付」のファイルピッカーにファイルサイズ「(1B)」が表示される", editPage.attachmentDropArea(),
                "(1B)");
        verify.absent("「ファイル名は200字までです。」は表示されない", editPage.attachmentDropArea(),
                editPage.attachmentErrorWhenShown());
    }

    @Test
    @DisplayName("No.2-13 ファイル形式チェック違反後に再度ファイルを指定すると、既存のエラーメッセージが削除されて再チェックされる")
    void case2x13() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path tooLarge = E2eTestFiles.ofSize(ATTACH_6MB_PLUS1, 6291457);
        final Path valid = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.opChooseFileRejected(
                "3. 「添付」のファイルピッカーで attach-6mb-plus1.bin を指定する" + "（形式チェック違反で選択は却下される）",
                editPage.attachmentDropArea(), tooLarge, editPage.attachmentDropArea());
        verify.text("4. 「添付」に「添付ファイルの容量が大きすぎます(6MBまで)」が表示される", editPage.attachmentError(),
                TOO_LARGE);

        verify.transitionCleared("エラーメッセージが表示されている", editPage.attachmentErrorWhenShown(),
                "5. 「添付」のファイルピッカーで attach-6mb.bin を指定する",
                () -> editPage.attachmentInput().setInputFiles(valid),
                editPage.attachmentDropArea(), "エラーメッセージが消える");
        verify.containsText("「attach-6mb.bin(6MB)」が表示される", editPage.attachmentDropArea(),
                "attach-6mb.bin(6MB)");
    }

    @Test
    @DisplayName("No.2-14 ファイルピッカーは編集保護がオンの間は無効で、オフにすると有効になる")
    void case2x14() {
        final Path file = unitData(BOUNDARY_CSV);
        verify.opOpen("1. /projects/detail/1 を開く（編集保護はオン）", () -> openDetailWithAttachments(0));

        verify.noDownload("2. 「添付」のファイルピッカーをクリックしてもファイル選択ダイアログが開かない",
                () -> verify.op("2. 「添付」のファイルピッカーをクリックする",
                        () -> editPage.attachmentDropArea().click(),
                        editPage.attachmentDropArea()));
        verify.opDropFile("3. 「添付」のファイルピッカーへファイルをドラッグ＆ドロップする", editPage.attachmentDropArea(), file,
                editPage.attachmentDropArea());
        verify.text("3. ドロップしたファイルは受け付けられず、「添付」の表示は変わらない",
                editPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);

        unprotect(4);
        verify.text("4. 「添付」の表示はファイルが指定されていない状態で有効になる",
                editPage.attachmentDropArea().locator(".drop-main"), DROP_PLACEHOLDER);

        verify.opChooseFile("5. 「添付」のファイルピッカーで 添付ファイル境界.csv を指定する", editPage.attachmentDropArea(),
                file, editPage.attachmentDropArea());
        verify.containsText("5. 「添付」の表示が「添付ファイル境界.csv(218B)」に変わる（有効化されている）",
                editPage.attachmentDropArea(), "添付ファイル境界.csv(218B)");
    }

    // ---- 3. 画面遷移（戻る） ----

    @Test
    @DisplayName("No.3-1 戻るボタン（画面上部・下部の2箇所）で入力を破棄して案件情報一覧画面へ遷移する")
    void case3x1() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        fillDiscardTargets();
        verify.opPress("4. 画面上部の「戻る」ボタンを押下する", editPage.backButtonTop(),
                () -> editPage.pressBackTop());
        verify.urlIs("4. /projects（案件情報一覧画面）へ遷移する", listPage.url());

        verify.opOpen("5. 案件情報更新画面へ戻る", () -> editPage.open(CODE));
        unprotect(5);
        fillDiscardTargets();
        verify.opPress("6. 画面下部の「戻る」ボタンを押下する", editPage.backButtonBottom(),
                () -> editPage.pressBackBottom());
        verify.urlIs("6. /projects（案件情報一覧画面）へ遷移する", listPage.url());

        verify.opOpen("7. 案件情報更新画面を開く", () -> editPage.open(CODE));
        verify.value("7. 「件名」は「更新対象案件」であり、「破棄される件名」にはなっていない",
                editPage.input(ProjectFormPage.SUBJECT), "更新対象案件");
    }

    /** 3-1 の手順3（破棄対象の入力）。上部・下部の戻るボタンで同じ入力を繰り返す。 */
    private void fillDiscardTargets() {
        verify.op("3. 「件名」を「破棄される件名」に書き換える",
                () -> editPage.fill(ProjectFormPage.SUBJECT, "破棄される件名"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. メール本文に「破棄されるはず」を入力する",
                () -> editPage.fill(ProjectFormPage.MAIL_BODY, "破棄されるはず"),
                editPage.input(ProjectFormPage.MAIL_BODY));
    }

    // ---- 4. 外部入出力（添付ファイルの一覧・ダウンロード・削除） ----

    @Test
    @DisplayName("No.4-1 添付ファイル一覧が登録日時の昇順で、序数が表示順に振られる")
    void case4x1() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));

        verify.texts("2. 4行が登録日時の昇順で並び、序数は上から 1・2・3・4 が振られる",
                editPage.attachmentRows().locator(".att-name"), new String[] {"1. 添付1.txt(46B)",
                        "2. 添付2.txt(46B)", "3. 添付3.txt(46B)", "4. 添付4.txt(46B)"});
        verify.texts("2. 各行に「営業 太郎(営業部) – 各登録日時」が表示される",
                editPage.attachmentRows().locator(".att-meta"),
                new String[] {"営業 太郎(営業部) – 2026/08/01 11:00:00",
                        "営業 太郎(営業部) – 2026/08/02 12:00:00", "営業 太郎(営業部) – 2026/08/03 13:00:00",
                        "営業 太郎(営業部) – 2026/08/04 14:00:00"});
    }

    @Test
    @DisplayName("No.4-2 ファイルピッカーで新たに指定したファイルは未登録のため、添付ファイル一覧に反映されない")
    void case4x2() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く（一覧に4件が表示されている）",
                () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.opChooseFile("3. 「添付」のファイルピッカーで attach-6mb.bin を指定する", editPage.attachmentDropArea(),
                file, editPage.attachmentDropArea());

        verify.containsText("「添付」のファイルピッカーには「attach-6mb.bin(6MB)」が表示される",
                editPage.attachmentDropArea(), "attach-6mb.bin(6MB)");
        verify.countIs("「添付ファイル」の一覧は4件のままで、「attach-6mb.bin」の行は現れない", editPage.attachmentRows(), 4);
    }

    @Test
    @DisplayName("No.4-3 編集保護がオンの時にファイル一覧の行をクリックすると、該当ファイルがダウンロードされる")
    void case4x3() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く（編集保護はオン）", () -> openDetailWithAttachments(4));

        verify.csvDownloaded("2. 一覧の1行目「1. 添付1.txt(46B)」をクリックするとファイル名「添付1.txt」で" + "ダウンロードされる",
                "添付1.txt", unitData(ATTACH_MATERIAL), () -> editPage.downloadAttachmentRow(1));
        verify.absent("削除確認ダイアログは表示されない", editPage.attachmentSectionLabel(), editPage.dialogs());
    }

    @Test
    @DisplayName("No.4-4 ダウンロード時に該当ファイルが既に削除されている場合、ダウンロードせず一覧のみを最新化する")
    void case4x4() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く（編集保護はオン。一覧に4件が表示されている）",
                () -> openDetailWithAttachments(4));
        verify.countIs("1. 一覧に4件が表示されている", editPage.attachmentRows(), 4);

        deleteFirstAttachmentInOtherBrowser(2);

        verify.noDownload("3. 一覧の1行目をクリックしてもファイルはダウンロードされない",
                () -> verify.op("3. 「添付ファイル」の一覧の1行目「1. 添付1.txt(46B)」をクリックする",
                        () -> editPage.clickAttachmentRow(1), editPage.attachmentList()));

        verify.texts("「添付ファイル」の一覧のみが再取得されて最新化され、3件が序数 1・2・3 で表示される",
                editPage.attachmentRows().locator(".att-name"),
                new String[] {"1. 添付2.txt(46B)", "2. 添付3.txt(46B)", "3. 添付4.txt(46B)"});
        verify.absent("画面に通知・エラーメッセージは表示されない", editPage.attachmentSectionLabel(),
                editPage.errorArea());
    }

    @Test
    @DisplayName("No.4-5 編集保護がオフの時にファイル一覧の行をクリックすると、削除確認ダイアログが表示される")
    void case4x5() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く（削除確認ダイアログは表示されていない）",
                () -> openDetailWithAttachments(4));
        verify.absent("1. 削除確認ダイアログは表示されていない", editPage.attachmentSectionLabel(),
                editPage.dialogs());
        unprotect(2);

        verify.noDownload("3. ファイルのダウンロードは発生しない",
                () -> verify.opPressConfirm("3. 「添付ファイル」の一覧の1行目「1. 添付1.txt(46B)」をクリックする",
                        editPage.attachmentRows().first(), DELETE_CONFIRM, false,
                        () -> editPage.clickAttachmentRow(1), editPage.attachmentList()));

        verify.countIs("削除は行われず、一覧は4件のままである", editPage.attachmentRows(), 4);
    }

    @Test
    @DisplayName("No.4-6 削除確認ダイアログのOKボタンで添付ファイルが削除され、一覧のみが再取得されて最新化される")
    void case4x6() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);

        verify.opPressConfirm("3. 一覧の1行目をクリックし、4. 削除確認ダイアログの「OK」ボタンを押下する",
                editPage.attachmentRows().first(), DELETE_CONFIRM, true, () -> {
                    editPage.clickAttachmentRow(1);
                    editPage.waitForAttachmentRows(3);
                }, editPage.attachmentList());

        verify.texts("「添付ファイル」の一覧が3件になり、序数が 1・2・3 に振り直される",
                editPage.attachmentRows().locator(".att-name"),
                new String[] {"1. 添付2.txt(46B)", "2. 添付3.txt(46B)", "3. 添付4.txt(46B)"});
        verify.value("「件名」など案件情報の各項目の表示は変わらない", editPage.input(ProjectFormPage.SUBJECT), "更新対象案件");
        verify.urlIs("画面遷移も起きない（一覧のみが再取得される）", editPage.url(CODE));
        verify.text("案件情報の更新日時が 2026/08/01 10:00:00 のままである", editPage.meta("更新日時"),
                SEEDED_TIMESTAMP);
    }

    @Test
    @DisplayName("No.4-7 削除確認ダイアログのキャンセルボタンで添付ファイルの削除を中止する")
    void case4x7() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);

        verify.opPressConfirm("3. 一覧の1行目をクリックし、4. 削除確認ダイアログの「キャンセル」ボタンを押下する",
                editPage.attachmentRows().first(), DELETE_CONFIRM, false,
                () -> editPage.clickAttachmentRow(1), editPage.attachmentList());

        verify.texts("「添付ファイル」の一覧は4件のままで、削除は実施されない", editPage.attachmentRows().locator(".att-name"),
                new String[] {"1. 添付1.txt(46B)", "2. 添付2.txt(46B)", "3. 添付3.txt(46B)",
                        "4. 添付4.txt(46B)"});
    }

    @Test
    @DisplayName("No.4-8 削除時に該当する添付ファイルが既に削除済みの場合、正常に完了したとみなし一覧のみを再取得する")
    void case4x8() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        deleteFirstAttachmentInOtherBrowser(3);

        verify.opPressConfirm("4. 一覧の1行目をクリックして削除確認ダイアログの「OK」ボタンを押下する",
                editPage.attachmentRows().first(), DELETE_CONFIRM, true, () -> {
                    editPage.clickAttachmentRow(1);
                    editPage.waitForAttachmentRows(3);
                }, editPage.attachmentList());

        verify.texts("「添付ファイル」の一覧が3件になる", editPage.attachmentRows().locator(".att-name"),
                new String[] {"1. 添付2.txt(46B)", "2. 添付3.txt(46B)", "3. 添付4.txt(46B)"});
        verify.absent("画面に通知・エラーメッセージは表示されない（削除は正常に完了したとみなす）", editPage.attachmentSectionLabel(),
                editPage.errorArea());
    }

    // ---- 5. サーバー・APIサービス処理 ----

    @Test
    @DisplayName("No.5-1 入力値チェックに違反がなければ案件情報が更新され、更新者・更新部署・更新日時が更新される")
    void case5x1() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「件名」を「更新後の件名」に書き換える", () -> editPage.fill(ProjectFormPage.SUBJECT, "更新後の件名"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「ステータス」を「交渉中」に書き換える",
                () -> editPage.select(ProjectFormPage.STATUS, "NEGOTIATING"),
                editPage.input(ProjectFormPage.STATUS));
        verify.op("3. 「備考」を「更新後の備考」に書き換える", () -> editPage.fill(ProjectFormPage.NOTE, "更新後の備考"),
                editPage.input(ProjectFormPage.NOTE));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.value("「件名」に「更新後の件名」が表示される", editPage.input(ProjectFormPage.SUBJECT), "更新後の件名");
        verify.value("「ステータス」に「交渉中」が表示される", editPage.input(ProjectFormPage.STATUS), "NEGOTIATING");
        verify.value("「備考」に「更新後の備考」が表示される", editPage.input(ProjectFormPage.NOTE), "更新後の備考");
        verify.text("「更新者」に「営業 太郎」が表示される", editPage.meta("更新者"), "営業 太郎");
        verify.text("「更新部署」に「営業部」が表示される", editPage.meta("更新部署"), "営業部");
        verify.text("「登録者」は変わらず「営業 太郎」のままである", editPage.meta("登録者"), "営業 太郎");
        verify.text("「登録部署」は変わらず「営業部」のままである", editPage.meta("登録部署"), "営業部");
        verify.text("「登録日時」は変わらず「2026/08/01 10:00:00」のままである", editPage.meta("登録日時"),
                SEEDED_TIMESTAMP);
        verify.absent("各入力項目の直下に入力値チェックのエラーメッセージは表示されない", editPage.form(),
                editPage.visibleFieldErrors());
    }

    @Test
    @DisplayName("No.5-2 内容を変更せずに編集確定ボタンを押した場合でも更新は実施される")
    void case5x2() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.opPress("3. 何も変更せずに「編集確定」を押下する", editPage.submitButton(),
                () -> editPage.pressSubmit());

        verify.value("各項目の値は変わらない（「件名」は「更新対象案件」のまま）", editPage.input(ProjectFormPage.SUBJECT),
                "更新対象案件");
        verify.absent("エラーメッセージは表示されない", editPage.form(),
                editPage.errorArea().or(editPage.visibleFieldErrors()));
    }

    @Test
    @DisplayName("No.5-3 添付ファイルが指定されていた場合、案件情報の更新と同時に添付ファイルが登録される")
    void case5x3() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.opChooseFile("3. 「添付」のファイルピッカーで attach-6mb.bin を指定する", editPage.attachmentDropArea(),
                file, editPage.attachmentDropArea());
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> {
            editPage.pressSubmit();
            editPage.waitForAttachmentRows(5);
        });

        verify.countIs("「添付ファイル」の一覧が5件になる", editPage.attachmentRows(), 5);
        verify.text("5行目に「5. attach-6mb.bin(6MB)」が表示される", editPage.attachmentName(5),
                "5. attach-6mb.bin(6MB)");
        verify.containsText("5行目の登録者・登録部署が「営業 太郎(営業部)」である", editPage.attachmentMeta(5),
                "営業 太郎(営業部)");
        verify.texts("既存の4件（添付1.txt〜添付4.txt）は序数 1〜4 のまま残る",
                editPage.attachmentRows().locator(".att-name"),
                new String[] {"1. 添付1.txt(46B)", "2. 添付2.txt(46B)", "3. 添付3.txt(46B)",
                        "4. 添付4.txt(46B)", "5. attach-6mb.bin(6MB)"});
        verify.sameText("5行目の登録日時は案件情報の更新日時と同値である", editPage.attachmentMeta(5),
                editPage.meta("更新日時"), TIMESTAMP_PATTERN);
    }

    @Test
    @DisplayName("No.5-4 メール送信ありで更新すると、更新した案件情報について営業情報メールが送信される")
    void case5x4() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「件名」を「メール送信あり更新」に書き換える",
                () -> editPage.fill(ProjectFormPage.SUBJECT, "メール送信あり更新"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. メール本文に「更新の本文」を入力する", () -> editPage.fill(ProjectFormPage.MAIL_BODY, "更新の本文"),
                editPage.input(ProjectFormPage.MAIL_BODY));
        verify.checked("4. 「メール送信」がチェックありである", editPage.input(ProjectFormPage.SEND_MAIL));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.mailReceived(mailbox, "【営業情報】(更新) メール送信あり更新", null, "更新の本文");
    }

    @Test
    @DisplayName("No.5-5 メール送信なしで更新すると、営業情報メールは送信されない")
    void case5x5() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「件名」を「メール送信なし更新」に書き換える",
                () -> editPage.fill(ProjectFormPage.SUBJECT, "メール送信なし更新"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.op("4. 「メール送信」のチェックを外す", () -> editPage.uncheck(ProjectFormPage.SEND_MAIL),
                editPage.input(ProjectFormPage.SEND_MAIL));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.value("4. 「件名」が「メール送信なし更新」になる（更新は実施されている）", editPage.input(ProjectFormPage.SUBJECT),
                "メール送信なし更新");
        verify.noMailSent(mailbox);
    }

    @Test
    @DisplayName("No.5-6 読み込み後に他のユーザが同じ案件情報を更新していた場合、排他制御に失敗し更新内容が破棄される")
    void case5x6() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(1);
        verify.op("1. 「件名」を「こちらの更新」に書き換える（まだ編集確定しない）",
                () -> editPage.fill(ProjectFormPage.SUBJECT, "こちらの更新"),
                editPage.input(ProjectFormPage.SUBJECT));

        OtherBrowser other = openOtherBrowser();
        ProjectEditPage otherEdit = new ProjectEditPage(other.page(), baseUrl());
        LoginPage otherLogin = new LoginPage(other.page(), baseUrl());
        otherLogin.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);
        other.verify().opOpen("2. （別ブラウザ）/projects/detail/1 を開く", () -> otherEdit.open(CODE));
        other.verify().op("2. （別ブラウザ）「編集保護」のトグルをオフに切り替える", () -> otherEdit.turnOffEditProtect(),
                otherEdit.editProtectCheckbox());
        other.verify().op("2. （別ブラウザ）「件名」を「先行した更新」に書き換える",
                () -> otherEdit.fill(ProjectFormPage.SUBJECT, "先行した更新"),
                otherEdit.input(ProjectFormPage.SUBJECT));
        other.verify().opPress("2. （別ブラウザ）「編集確定」を押下する", otherEdit.submitButton(),
                () -> otherEdit.pressSubmit());

        verify.opPress("3. 元のウィンドウで「編集確定」を押下する", editPage.submitButton(),
                () -> editPage.pressSubmit());

        verify.text("エラータイトル「案件情報の更新に失敗しました。」が表示される", editPage.errorTitle(), "案件情報の更新に失敗しました。");
        verify.text("エラーメッセージ「他のユーザが案件情報を更新しました。再度更新内容を入力してください。」が" + "表示される",
                editPage.errorMessage(), "他のユーザが案件情報を更新しました。再度更新内容を入力してください。");
        verify.value("「件名」には先行して更新された「先行した更新」が表示される" + "（「こちらの更新」は破棄されている）",
                editPage.input(ProjectFormPage.SUBJECT), "先行した更新");
    }

    @Test
    @DisplayName("No.5-7 メールの送信に失敗しても更新は取り消されず、ユーザに失敗は通知されない")
    void case5x7() {
        verify.opArmFault("1. （障害シーム）「メール送信失敗」を有効化する",
                ProjectRegisterFaultSeamConfig.MAIL_SEND_FAILURE);
        verify.opOpen("2. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("2. 「件名」を「メール失敗更新」に書き換える",
                () -> editPage.fill(ProjectFormPage.SUBJECT, "メール失敗更新"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.checked("3. 「メール送信」がチェックありである", editPage.input(ProjectFormPage.SEND_MAIL));
        verify.opPress("3. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.value("「件名」に「メール失敗更新」が表示される（更新は取り消されていない）", editPage.input(ProjectFormPage.SUBJECT),
                "メール失敗更新");
        verify.faultFired("更新処理がメール送信失敗の経路を通った", ProjectRegisterFaultSeamConfig.MAIL_SEND_FAILURE);
        verify.absent("画面にメール送信の失敗を知らせるメッセージ・エラー表示は現れない", commonHeader.screenTitle(),
                editPage.errorArea().or(editPage.visibleFieldErrors()));
    }

    // ---- 6. 入力チェック ----

    @Test
    @DisplayName("No.6-1 件名が未入力の場合、必須入力に違反する")
    void case6x1() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「件名」を空にする", () -> editPage.fill(ProjectFormPage.SUBJECT, ""),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verifyOnlyFieldError(ProjectFormPage.SUBJECT, "件名", REQUIRED);
        verifyNotUpdated();
    }

    @Test
    @DisplayName("No.6-2 【境界値】件名が201文字の場合、最大文字数(200)に違反する")
    void case6x2() {
        maxLengthCase(ProjectFormPage.SUBJECT, "件名", 200);
    }

    @Test
    @DisplayName("No.6-3 件名に使用できない文字を含む場合、使用文字に違反する")
    void case6x3() {
        illegalCharacterCase(ProjectFormPage.SUBJECT, "件名");
    }

    @Test
    @DisplayName("No.6-4 【境界値】取引先が201文字の場合、最大文字数(200)に違反する")
    void case6x4() {
        maxLengthCase(ProjectFormPage.CLIENT, "取引先", 200);
    }

    @Test
    @DisplayName("No.6-5 取引先に使用できない文字を含む場合、使用文字に違反する")
    void case6x5() {
        illegalCharacterCase(ProjectFormPage.CLIENT, "取引先");
    }

    @Test
    @DisplayName("No.6-6 【境界値】商流が201文字の場合、最大文字数(200)に違反する")
    void case6x6() {
        maxLengthCase(ProjectFormPage.DISTRIBUTION, "商流", 200);
    }

    @Test
    @DisplayName("No.6-7 商流に使用できない文字を含む場合、使用文字に違反する")
    void case6x7() {
        illegalCharacterCase(ProjectFormPage.DISTRIBUTION, "商流");
    }

    @Test
    @DisplayName("No.6-8 【境界値】案件概要が2001文字の場合、最大文字数(2000)に違反する")
    void case6x8() {
        maxLengthCase(ProjectFormPage.OVERVIEW, "案件概要", 2000);
    }

    @Test
    @DisplayName("No.6-9 案件概要に使用できない文字を含む場合、使用文字に違反する")
    void case6x9() {
        illegalCharacterCase(ProjectFormPage.OVERVIEW, "案件概要");
    }

    @Test
    @DisplayName("No.6-10 【境界値】工程が201文字の場合、最大文字数(200)に違反する")
    void case6x10() {
        maxLengthCase(ProjectFormPage.PROCESS, "工程", 200);
    }

    @Test
    @DisplayName("No.6-11 工程に使用できない文字を含む場合、使用文字に違反する")
    void case6x11() {
        illegalCharacterCase(ProjectFormPage.PROCESS, "工程");
    }

    @Test
    @DisplayName("No.6-12 開始日が日付として解釈できない場合、日付形式に違反する")
    void case6x12() {
        invalidDateCase(ProjectFormPage.START_DATE, "開始日", "2026/13/01");
    }

    @Test
    @DisplayName("No.6-13 終了日が日付として解釈できない場合、日付形式に違反する")
    void case6x13() {
        invalidDateCase(ProjectFormPage.END_DATE, "終了日", "2026/02/30");
    }

    @Test
    @DisplayName("No.6-14 終了日が開始日より前の場合、日付下限に違反する")
    void case6x14() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「開始日」に 2026/12/01 を入力する",
                () -> editPage.fill(ProjectFormPage.START_DATE, "2026-12-01"),
                editPage.input(ProjectFormPage.START_DATE));
        verify.op("3. 「終了日」に 2026/11/30 を入力する",
                () -> editPage.fill(ProjectFormPage.END_DATE, "2026-11-30"),
                editPage.input(ProjectFormPage.END_DATE));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verifyOnlyFieldError(ProjectFormPage.END_DATE, "終了日", "開始日以降の日付を入力してください。");
        verifyNotUpdated();
    }

    @Test
    @DisplayName("No.6-15 【境界値】スキルが201文字の場合、最大文字数(200)に違反する")
    void case6x15() {
        maxLengthCase(ProjectFormPage.SKILL, "スキル", 200);
    }

    @Test
    @DisplayName("No.6-16 スキルに使用できない文字を含む場合、使用文字に違反する")
    void case6x16() {
        illegalCharacterCase(ProjectFormPage.SKILL, "スキル");
    }

    @Test
    @DisplayName("No.6-17 【境界値】人数が201文字の場合、最大文字数(200)に違反する")
    void case6x17() {
        maxLengthCase(ProjectFormPage.HEADCOUNT, "人数", 200);
    }

    @Test
    @DisplayName("No.6-18 人数に使用できない文字を含む場合、使用文字に違反する")
    void case6x18() {
        illegalCharacterCase(ProjectFormPage.HEADCOUNT, "人数");
    }

    @Test
    @DisplayName("No.6-19 【境界値】残数が201文字の場合、最大文字数(200)に違反する")
    void case6x19() {
        maxLengthCase(ProjectFormPage.REMAINING_COUNT, "残数", 200);
    }

    @Test
    @DisplayName("No.6-20 残数に使用できない文字を含む場合、使用文字に違反する")
    void case6x20() {
        illegalCharacterCase(ProjectFormPage.REMAINING_COUNT, "残数");
    }

    @Test
    @DisplayName("No.6-21 【境界値】詳細: が201文字の場合、最大文字数(200)に違反する")
    void case6x21() {
        maxLengthCase(ProjectFormPage.BP_DETAIL, "詳細: ", 200);
    }

    @Test
    @DisplayName("No.6-22 詳細: に使用できない文字を含む場合、使用文字に違反する")
    void case6x22() {
        illegalCharacterCase(ProjectFormPage.BP_DETAIL, "詳細: ");
    }

    @Test
    @DisplayName("No.6-23 【境界値】作業場所が201文字の場合、最大文字数(200)に違反する")
    void case6x23() {
        maxLengthCase(ProjectFormPage.WORKPLACE, "作業場所", 200);
    }

    @Test
    @DisplayName("No.6-24 作業場所に使用できない文字を含む場合、使用文字に違反する")
    void case6x24() {
        illegalCharacterCase(ProjectFormPage.WORKPLACE, "作業場所");
    }

    @Test
    @DisplayName("No.6-25 【境界値】見込単価が201文字の場合、最大文字数(200)に違反する")
    void case6x25() {
        maxLengthCase(ProjectFormPage.ESTIMATED_PRICE, "見込単価", 200);
    }

    @Test
    @DisplayName("No.6-26 見込単価に使用できない文字を含む場合、使用文字に違反する")
    void case6x26() {
        illegalCharacterCase(ProjectFormPage.ESTIMATED_PRICE, "見込単価");
    }

    @Test
    @DisplayName("No.6-27 【境界値】備考が4001文字の場合、最大文字数(4000)に違反する")
    void case6x27() {
        maxLengthCase(ProjectFormPage.NOTE, "備考", 4000);
    }

    @Test
    @DisplayName("No.6-28 備考に使用できない文字を含む場合、使用文字に違反する")
    void case6x28() {
        illegalCharacterCase(ProjectFormPage.NOTE, "備考");
    }

    @Test
    @DisplayName("No.6-29 【境界値】メール本文が4001文字の場合、最大文字数(4000)に違反する")
    void case6x29() {
        maxLengthCase(ProjectFormPage.MAIL_BODY, "メール本文", 4000);
    }

    @Test
    @DisplayName("No.6-30 メール本文に使用できない文字を含む場合、使用文字に違反する")
    void case6x30() {
        illegalCharacterCase(ProjectFormPage.MAIL_BODY, "メール本文");
    }

    @Test
    @DisplayName("No.6-31 【境界値】各項目の最大文字数ちょうど・改行を含むテキストエリアは入力値チェックに違反しない")
    void case6x31() {
        String text200 = "あ".repeat(200);

        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「件名」に全角200文字を入力する", () -> editPage.fill(ProjectFormPage.SUBJECT, text200),
                editPage.input(ProjectFormPage.SUBJECT));
        Map<String, String> text200Fields = new LinkedHashMap<>();
        text200Fields.put(ProjectFormPage.CLIENT, "取引先");
        text200Fields.put(ProjectFormPage.DISTRIBUTION, "商流");
        text200Fields.put(ProjectFormPage.PROCESS, "工程");
        text200Fields.put(ProjectFormPage.SKILL, "スキル");
        text200Fields.put(ProjectFormPage.HEADCOUNT, "人数");
        text200Fields.put(ProjectFormPage.REMAINING_COUNT, "残数");
        text200Fields.put(ProjectFormPage.BP_DETAIL, "詳細: ");
        text200Fields.put(ProjectFormPage.WORKPLACE, "作業場所");
        text200Fields.put(ProjectFormPage.ESTIMATED_PRICE, "見込単価");
        text200Fields.forEach((id, label) -> verify.op("3. 「" + label + "」に全角200文字を入力する",
                () -> editPage.fill(id, text200), editPage.input(id)));

        // テキストエリアの改行は送信時に CR+LF へ正規化される（HTML の仕様）。最大文字数ちょうど
        // （改行文字は U+000D・U+000A の 2 文字）になるよう、入力する文字数を組み立てる。
        String overview2000 = "あ".repeat(999) + "\n" + "あ".repeat(999);
        String note4000 = "あ".repeat(1999) + "\n" + "あ".repeat(1999);
        verify.op("4. 「案件概要」に改行を含む全角2000文字を入力する",
                () -> editPage.fill(ProjectFormPage.OVERVIEW, overview2000),
                editPage.input(ProjectFormPage.OVERVIEW));
        verify.op("4. 「備考」に改行を含む全角4000文字を入力する", () -> editPage.fill(ProjectFormPage.NOTE, note4000),
                editPage.input(ProjectFormPage.NOTE));
        verify.op("4. メール本文に改行を含む全角4000文字を入力する",
                () -> editPage.fill(ProjectFormPage.MAIL_BODY, note4000),
                editPage.input(ProjectFormPage.MAIL_BODY));
        verify.op("5. 「開始日」に 2026/09/01 を入力する",
                () -> editPage.fill(ProjectFormPage.START_DATE, "2026-09-01"),
                editPage.input(ProjectFormPage.START_DATE));
        verify.op("5. 「終了日」に 2026/09/01 を入力する（開始日と同日）",
                () -> editPage.fill(ProjectFormPage.END_DATE, "2026-09-01"),
                editPage.input(ProjectFormPage.END_DATE));
        verify.opPress("6. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.absent("各入力項目の直下に入力値チェックのエラーメッセージは表示されない", editPage.form(),
                editPage.visibleFieldErrors());
        verify.value("「件名」に入力した値が表示される", editPage.input(ProjectFormPage.SUBJECT), text200);
        verify.value("「案件概要」に入力した値（改行を含む）が表示される", editPage.input(ProjectFormPage.OVERVIEW),
                overview2000);
        verify.value("「備考」に入力した値（改行を含む）が表示される", editPage.input(ProjectFormPage.NOTE), note4000);
    }

    @Test
    @DisplayName("No.6-32 チェック違反のあった全ての画面項目にエラーメッセージを表示する")
    void case6x32() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「件名」を空にする", () -> editPage.fill(ProjectFormPage.SUBJECT, ""),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「商流」に半角カタカナを含む「ｱｲｳ」を入力する",
                () -> editPage.fill(ProjectFormPage.DISTRIBUTION, HALF_WIDTH_KANA),
                editPage.input(ProjectFormPage.DISTRIBUTION));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.text("「件名」の入力欄の直下に「この項目は入力が必要です。」が表示される",
                editPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);
        verify.text("「商流」の入力欄の直下に「使用不可能な文字が含まれています。」が同時に表示される",
                editPage.fieldError(ProjectFormPage.DISTRIBUTION), ILLEGAL_CHARACTER);
        verify.urlIs("画面遷移せずに案件情報更新画面が再表示される", editPage.url(CODE));
        verifyNotUpdated();
    }

    @Test
    @DisplayName("No.6-33 同じ項目内で複数の入力値チェックに違反した場合、より小さな番号のエラーメッセージ1つのみを表示する")
    void case6x33() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.opBypassRemoveAttribute("3. 「件名」入力欄の maxlength 属性を解除する",
                editPage.input(ProjectFormPage.SUBJECT), "maxlength");
        verify.op("4. 「件名」に、半角カタカナ「ｱ」を含む201文字を入力する",
                () -> editPage.fill(ProjectFormPage.SUBJECT, "ｱ" + "a".repeat(200)),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.opPress("5. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.text("「件名」の入力欄の直下に表示されるのは「200文字以内で入力してください。」のみである" + "（「使用不可能な文字が含まれています。」は表示されない）",
                editPage.fieldError(ProjectFormPage.SUBJECT), "200文字以内で入力してください。");
        verify.urlIs("画面遷移せずに案件情報更新画面が再表示される", editPage.url(CODE));
        verifyNotUpdated();
    }

    @Test
    @DisplayName("No.6-34 入力値チェック違反後に再度編集確定すると、既存のエラーメッセージが削除されてから再チェックされる")
    void case6x34() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(1);
        verify.op("1. 「件名」を空にする", () -> editPage.fill(ProjectFormPage.SUBJECT, ""),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.opPress("1. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());
        verify.text("2. 「件名」の入力欄の直下に「この項目は入力が必要です。」が表示される",
                editPage.fieldError(ProjectFormPage.SUBJECT), REQUIRED);

        verify.op("3. 「編集保護」のトグルをオフに切り替える", () -> editPage.turnOffEditProtect(),
                editPage.editProtectCheckbox());
        verify.op("3. 「件名」に「再チェック確認」を入力する", () -> editPage.fill(ProjectFormPage.SUBJECT, "再チェック確認"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.op("3. 「商流」に半角カタカナを含む「ｱｲｳ」を入力する",
                () -> editPage.fill(ProjectFormPage.DISTRIBUTION, HALF_WIDTH_KANA),
                editPage.input(ProjectFormPage.DISTRIBUTION));
        verify.transitionClearedByPress("「件名」の直下にエラーメッセージが表示されている",
                editPage.fieldError(ProjectFormPage.SUBJECT), "3. 「編集確定」を押下する",
                editPage.submitButton(), () -> editPage.pressSubmit(),
                editPage.fieldError(ProjectFormPage.DISTRIBUTION), "「件名」の直下のエラーメッセージが消える");

        verify.text("「商流」の入力欄の直下だけに「使用不可能な文字が含まれています。」が表示される",
                editPage.fieldError(ProjectFormPage.DISTRIBUTION), ILLEGAL_CHARACTER);
        verify.countIs("表示されている入力値チェックのエラーメッセージは1件のみである", editPage.visibleFieldErrors(), 1);
        verifyNotUpdated();
    }

    @Test
    @DisplayName("No.6-35 ステータスに空値を送った場合、必須入力に違反する")
    void case6x35() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「ステータス」の値を空にする（選択肢に空欄が無いため画面操作では選べない）", () -> editPage.forceStatusValue(""),
                editPage.input(ProjectFormPage.STATUS));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.urlIs("エラー画面へは遷移せず、画面遷移せずに案件情報更新画面が再表示される", editPage.url(CODE));
        verifyOnlyFieldError(ProjectFormPage.STATUS, "ステータス", REQUIRED);
        verifyNotUpdated();
    }

    // ---- 7. 認証・認可／サーバー処理の異常分岐 ----

    @Test
    @DisplayName("No.7-1 更新時にログインの有効期限が切れている場合、入力値と指定した添付ファイルは破棄され更新は行われない")
    void case7x1() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(1);
        verify.op("1. 「件名」を「失効時更新」に書き換える", () -> editPage.fill(ProjectFormPage.SUBJECT, "失効時更新"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.opChooseFile("1. 「添付」で attach-6mb.bin を指定する", editPage.attachmentDropArea(), file,
                editPage.attachmentDropArea());

        clearSession(2);

        verify.opPress("3. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());
        verify.visible("3. 案件情報更新画面は再表示されずログイン画面が表示される", loginPage.form());

        signInAgain(4);
        verify.urlIs("4. 案件情報更新画面ではなく案件情報一覧画面 /projects へ遷移する", listPage.url());

        verify.opOpen("5. 案件情報更新画面を開く", () -> openDetailWithAttachments(4));
        verify.value("5. 「件名」は「更新対象案件」のままである", editPage.input(ProjectFormPage.SUBJECT), "更新対象案件");
        verify.countIs("5. 「添付ファイル」の一覧も4件のままである", editPage.attachmentRows(), 4);
    }

    @Test
    @DisplayName("No.7-2 ダウンロード時にログインの有効期限が切れている場合、ダウンロードは実施されず案件情報一覧画面へ遷移する")
    void case7x2() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く（編集保護はオン）", () -> openDetailWithAttachments(4));
        clearSession(2);

        verify.noDownload("3・4. ファイルのダウンロードが発生していない", () -> {
            verify.op("3. 「添付ファイル」の一覧の1行目をクリックする", () -> editPage.clickAttachmentRow(1),
                    loginPage.form());
            verify.visible("3. ファイルはダウンロードされず、ログイン画面が表示される", loginPage.form());
            signInAgain(4);
        });

        verify.urlIs("4. 案件情報更新画面ではなく案件情報一覧画面 /projects へ遷移する", listPage.url());
    }

    @Test
    @DisplayName("No.7-3 削除時にログインの有効期限が切れている場合、削除は実施されず案件情報一覧画面へ遷移する")
    void case7x3() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        clearSession(3);

        verify.opPressConfirm("4. 一覧の1行目をクリックして削除確認ダイアログの「OK」ボタンを押下する",
                editPage.attachmentRows().first(), DELETE_CONFIRM, true,
                () -> editPage.clickAttachmentRow(1), loginPage.form());
        verify.visible("4. ログイン画面が表示される", loginPage.form());

        signInAgain(5);
        verify.urlIs("5. 案件情報一覧画面 /projects へ遷移する", listPage.url());

        verify.opOpen("6. 案件情報更新画面を開く", () -> openDetailWithAttachments(4));
        verify.countIs("6. 「添付ファイル」の一覧は4件のままであり、削除は実施されていない", editPage.attachmentRows(), 4);
    }

    @Test
    @DisplayName("No.7-4 添付ファイルの削除でデータベース例外が発生した場合、削除前の状態に戻りエラー画面に【サーバー内部エラー】が表示される")
    void case7x4() {
        insertAttachments(unitData(SEED_ATTACH_4));
        verify.opArmFault("1. （障害シーム）「データベース例外（添付ファイル削除）」を有効化する",
                ProjectUpdateFaultSeamConfig.DB_EXCEPTION_ON_ATTACHMENT_DELETE);
        verify.opOpen("2. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);

        verify.opPressConfirm("3. 一覧の1行目をクリックして削除確認ダイアログの「OK」ボタンを押下する",
                editPage.attachmentRows().first(), DELETE_CONFIRM, true, () -> {
                    editPage.clickAttachmentRow(1);
                    page.waitForURL(url -> url.contains("/error"));
                }, errorPage.card());

        verify.text("3. エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("3. エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.faultFired("添付ファイルの削除がデータベース例外の経路を通った",
                ProjectUpdateFaultSeamConfig.DB_EXCEPTION_ON_ATTACHMENT_DELETE);

        verify.opOpen("4. /projects/detail/1 を開く", () -> openDetail());
        verify.countIs("4. 「添付ファイル」の一覧が4件のままであり、削除実施前の状態に戻っている", editPage.attachmentRows(), 4);
    }

    @Test
    @DisplayName("No.7-5 更新処理でデータベース例外が発生した場合、更新前の状態に戻りエラー画面に【サーバー内部エラー】が表示される")
    void case7x5() {
        insertAttachments(unitData(SEED_ATTACH_4));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opArmFault("1. （障害シーム）「データベース例外（案件更新）」を有効化する",
                ProjectUpdateFaultSeamConfig.DB_EXCEPTION_ON_UPDATE);
        verify.opOpen("2. /projects/detail/1 を開く", () -> openDetailWithAttachments(4));
        unprotect(2);
        verify.op("2. 「件名」を「例外時更新」に書き換える", () -> editPage.fill(ProjectFormPage.SUBJECT, "例外時更新"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.opChooseFile("2. 「添付」で attach-6mb.bin を指定する", editPage.attachmentDropArea(), file,
                editPage.attachmentDropArea());
        verify.opPress("3. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.text("3. エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("3. エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.faultFired("更新処理がデータベース例外の経路を通った",
                ProjectUpdateFaultSeamConfig.DB_EXCEPTION_ON_UPDATE);

        verify.opOpen("4. /projects/detail/1 を開く", () -> openDetail());
        verify.value("4. 「件名」が「更新対象案件」のままである", editPage.input(ProjectFormPage.SUBJECT), "更新対象案件");
        verify.countIs("4. 「添付ファイル」の一覧も4件のままである", editPage.attachmentRows(), 4);
        verify.text("4. 更新日時も 2026/08/01 10:00:00 のままである", editPage.meta("更新日時"), SEEDED_TIMESTAMP);
    }

    @Test
    @DisplayName("No.7-6 区分項目に選択肢に無い値を受けた場合、入力値チェックとして扱わずサーバー内部エラーになる")
    void case7x6() {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(1);
        verify.op("1. 「件名」を「区分値改ざん」に書き換える", () -> editPage.fill(ProjectFormPage.SUBJECT, "区分値改ざん"),
                editPage.input(ProjectFormPage.SUBJECT));
        verify.op("2. 「ステータス」の選択肢に無い値「UNKNOWN」を選択済みの値として設定する",
                () -> editPage.forceStatusValue("UNKNOWN"), editPage.input(ProjectFormPage.STATUS));
        verify.opPress("3. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.text("エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.absent("案件情報更新画面は再表示されず、入力値チェックのエラーメッセージも表示されない", errorPage.card(),
                editPage.form().or(editPage.visibleFieldErrors()));

        verify.opOpen("案件情報更新画面を開いて更新されていないことを確認する", () -> editPage.open(CODE));
        verify.text("案件情報は更新されていない（更新日時が 2026/08/01 10:00:00 のままである）", editPage.meta("更新日時"),
                SEEDED_TIMESTAMP);
    }

    @Test
    @DisplayName("No.7-7 クライアント側のファイル形式チェックを迂回しても、サーバ側の防御的な検証で容量違反が検出される")
    void case7x7() {
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB_PLUS1, 6291457);
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(1);
        verify.op("2. ファイル選択イベントのファイル形式チェックを迂回して attach-6mb-plus1.bin を" + "添付として送信させる",
                () -> editPage.attachBypassingClientCheck(file));
        verify.machineEquals("手順2で attach-6mb-plus1.bin が添付として設定されている", ATTACH_6MB_PLUS1,
                editPage.attachedFileName());
        verify.opPress("3. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.urlIs("更新は実施されず、案件情報更新画面が再表示される", editPage.url(CODE));
        verify.text("「添付」の項目に「添付ファイルの容量が大きすぎます(6MBまで)」が表示される", editPage.attachmentError(),
                TOO_LARGE);
        verify.text("案件情報は更新されていない（更新日時が 2026/08/01 10:00:00 のままである）", editPage.meta("更新日時"),
                SEEDED_TIMESTAMP);
    }

    @Test
    @DisplayName("No.7-8 登録済みの添付ファイルが5件のときに添付を指定して更新すると、サーバ側の防御的な検証で件数違反が検出される")
    void case7x8() {
        insertAttachments(unitData(SEED_ATTACH_5));
        final Path file = E2eTestFiles.ofSize(ATTACH_6MB, 6291456);
        verify.opOpen("1. /projects/detail/1 を開く", () -> openDetailWithAttachments(5));
        unprotect(1);
        verify.op("2. ファイル選択イベントのファイル形式チェックを迂回して attach-6mb.bin を" + "添付として送信させる",
                () -> editPage.attachBypassingClientCheck(file));
        verify.machineEquals("手順2で attach-6mb.bin が添付として設定されている", ATTACH_6MB,
                editPage.attachedFileName());
        verify.opPress("3. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verify.urlIs("更新は実施されず、案件情報更新画面が再表示される", editPage.url(CODE));
        verify.text("「添付」の項目に「登録できる添付ファイルは5件までです。」が表示される", editPage.attachmentError(), TOO_MANY);
        verify.text("案件情報は更新されていない（更新日時が 2026/08/01 10:00:00 のままである）", editPage.meta("更新日時"),
                SEEDED_TIMESTAMP);
    }

    @Test
    @DisplayName("No.7-9 初期表示で管理コード未指定・未登録以外のエラーが発生した場合、エラー画面に【サーバー内部エラー】が表示される")
    void case7x9() {
        verify.opArmFault("1. （障害シーム）「データベース例外（案件取得）」を有効化する",
                ProjectUpdateFaultSeamConfig.DB_EXCEPTION_ON_FIND);
        verify.opOpen("2. /projects/detail/1 を開く", () -> editPage.open(CODE));

        verify.text("エラータイトル「An Error Occurred」が表示される", errorPage.errorTitle(),
                "An Error Occurred");
        verify.text("エラー内容「エラーが発生しました。」が表示される", errorPage.errorMessage(), "エラーが発生しました。");
        verify.faultFired("初期表示の案件情報取得がデータベース例外の経路を通った",
                ProjectUpdateFaultSeamConfig.DB_EXCEPTION_ON_FIND);
        verify.absent("案件情報更新画面は表示されず、「存在しない案件情報です」もページ未検出エラーも" + "表示されない", errorPage.card(),
                commonHeader.screenTitleOf(ProjectEditPage.SCREEN_TITLE));
    }

    // ---- 共通シナリオ・検証ヘルパー ----

    /** 「編集保護」のトグルをオフに切り替える（手順番号を与えてキャプションに反映する）。 */
    private void unprotect(int step) {
        verify.op(step + ". 「編集保護」のトグルをオフに切り替える", () -> editPage.turnOffEditProtect(),
                editPage.editProtectCheckbox());
    }

    /** 案件情報更新画面を開き、添付ファイル一覧の取得（API）が落ち着くまで待つ（件数は検証側で確かめる）。 */
    private void openDetail() {
        editPage.open(CODE);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /** 案件情報更新画面を開き、添付ファイル一覧の描画完了まで待つ。 */
    private void openDetailWithAttachments(int expectedRows) {
        editPage.open(CODE);
        editPage.waitForAttachmentRows(expectedRows);
    }

    /** セッション Cookie を削除してログインの有効期限が切れた状態にする。 */
    private void clearSession(int step) {
        verify.op(step + ". ブラウザが保持するセッションCookieを削除する" + "（ログインの有効期限が切れた状態にする）",
                () -> page.context().clearCookies());
        verify.machineEquals("手順" + step + "の後、セッションCookie（JSESSIONID）は 0件である", "0",
                String.valueOf(page.context().cookies().stream()
                        .filter(cookie -> "JSESSIONID".equals(cookie.name)).count()));
    }

    /** 表示されたログイン画面で再ログインする。 */
    private void signInAgain(int step) {
        verify.op(step + ". ユーザID に SM0001 を入力する", () -> loginPage.fillUserId(GENERAL_USER),
                loginPage.userIdInput());
        verify.op(step + ". パスワードを入力する", () -> loginPage.fillPassword(PASSWORD),
                loginPage.passwordInput());
        verify.opPress(step + ". 「ログイン」を押下する", loginPage.loginButton(), () -> {
            loginPage.pressLogin();
            listPage.waitForListLoaded();
        });
    }

    /** 別ブラウザ（同じユーザ）で添付ファイルの1行目を削除する。 */
    private void deleteFirstAttachmentInOtherBrowser(int step) {
        OtherBrowser other = openOtherBrowser();
        ProjectEditPage otherEdit = new ProjectEditPage(other.page(), baseUrl());
        LoginPage otherLogin = new LoginPage(other.page(), baseUrl());
        otherLogin.signIn(GENERAL_USER, PASSWORD, ProjectListPage.PATH);
        other.verify().opOpen(step + ". （別ブラウザ）/projects/detail/1 を開く", () -> {
            otherEdit.open(CODE);
            otherEdit.waitForAttachmentRows(4);
        });
        other.verify().op(step + ". （別ブラウザ）「編集保護」のトグルをオフに切り替える",
                () -> otherEdit.turnOffEditProtect(), otherEdit.editProtectCheckbox());
        other.verify().opPressConfirm(step + ". （別ブラウザ）1行目「添付1.txt」を削除する",
                otherEdit.attachmentRows().first(), DELETE_CONFIRM, true, () -> {
                    otherEdit.clickAttachmentRow(1);
                    otherEdit.waitForAttachmentRows(3);
                }, otherEdit.attachmentList());
        other.context().close();
    }

    /** 最大文字数違反の共通シナリオ（maxlength を解除して超過文字数を入力し編集確定する）。 */
    private void maxLengthCase(String inputId, String label, int max) {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.opBypassRemoveAttribute("3. 「" + label + "」入力欄の maxlength 属性を解除する",
                editPage.input(inputId), "maxlength");
        verify.op("4. 「" + label + "」に半角英字" + (max + 1) + "文字（a を" + (max + 1) + "回）を入力する",
                () -> editPage.fill(inputId, "a".repeat(max + 1)), editPage.input(inputId));
        verify.opPress("5. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verifyOnlyFieldError(inputId, label, max + "文字以内で入力してください。");
        verifyNotUpdated();
    }

    /** 使用文字違反の共通シナリオ（半角カタカナを含む値を入力して編集確定する）。 */
    private void illegalCharacterCase(String inputId, String label) {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.op("3. 「" + label + "」に半角カタカナを含む「ｱｲｳ」を入力する",
                () -> editPage.fill(inputId, HALF_WIDTH_KANA), editPage.input(inputId));
        verify.opPress("4. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verifyOnlyFieldError(inputId, label, ILLEGAL_CHARACTER);
        verifyNotUpdated();
    }

    /** 日付形式違反の共通シナリオ（type を date から text へ変更して不正な日付を入力する）。 */
    private void invalidDateCase(String inputId, String label, String value) {
        verify.opOpen("1. /projects/detail/1 を開く", () -> editPage.open(CODE));
        unprotect(2);
        verify.opBypassSetAttribute("3. 「" + label + "」入力欄の type 属性を date から text へ変更する",
                editPage.input(inputId), "type", "text");
        verify.op("4. 「" + label + "」に「" + value + "」を入力する", () -> editPage.fill(inputId, value),
                editPage.input(inputId));
        verify.opPress("5. 「編集確定」を押下する", editPage.submitButton(), () -> editPage.pressSubmit());

        verifyOnlyFieldError(inputId, label, "無効な日付です。");
        verifyNotUpdated();
    }

    /** 指定項目にだけエラーメッセージが表示されていることを検証する。 */
    private void verifyOnlyFieldError(String inputId, String label, String message) {
        verify.text("「" + label + "」の入力欄の直下に「" + message + "」が表示される", editPage.fieldError(inputId),
                message);
        verify.countIs("表示されている入力値チェックのエラーメッセージは1件のみで、" + "他の画面項目の直下には表示されない",
                editPage.visibleFieldErrors(), 1);
        verify.urlIs("更新は実施されず、画面遷移せずに案件情報更新画面が再表示される", editPage.url(CODE));
    }

    /** 案件情報が更新されていないこと（更新日時がシードのまま）を検証する。 */
    private void verifyNotUpdated() {
        verify.text("案件情報は更新されていない（更新日時が 2026/08/01 10:00:00 のままである）", editPage.meta("更新日時"),
                SEEDED_TIMESTAMP);
    }

    /** 取引先のオートコンプリート候補の期待値（会社名を会社IDの昇順）。 */
    private List<String> expectedClientOptions() {
        List<String> expected = new ArrayList<>();
        final Path csv = E2eSpecLayout.baselineDataDir().resolve("companies.csv");
        List<List<String>> records = E2eSeedSupport.parseCsv(E2eSeedSupport.readContent(csv));
        records.stream().skip(1)
                .sorted((a, b) -> Integer.compare(Integer.parseInt(a.get(0).trim()),
                        Integer.parseInt(b.get(0).trim())))
                .forEach(row -> expected.add(row.get(1)));
        return expected;
    }
}
