package testgen.e2e.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.options.RecordVideoSize;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.web.server.LocalServerPort;
import testgen.e2e.support.evidence.E2eFrameService;
import testgen.e2e.support.evidence.E2eLogCollector;
import testgen.e2e.support.evidence.E2eStepRecorder;
import testgen.e2e.support.evidence.E2eVerify;

/**
 * E2E テストの基底クラス。
 *
 * <p>
 * {@code @SpringBootTest(RANDOM_PORT)}＋Testcontainers で本番同等のアプリを同一プロセス起動し、実ブラウザ
 * （Playwright）でそのポートを操作する。各テスト前にベースラインを再投入して冪等性を担保し、テストごとに新しい {@link BrowserContext}
 * を用いてセッション（Cookie）を分離する。
 *
 * <p>
 * キットはアプリに依存しない。アプリ起動の注釈（{@code @SpringBootTest(classes = <アプリ起動クラス>,
 * webEnvironment = RANDOM_PORT)}・{@code @Import(<Testcontainers 設定クラス>)}・
 * {@code @ActiveProfiles("e2e")}）は本クラスには付けず、プロジェクト側の**糊クラス**（本クラスを継承する 抽象クラス。testgen-exec
 * が転写時に生成する）が付与する。テストは糊クラスを継承する。E2E テストは アプリのパッケージ外（{@code testgen.e2e}
 * 配下）に置くため、{@code @SpringBootTest} の {@code classes} は糊クラスで必ず明示する。
 *
 * <p>
 * 証跡モード（{@code e2e.evidence=true}／{@code e2eEvidence} タスク）では、ケースごとにステップフレーム群・
 * steps.json・動画・トレースを機能の {@code エビデンス/} へ出力する。通常の {@code e2eTest} は証跡を取らず高速に実行する。操作ディレイは
 * {@code e2e.slow-mo-ms} で調整する。
 *
 * <p>
 * 対象は テスト実施単位（{@code e2e.target}。必ず指定する）に閉じる。実施単位の中で機能を絞って実行するときは {@code e2e.features}
 * を指定する。選択外の機能のテストクラスは、テストインスタンス生成・コンテキスト起動より前に無効化され、
 * アプリ・データベースコンテナを起動せずにスキップされる（{@link E2eFeature} 注釈＋{@link E2eFeatureSelection}）。
 */
@Tag("e2e")
@ExtendWith(E2eFeatureSelection.class)
@ExtendWith(E2eResultRecorder.class)
public abstract class E2eBaseTest {

