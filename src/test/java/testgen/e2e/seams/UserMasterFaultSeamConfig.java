package testgen.e2e.seams;

import com.system_server.ai_demo.database.mapper.UsersMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import testgen.e2e.support.E2eFaultSeams;

/**
 * ユーザマスタ管理の障害系シーム（テスト専用）。
 *
 * <p>
 * 編集中のデータベース例外と、他のユーザによる同時更新（排他エラー）は画面操作だけでは到達できないため、 アプリの Bean を委譲でラップし、障害を注入する分岐で
 * {@link E2eFaultSeams#shouldFire(String)} を呼ぶ （到達手段カタログ 手段4）。
 *
 * <ul>
 * <li>データベース例外は登録の実行<b>後</b>に送出し、編集のトランザクションがロールバックされる状況を再現する。</li>
 * <li>排他エラーは更新・削除の<b>更新件数 0</b> を返して再現する（アプリはこれを「他のユーザがデータを 更新中」と判定する）。実際の更新・削除は行わない。</li>
 * </ul>
 *
 * <p>
 * ラップは {@link BeanPostProcessor} で行う（同型の {@code @Bean} を足すと自動構成が退避するため）。
 */
@TestConfiguration
public class UserMasterFaultSeamConfig {

    /** ユーザマスタ編集のデータベース例外シーム名（仕様書の操作手順の記載と一致させる）。 */
    public static final String DB_EXCEPTION_ON_EDIT = "データベース例外（ユーザマスタ編集）";

    /** ユーザマスタ更新の排他エラーシーム名（仕様書の操作手順の記載と一致させる）。 */
    public static final String CONFLICT_ON_UPDATE = "排他エラー（ユーザマスタ更新）";

    /** ユーザマスタ削除の排他エラーシーム名（仕様書の操作手順の記載と一致させる）。 */
    public static final String CONFLICT_ON_DELETE = "排他エラー（ユーザマスタ削除）";

    /**
     * ユーザマスタのデータアクセスを委譲でラップし、シームが有効なときに障害を注入する。
     *
     * @return ユーザマスタ Mapper をラップする BeanPostProcessor
     */
    @Bean
    static BeanPostProcessor userMasterFaultSeam() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof UsersMapper delegate)) {
                    return bean;
                }
                return Proxy.newProxyInstance(UsersMapper.class.getClassLoader(),
                        new Class<?>[] {UsersMapper.class}, (proxy, method, args) -> {
                            String name = method.getName();
                            if ("update".equals(name)
                                    && E2eFaultSeams.shouldFire(CONFLICT_ON_UPDATE)) {
                                return 0;
                            }
                            if ("deleteByUserId".equals(name)
                                    && E2eFaultSeams.shouldFire(CONFLICT_ON_DELETE)) {
                                return 0;
                            }
                            Object result = invoke(delegate, method, args);
                            if ("insert".equals(name)
                                    && E2eFaultSeams.shouldFire(DB_EXCEPTION_ON_EDIT)) {
                                throw new IllegalStateException("障害シーム: " + DB_EXCEPTION_ON_EDIT);
                            }
                            return result;
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
