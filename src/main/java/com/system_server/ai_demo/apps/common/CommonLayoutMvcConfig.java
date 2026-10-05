package com.system_server.ai_demo.apps.common;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 共通レイアウトの MVC 設定。{@link CommonLayoutInterceptor} を登録する。
 *
 * <p>
 * インターセプタは全リクエストに登録し、{@link CommonLayout} 付きコントローラーの画面応答かどうかは インターセプタ自身がハンドラの注釈で判定する。
 */
@Configuration
public class CommonLayoutMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<UserReferenceService> userReferenceServiceProvider;
    private final ObjectProvider<DepartmentReferenceService> departmentReferenceServiceProvider;

    public CommonLayoutMvcConfig(ObjectProvider<UserReferenceService> userReferenceServiceProvider,
            ObjectProvider<DepartmentReferenceService> departmentReferenceServiceProvider) {
        this.userReferenceServiceProvider = userReferenceServiceProvider;
        this.departmentReferenceServiceProvider = departmentReferenceServiceProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CommonLayoutInterceptor(userReferenceServiceProvider,
                departmentReferenceServiceProvider));
    }
}
