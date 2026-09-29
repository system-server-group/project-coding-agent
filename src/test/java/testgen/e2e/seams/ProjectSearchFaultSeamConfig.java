package testgen.e2e.seams;

import com.system_server.ai_demo.database.mapper.ProjectsMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import testgen.e2e.support.E2eFaultSeams;

/**
 * 案件検索の障害系シーム（テスト専用）。
 *
 * <p>
 * 検索処理のデータベース例外は画面操作だけでは到達できないため、アプリの Bean を委譲でラップし、 障害を注入する分岐で
 * {@link E2eFaultSeams#shouldFire(String)} を呼ぶ（到達手段カタログ 手段4）。
 * 検索は参照のみで副作用を持たないため、検索の実行<b>前</b>に例外を送出する。
 *
 * <p>
 * ラップは {@link BeanPostProcessor} で行う（同型の {@code @Bean} を足すと自動構成が退避するため）。
 */
@TestConfiguration
public class ProjectSearchFaultSeamConfig {

    /** 案件検索のデータベース例外シーム名（仕様書の操作手順の記載と一致させる）。 */
    public static final String DB_EXCEPTION_ON_SEARCH = "データベース例外（案件検索）";

    /**
     * 案件情報のデータアクセスを委譲でラップし、シームが有効なときは検索の実行前に例外を送出する。
     *
     * @return 案件情報 Mapper をラップする BeanPostProcessor
     */
    @Bean
    static BeanPostProcessor projectSearchFaultSeam() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof ProjectsMapper delegate)) {
                    return bean;
                }
                return Proxy.newProxyInstance(ProjectsMapper.class.getClassLoader(),
                        new Class<?>[] {ProjectsMapper.class}, (proxy, method, args) -> {
                            if ("search".equals(method.getName())
                                    && E2eFaultSeams.shouldFire(DB_EXCEPTION_ON_SEARCH)) {
                                throw new IllegalStateException("障害シーム: " + DB_EXCEPTION_ON_SEARCH);
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