    /**
     * 証跡モードで各ページ先頭に「アドレスバー風の情報バー」を挿入する init script。
     *
     * <p>
     * Playwright の録画はページ表示領域のみ（ブラウザのアドレスバー等クロームは録画外）のため、現在のパス（URL）を
     * 画面内に大きく表示して目視判定できるようにする。あわせて、実際に打鍵した入力欄の値（マスクされるパスワードを 含む）を表示し、動画で入力内容を確認できるようにする。値は開発用ダミーのみ。
     *
     * <p>
     * バーは画面上部に固定表示し、アプリの表示領域（{@code .app-shell}／{@code .center-screen}）をバー高さ分だけ
     * 下へオフセットするため、ヘッダー等の画面端 UI を覆い隠さない（バーは常に前面）。表示は実際に {@code input} が
     * 発火した欄だけに限定し、検索フォーム等の既定値は出さない。{@code pointer-events:none} で操作も妨げない。
     *
     * <p>
     * あわせて操作の可視化として、マウス位置に追従するドットと、クリック位置に広がって消える波紋を表示する。
     * クリック自体は画面に痕跡を残さず動画で「どこを押したか」が判別できないための補いで、シナリオ側の対応は 不要（全操作へ自動適用）。いずれも
     * {@code pointer-events:none} で操作に干渉しない。
     */
    // スクリプト内の __SHELL__／__CENTER__ は「バー高さぶん下へ逃がすアプリ側レイアウトのルート要素」の
    // セレクタトークン（{@link #overlayShellSelector}／{@link #overlayCenterSelector} で差し込む。
    // 該当要素が無ければ offset は何もしない）。
    private static final String OVERLAY_SCRIPT = """
            (() => {
              const ID = '__e2e_overlay__';
              const typed = new Map();
              let stepText = '';
              const labelFor = (el) => {
                if (el.id) {
                  const l = document.querySelector('label[for="' + el.id + '"]');
                  if (l && l.textContent.trim()) return l.textContent.trim();
                }
                return el.getAttribute('name') || el.id || el.type || 'field';
              };
              const skip = ['submit', 'button', 'hidden'];
              const collect = (el) => {
                if (!el || !('value' in el)) return;
                const type = (el.type || '').toLowerCase();
                if (skip.includes(type)) return;
                if (type === 'checkbox' || type === 'radio') {
                  typed.set(el, labelFor(el) + ': ' + (el.checked ? 'ON' : 'OFF'));
                  return;
                }
                if (type === 'file') {
                  const names = el.files && el.files.length
                    ? Array.from(el.files).map((f) => f.name).join(', ') : '';
                  if (names) { typed.set(el, labelFor(el) + ': ' + names); }
                  else { typed.delete(el); }
                  return;
                }
                if (el.value == null || el.value === '') {
                  typed.delete(el);
                  return;
                }
                typed.set(el, labelFor(el) + ': ' + el.value);
              };
              const offset = (h) => {
                const shell = document.querySelector('__SHELL__');
                if (shell) shell.style.top = h + 'px';
                const center = document.querySelector('__CENTER__');
                if (center) {
                  center.style.boxSizing = 'border-box';
                  center.style.paddingTop = h + 'px';
                }
                // 既知のアプリコンテナが無い外部UI（MailHog等）ではレイアウトへ介入しない。
                // body への padding は外部UI自身の固定ヘッダーと内容の重なりを生み、
                // クリックのヒットテストを恒常的に阻害する（バーが上部を覆うのは
                // pointer-events:none のため操作に無害で、主要な確認対象は下部に写る）。
              };
              const draw = () => {
                if (!document.body) return;
                let box = document.getElementById(ID);
                if (!box) {
                  box = document.createElement('div');
                  box.id = ID;
                  box.style.cssText = 'position:fixed;left:0;right:0;top:0;'
                    + 'z-index:2147483647;pointer-events:none;background:#0f172a;'
                    + 'color:#fff;padding:6px 16px;white-space:nowrap;overflow:hidden;'
                    + 'text-overflow:ellipsis;font:700 16px/1.6 ui-monospace,monospace';
                  const url = document.createElement('div');
                  url.id = ID + '_url';
                  const inp = document.createElement('div');
                  inp.id = ID + '_inp';
                  inp.style.cssText = 'font-size:14px;color:#6ee7b7;'
                    + 'font-weight:600;min-height:1.4em';
                  const stp = document.createElement('div');
                  stp.id = ID + '_stp';
                  stp.style.cssText = 'font-size:14px;color:#fbbf24;'
                    + 'font-weight:600;min-height:1.4em';
                  box.appendChild(url);
                  box.appendChild(inp);
                  box.appendChild(stp);
                  document.body.appendChild(box);
                }
                document.getElementById(ID + '_url').textContent =
                  'URL  ' + location.pathname + location.search;
                const values = Array.from(typed.values()).join('　／　');
                document.getElementById(ID + '_inp').textContent =
                  values ? ('入力  ' + values) : '';
                document.getElementById(ID + '_stp').textContent = stepText;
                offset(box.offsetHeight);
              };
              const cursor = () => {
                let dot = document.getElementById(ID + '_cur');
                if (!dot) {
                  dot = document.createElement('div');
                  dot.id = ID + '_cur';
                  dot.style.cssText = 'position:fixed;width:14px;height:14px;'
                    + 'margin:-7px 0 0 -7px;border-radius:50%;display:none;'
                    + 'background:rgba(239,68,68,.85);border:2px solid #fff;'
                    + 'z-index:2147483646;pointer-events:none';
                  document.body.appendChild(dot);
                }
                return dot;
              };
              const ripple = (x, y) => {
                const r = document.createElement('div');
                r.style.cssText = 'position:fixed;left:' + x + 'px;top:' + y + 'px;'
                  + 'width:56px;height:56px;margin:-28px 0 0 -28px;border-radius:50%;'
                  + 'border:3px solid #ef4444;background:rgba(239,68,68,.3);'
                  + 'z-index:2147483646;pointer-events:none;'
                  + 'transition:transform .5s ease-out,opacity .5s ease-out;'
                  + 'transform:scale(.3);opacity:1';
                document.body.appendChild(r);
                requestAnimationFrame(() => {
                  r.style.transform = 'scale(1.6)';
                  r.style.opacity = '0';
                });
                setTimeout(() => r.remove(), 700);
              };
              const boot = () => {
                draw();
                document.addEventListener('input', (e) => {
                  collect(e.target);
                  draw();
                }, true);
                document.addEventListener('change', (e) => {
                  collect(e.target);
                  draw();
                }, true);
                document.addEventListener('mousemove', (e) => {
                  const dot = cursor();
                  dot.style.display = 'block';
                  dot.style.left = e.clientX + 'px';
                  dot.style.top = e.clientY + 'px';
                }, true);
                document.addEventListener('mousedown', (e) => {
                  ripple(e.clientX, e.clientY);
                }, true);
              };
              window.__e2eSetStep = (t) => { stepText = t || ''; draw(); };
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', boot);
              } else {
                boot();
              }
            })();
            """;

