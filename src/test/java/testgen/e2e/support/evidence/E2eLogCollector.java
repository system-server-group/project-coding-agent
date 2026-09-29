package testgen.e2e.support.evidence;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.LoggerFactory;

/**
 * ケース実行中のアプリケーションログを捕捉し、証跡（steps.json のログ欄）へ引き渡す（証跡V2）。
 *
 * <p>
 * テスト開始時に logback のルートロガーへ {@link ListAppender} を装着し、終了時に外して回収する。
 * ノイズを抑えるため、自アプリ（コンストラクタで与えるルートパッケージ配下）のログは全レベル、その他は WARN 以上だけを残す。
 * 出力されるのは開発用ダミーのみであり、秘匿情報はアプリのログ方針（出力しない）に従う。
 */
public final class E2eLogCollector {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** 自アプリのルートパッケージ（この配下は全レベル、その他は WARN 以上だけを証跡ログへ残す）。 */
    private final String appPackage;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;

    /**
     * @param appPackage 自アプリのルートパッケージ（{@code E2eBaseTest#appRootPackage()} が糊クラス経由で与える）
     */
    public E2eLogCollector(String appPackage) {
        this.appPackage = appPackage;
    }

    /** ルートロガーへ捕捉用アペンダを装着する（テスト開始時に呼ぶ）。 */
    public void attach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        appender.list.clear();
        appender.start();
        root.addAppender(appender);
    }

    /**
     * アペンダを外し、捕捉したログをレコーダーへ引き渡す（テスト終了時に呼ぶ）。
     *
     * @param recorder 引き渡し先のステップ記録
     */
    public void detachInto(E2eStepRecorder recorder) {
        if (root == null) {
            return;
        }
        root.detachAppender(appender);
        appender.stop();
        root = null;
        for (ILoggingEvent event : appender.list) {
            boolean fromApp = event.getLoggerName().startsWith(appPackage);
            if (!fromApp && !event.getLevel().isGreaterOrEqual(Level.WARN)) {
                continue;
            }
            String ts = Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault())
                    .toLocalTime().format(TS_FORMAT);
            recorder.addLog(ts, "app", event.getLevel().toString(),
                    shortLoggerName(event.getLoggerName()), event.getFormattedMessage());
        }
    }

    /** ロガー名の末尾 2 要素だけを残す（表示幅の節約）。 */
    private static String shortLoggerName(String loggerName) {
        String[] parts = loggerName.split("\\.");
        if (parts.length <= 2) {
            return loggerName;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
