package testgen.e2e.seams;

import com.system_server.ai_demo.database.mapper.ProjectsMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import testgen.e2e.support.E2eFaultSeams;

/**
 * 案件情報登録の障害系シーム（テスト専用）。
 *
 * <p>
 * サーバー内部の異常分岐（登録処理のデータベース例外・営業情報メールの送信失敗）は画面操作だけでは 到達できないため、アプリの Bean を委譲でラップし、障害を注入する分岐で
 * {@link E2eFaultSeams#shouldFire(String)} を呼ぶ（到達手段カタログ 手段4）。状態管理（ケース単位
 * トグル・発火後自動解除・ベースラインリセット時の全解除）と発火記録はキット側が担う。
 *
 * <p>
 * ラップは {@link BeanPostProcessor} で行う（同型の {@code @Bean} を足すと、自動構成の {@code @ConditionalOnMissingBean}
 * が退避して委譲先そのものが作られなくなるため）。本構成を {@code @Import} したテストクラスの ApplicationContext でのみ有効になる。
 */
@TestConfiguration
public class ProjectRegisterFaultSeamConfig {

    /** 登録処理のデータベース例外シーム名（仕様書の操作手順の記載と一致させる）。 */
    public static final String DB_EXCEPTION_ON_REGISTER = "データベース例外（案件登録）";

    /** 営業情報メールの送信失敗シーム名（仕様書の操作手順の記載と一致させる）。 */
    public static final String MAIL_SEND_FAILURE = "メール送信失敗";

    /**
     * 案件情報のデータアクセスを委譲でラップし、シームが有効なときは登録行の挿入<b>後</b>に例外を送出する。
     *
     * <p>
     * 挿入後に送出することで、登録処理のトランザクション内でデータベース例外が発生した状況を再現する （挿入済みの行はロールバックされ、登録実施前の状態へ戻る）。
     *
     * @return 案件情報 Mapper をラップする BeanPostProcessor
     */
    @Bean
    static BeanPostProcessor projectsMapperFaultSeam() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof ProjectsMapper delegate)) {
                    return bean;
                }
                return Proxy.newProxyInstance(ProjectsMapper.class.getClassLoader(),
                        new Class<?>[] {ProjectsMapper.class}, (proxy, method, args) -> {
                            Object result = invoke(delegate, method, args);
                            if ("insert".equals(method.getName())
                                    && E2eFaultSeams.shouldFire(DB_EXCEPTION_ON_REGISTER)) {
                                throw new IllegalStateException(
                                        "障害シーム: " + DB_EXCEPTION_ON_REGISTER);
                            }
                            return result;
                        });
            }
        };
    }

    /**
     * メール送信を委譲でラップし、シームが有効なときは送信を行わず例外を送出する。
     *
     * <p>
     * 送信元（{@code JavaMailSender}）で失敗させることで、アプリの営業情報メール送信サービスが持つ 失敗時の扱い（例外を送出せず警告ログのみ）をそのまま通す。
     *
     * @return メール送信元をラップする BeanPostProcessor
     */
    @Bean
    static BeanPostProcessor mailSenderFaultSeam() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof JavaMailSender delegate)) {
                    return bean;
                }
                return Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(),
                        new Class<?>[] {JavaMailSender.class}, (proxy, method, args) -> {
                            if (method.getName().startsWith("send")
                                    && E2eFaultSeams.shouldFire(MAIL_SEND_FAILURE)) {
                                throw new MailSendException("障害シーム: " + MAIL_SEND_FAILURE);
                            }
                            return invoke(delegate, method, args);
                        });
            }
        };
    }

    /** 委譲呼び出し（リフレクションの包み例外をそのまま原因例外として送出する）。 */
    private static Object invoke(Object delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