    private static Playwright playwright;
    private static Browser browser;

    /** テストごとに生成するブラウザコンテキスト（セッション分離の単位）。 */
    protected BrowserContext context;

    /** 操作対象のページ。 */
    protected Page page;

    /**
     * 検証部品ファサード。テストは本フィールド経由で操作フレーム・検証を記述する。
     *
     * <p>
     * 検証（アサーション）は証跡モードでない実行でも同一に働き、フレーム撮影・steps.json 記録だけが 証跡モード限定になる。
     */
    protected E2eVerify verify;

    private E2eStepRecorder stepRecorder;
    private E2eLogCollector logCollector;

    /** ケース中に開いた別ブラウザのコンテキスト（ケース終了時に全て閉じる）。 */
    private final List<BrowserContext> otherContexts = new ArrayList<>();

    /** 同一コンテキストで開いた別タブ（動画は主ブラウザのみ残すため、終了時に破棄する）。 */
    private final List<Page> otherTabs = new ArrayList<>();

    private Path evidenceDir;
    private String currentCaseSlug;

    @LocalServerPort
    private int port;

    /**
     * 証跡の出力先を決める機能名。テスト実施単位配下の {@code <実施単位>/<機能名>/エビデンス/} に証跡を出力する（{@link E2eSpecLayout}）。
     *
     * <p>
     * 正本はテストクラスの {@link E2eFeature} 注釈。機能絞り込み（{@link E2eFeatureSelection}）が
     * コンテキスト起動前に参照するため、メソッドのオーバーライドではなく注釈で宣言する。
     *
     * @return 機能名（機能フォルダ名に一致）
     */
    protected final String featureName() {
        E2eFeature feature = getClass().getAnnotation(E2eFeature.class);
        if (feature == null) {
            throw new IllegalStateException(
                    "テストクラスに @E2eFeature（機能名）が宣言されていません: " + getClass().getName());
        }
        return feature.value();
    }

    /**
     * 自アプリのルートパッケージを返す。証跡ログの取捨（この配下は全レベル、その他は WARN 以上のみ）に 使う。糊クラスが実装する。
     *
     * @return アプリのルートパッケージ（例: アプリ起動クラスのパッケージ）
     */
    protected abstract String appRootPackage();

