package com.system_server.ai_demo.apps.common;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 共通レイアウトの初期表示。ログイン中ユーザのユーザ名・部署名・ロールを取得し、共通ヘッダー用のモデル属性として供給する。
 *
 * <p>
 * {@link CommonLayout} を付与した画面コントローラーの画面応答にのみ適用する。属性の解決はハンドラ実行後
 * （postHandle）に行う——マスタ洗い替え等、ハンドラ内でマスタの内容が変わる操作の応答にも変更後の最新値を 表示するため（ハンドラより前に実行される
 * {@code @ModelAttribute} では洗い替え前の値になる）。リダイレクト 応答には付与しない（ヘッダーは描画されず、モデル属性がリダイレクト URL
 * のクエリへ漏れることを防ぐ）。 部署IDに対応する部署マスタが存在しない場合はサーバー内部エラー（例外送出→エラー画面）とする。
 */
public class CommonLayoutInterceptor implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ObjectProvider<UserReferenceService> userReferenceServiceProvider;
    private final ObjectProvider<DepartmentReferenceService> departmentReferenceServiceProvider;

    public CommonLayoutInterceptor(
            ObjectProvider<UserReferenceService> userReferenceServiceProvider,
            ObjectProvider<DepartmentReferenceService> departmentReferenceServiceProvider) {
        this.userReferenceServiceProvider = userReferenceServiceProvider;
        this.departmentReferenceServiceProvider = departmentReferenceServiceProvider;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !handlerMethod.getBeanType().isAnnotationPresent(CommonLayout.class)) {
            return;
        }
        if (modelAndView == null || modelAndView.getViewName() == null
                || modelAndView.getViewName().startsWith("redirect:")) {
            return;
        }
        UserReferenceService userReferenceService = userReferenceServiceProvider.getObject();
        DepartmentReferenceService departmentReferenceService =
                departmentReferenceServiceProvider.getObject();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();
        String userName = userReferenceService.getUserName(userId);
        Integer departmentId = userReferenceService.getUserDepartmentId(userId);
        String departmentName = null;
        if (departmentId != null) {
            departmentName = departmentReferenceService.getDepartmentName(departmentId);
        }
        if (departmentName == null) {
            throw new IllegalStateException("department master not found for the logged-in user");
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
        modelAndView.addObject("headerUserName", userName);
        modelAndView.addObject("headerDepartmentName", departmentName);
        modelAndView.addObject("headerIsAdmin", admin);
    }
}
