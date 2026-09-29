package testgen.e2e.seams;

import com.system_server.ai_demo.database.mapper.CompaniesMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import testgen.e2e.support.E2eFaultSeams;

/**
 * 会社マスタ管理の障害系シーム（テスト専用）。
 *
 * <p>
 * 洗い替え中のデータベース例外は画面操作だけでは到達できないため、アプリの Bean を委譲でラップし、 障害を注入する分岐で
 * {@link E2eFaultSeams#shouldFire(String)} を呼ぶ（到達手段カタログ 手段4）。
 * 登録が実行された<b>後</b>に例外を送出することで、洗い替えのトランザクション内で データベース例外が発生した状況（削除・登録がロールバックされる）を再現する。
 *
 * <p>
 * ラップは {@link BeanPostProcessor} で行う（同型の {@code @Bean} を足すと自動構成が退避するため）。
 */
@TestConfiguration
public class CompanyMasterFaultSeamConfig {

    /** 会社マスタ洗い替えのデータベース例外シーム名（仕様書の操作手順の記載と一致させる）。 */
    public static final String DB_EXCEPTION_ON_REPLACE = "データベース例外（会社マスタ洗い替え）";

    /**
     * 会社マスタのデータアクセスを委譲でラップし、シームが有効なときは登録の実行<b>後</b>に例外を送出する。
     *
     * @return 会社マスタ Mapper をラップする BeanPostProcessor
     */
    @Bean
    static BeanPostProcessor companyReplaceFaultSeam() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof CompaniesMapper delegate)) {
                    return bean;
                }
                return Proxy.newProxyInstance(CompaniesMapper.class.getClassLoader(),
                        new Class<?>[] {CompaniesMapper.class}, (proxy, method, args) -> {
                            Object result = invoke(delegate, method, args);
                            if ("insert".equals(method.getName())
                                    && E2eFaultSeams.shouldFire(DB_EXCEPTION_ON_REPLACE)) {
                                throw new IllegalStateException(
                                        "障害シーム: " + DB_EXCEPTION_ON_REPLACE);
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