    /**
     * 証跡バーの高さぶん下へ逃がすアプリ側レイアウトのルート要素（CSS セレクタ）。該当要素が無ければ
     * レイアウトへ介入しないため、既定のままでも無害。アプリのレイアウトに合わせる場合は糊クラスで 上書きする。
     *
     * @return シェル要素のセレクタ（既定 {@code .app-shell}）
     */
    protected String overlayShellSelector() {
        return ".app-shell";
    }

    /**
     * {@link #overlayShellSelector()} と対になる中央領域のセレクタ。
     *
     * @return 中央領域のセレクタ（既定 {@code .center-screen}）
     */
    protected String overlayCenterSelector() {
        return ".center-screen";
    }

    private String overlayScript() {
        return OVERLAY_SCRIPT.replace("__SHELL__", overlayShellSelector()).replace("__CENTER__",
                overlayCenterSelector());
    }

    /**
     * ブラウザコンテキストのロケール。日付入力（{@code input[type=date]}）の表示形式や
     * {@code Intl} 系 API の出力がロケールに依存するため、証跡の表示を決定的にする目的で固定する。
     * プロジェクトの前提ロケールが異なる場合は糊クラスで上書きする。
     *
     * @return ロケール（既定 {@code ja-JP}）
     */
    protected String contextLocale() {
        return "ja-JP";
    }

    /**
     * ブラウザコンテキストのタイムゾーン。画面のクライアント側日時処理を決定的にする目的で固定する。
     * プロジェクトの前提タイムゾーンが異なる場合は糊クラスで上書きする。
     *
     * @return タイムゾーン ID（既定 {@code Asia/Tokyo}）
     */
    protected String contextTimezoneId() {
        return "Asia/Tokyo";
    }

    /** 主ブラウザ・別ブラウザで共通のコンテキスト設定（ビューポート・ロケール・タイムゾーン）。 */
    private Browser.NewContextOptions newContextOptions() {
        return new Browser.NewContextOptions().setViewportSize(1280, 800)
                .setLocale(contextLocale()).setTimezoneId(contextTimezoneId());
    }

    /**
     * アプリ表を初期化してベースライン（マスタ／参照系）を再投入する。各テストの前に呼ばれ、実行の 冪等性を担保する。実装はプロジェクト側のシーダーで行う（キットには含まれない）:
     * {@code TRUNCATE ... RESTART IDENTITY CASCADE} → ベースライン正本 CSV の投入（明示 ID・
     * {@code OVERRIDING SYSTEM VALUE}）→ {@link E2eSeedSupport#moveSequencePastSeedBand} による
     * 自動採番の帯域移動。CSV の読取・検証は {@link E2eSeedSupport} を用いる。
     */
    protected abstract void resetBaseline();

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        double slowMo = Double.parseDouble(System.getProperty("e2e.slow-mo-ms", "0"));
        browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true).setSlowMo(slowMo));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void setUpEach(TestInfo testInfo) {
        // 機能絞り込み（-Pe2e.features）の選択外クラスは E2eFeatureSelection がコンテキスト起動前に
        // 無効化しているため、ここに到達するのは実行対象の機能のみ。
        // 障害シームはベースラインと同時に全解除する（前ケースの取りこぼしを持ち越さない）。
        E2eFaultSeams.reset();
        resetBaseline();
        Browser.NewContextOptions options = newContextOptions();
        if (E2eEvidence.enabled()) {
            currentCaseSlug = E2eEvidence.slug(testInfo.getDisplayName());
            E2eSpecLayout.requireFeature(featureName());
            evidenceDir = E2eSpecLayout.evidenceDir(featureName());
            createEvidenceDir();
            options.setRecordVideoDir(evidenceDir)
                    .setRecordVideoSize(new RecordVideoSize(1280, 800));
            E2eResultRecorder.registerEvidenceDir(getClass().getName(), evidenceDir);
        }
        context = browser.newContext(options);
        if (E2eEvidence.enabled()) {
            context.addInitScript(overlayScript());
            context.tracing().start(new Tracing.StartOptions().setScreenshots(true)
                    .setSnapshots(true).setSources(true));
        }
        page = context.newPage();
        if (E2eEvidence.enabled()) {
            stepRecorder = new E2eStepRecorder(evidenceDir, featureName(),
                    System.getProperty("e2e.target", ""), testInfo.getDisplayName());
            stepRecorder.deleteExistingFrames();
            verify = new E2eVerify(page, new E2eFrameService(page, evidenceDir), stepRecorder);
            // アプリログ（logback）を捕捉し、証跡ログ（steps.json の logs）に残す。
            logCollector = new E2eLogCollector(appRootPackage());
            logCollector.attach();
            E2eStepRecorder recorder = stepRecorder;
            String base = baseUrl();
            // 画面遷移（リダイレクト等、見た目で確認しにくい事実）を証跡ログへ記録する。
            page.onFrameNavigated(frame -> {
                if (frame.parentFrame() == null) {
                    recorder.addLog(E2eStepRecorder.nowTs(), "harness", "NAV", "",
                            "画面遷移: " + frame.url());
                }
            });
            // ブラウザコンソールは自アプリの画面で出た warning／error だけを記録する（外部UIはノイズ）。
            page.onConsoleMessage(message -> {
                String type = message.type();
                if (("error".equals(type) || "warning".equals(type))
                        && page.url().startsWith(base)) {
                    recorder.addLog(E2eStepRecorder.nowTs(), "browser", type, "", message.text());
                }
            });
        } else {
            stepRecorder = null;
            logCollector = null;
            verify = new E2eVerify(page, null, null);
        }
    }

    @AfterEach
    void tearDownEach() {
        for (BrowserContext other : otherContexts) {
            other.close();
        }
        otherContexts.clear();
        if (logCollector != null) {
            logCollector.detachInto(stepRecorder);
            logCollector = null;
        }
        if (E2eEvidence.enabled() && page != null) {
            captureEvidence();
        } else if (context != null) {
            context.close();
        }
        otherTabs.clear();
    }

    /**
     * 起動中アプリのベース URL を返す。
     *
     * @return {@code http://localhost:<ランダムポート>}
     */
    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * {@link #openOtherBrowser()} が返す別ブラウザ一式。
     *
     * @param context 別セッションのコンテキスト（先に閉じたい場合に使う。ケース終了時には自動で閉じる）
     * @param page 別ブラウザのページ
     * @param verify 別ブラウザ用の検証部品（主ブラウザと同じ steps.json へ連番で記録する）
     */
    public record OtherBrowser(BrowserContext context, Page page, E2eVerify verify) {
    }

    /**
     * 別ブラウザ（別セッション）を開く（排他制御・「読込後に他ユーザが更新した」状態の再現用）。
     *
     * <p>
     * 主ブラウザと同じ {@link E2eStepRecorder} を共有する専用の {@link E2eVerify} を返すため、別ブラウザ側の
     * 操作・検証も同一ケースのフィルムストリップへ連番で並び、「誰がいつ何をしたか」を1本の証跡で追える。 各ステップの desc には別ブラウザ側である旨（例:
     * 「【別ブラウザ】…」）を明示すること。 動画（.webm）は主ブラウザのみ録画される（フレームが証跡本体）。コンテキストはケース終了時に自動で閉じる。
     *
     * @return 別ブラウザ一式（context・page・verify）
     */
    protected OtherBrowser openOtherBrowser() {
        BrowserContext other = browser.newContext(newContextOptions());
        otherContexts.add(other);
        if (E2eEvidence.enabled()) {
            other.addInitScript(overlayScript());
        }
        Page otherPage = other.newPage();
        if (E2eEvidence.enabled()) {
            E2eStepRecorder recorder = stepRecorder;
            // 別ブラウザ側の画面遷移も、主ブラウザと区別できる形で証跡ログへ残す。
            otherPage.onFrameNavigated(frame -> {
                if (frame.parentFrame() == null) {
                    recorder.addLog(E2eStepRecorder.nowTs(), "harness", "NAV", "",
                            "画面遷移（別ブラウザ）: " + frame.url());
                }
            });
            return new OtherBrowser(other, otherPage, new E2eVerify(otherPage,
                    new E2eFrameService(otherPage, evidenceDir), stepRecorder));
        }
        return new OtherBrowser(other, otherPage, new E2eVerify(otherPage, null, null));
    }

    /**
     * {@link #openOtherTab()} が返す別タブ一式。
     *
     * @param page 別タブのページ
     * @param verify 別タブ用の検証部品（主ブラウザと同じ steps.json へ連番で記録する）
     */
    public record OtherTab(Page page, E2eVerify verify) {
    }

    /**
     * 同一ブラウザの別タブ（セッション Cookie を共有する同一 {@link BrowserContext} の新しいページ）を開く。
     *
     * <p>
     * 「セッション失効（ログイン有効期限切れ）状態での操作」の再現に用いる（到達手段カタログ 手段3）。
     * 別タブで同一ユーザのログアウトを押下すると、セッションを共有する元のタブも失効する。別セッションを
     * 開く {@link #openOtherBrowser()}（手段2）はセッションが異なり元のタブを失効させないため、本用途には
     * 用いない。
     *
     * <p>
     * 主ブラウザと同じ {@link E2eStepRecorder} を共有する専用の {@link E2eVerify} を返すため、別タブ側の
     * 操作・検証も同一ケースのフィルムストリップへ連番で並ぶ。各ステップの desc には別タブ側である旨
     * （例: 「【別タブ】…」）を明示すること。動画（.webm）は主ブラウザのみ録画される（フレームが証跡本体）。
     * タブはコンテキストとともにケース終了時に閉じられる。
     *
     * @return 別タブ一式（page・verify）
     */
    protected OtherTab openOtherTab() {
        Page tab = context.newPage();
        otherTabs.add(tab);
        if (E2eEvidence.enabled()) {
            E2eStepRecorder recorder = stepRecorder;
            // 別タブ側の画面遷移も、主ブラウザと区別できる形で証跡ログへ残す。
            tab.onFrameNavigated(frame -> {
                if (frame.parentFrame() == null) {
                    recorder.addLog(E2eStepRecorder.nowTs(), "harness", "NAV", "",
                            "画面遷移（別タブ）: " + frame.url());
                }
            });
            return new OtherTab(tab,
                    new E2eVerify(tab, new E2eFrameService(tab, evidenceDir), stepRecorder));
        }
        return new OtherTab(tab, new E2eVerify(tab, null, null));
    }

    private void captureEvidence() {
        Video video = page.video();
        // 別タブは主ブラウザと同じコンテキストのため個別に録画されるが、証跡の動画は主ブラウザのみと
        // する（フレームが証跡本体）。コンテキストを閉じる前に破棄予約しておく。
        List<Video> tabVideos = new ArrayList<>();
        for (Page tab : otherTabs) {
            Video tabVideo = tab.video();
            if (tabVideo != null) {
                tabVideos.add(tabVideo);
            }
        }
        try {
            // ステップフレーム群が証跡本体。steps.json（ステップ＋証跡ログ）を書き出す。
            stepRecorder.write();
            context.tracing().stop(new Tracing.StopOptions()
                    .setPath(evidenceDir.resolve(currentCaseSlug + ".trace.zip")));
        } finally {
            context.close();
        }
        for (Video tabVideo : tabVideos) {
            tabVideo.delete();
        }
        otherTabs.clear();
        if (video != null) {
            try {
                Files.move(video.path(), evidenceDir.resolve(currentCaseSlug + ".webm"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("動画の保存に失敗しました: " + currentCaseSlug, e);
            }
        }
    }

    private void createEvidenceDir() {
        try {
            Files.createDirectories(evidenceDir);
        } catch (IOException e) {
            throw new UncheckedIOException("エビデンスディレクトリの作成に失敗しました: " + evidenceDir, e);
        }
    }
}
