package com.system_server.ai_demo.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.system_server.ai_demo.apps.master.reference.services.DepartmentReferenceService;
import com.system_server.ai_demo.apps.master.reference.services.UserReferenceService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

class CommonLayoutInterceptorTest {

    private final UserReferenceService userReferenceService = mock(UserReferenceService.class);
    private final DepartmentReferenceService departmentReferenceService =
            mock(DepartmentReferenceService.class);
    private final CommonLayoutInterceptor interceptor = new CommonLayoutInterceptor(
            provider(userReferenceService), provider(departmentReferenceService));

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getObject()).thenReturn(value);
        return objectProvider;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String userId, String authority) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(userId,
                "password", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @CommonLayout
    static class LayoutProbe {
        public String probe() {
            return "probe";
        }
    }

    static class PlainProbe {
        public String probe() {
            return "probe";
        }
    }

    private static HandlerMethod handler(Class<?> type) throws Exception {
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(),
                type.getMethod("probe"));
    }

    private void postHandle(HandlerMethod handlerMethod, ModelAndView modelAndView)
            throws Exception {
        interceptor.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handlerMethod, modelAndView);
    }

    @Test
    void postHandle_populatesForAdmin() throws Exception {
        authenticate("SM9", "ROLE_ADMIN");
        when(userReferenceService.getUserName("SM9")).thenReturn("管理者");
        when(userReferenceService.getUserDepartmentId("SM9")).thenReturn(10);
        when(departmentReferenceService.getDepartmentName(10)).thenReturn("情報システム部");
        ModelAndView modelAndView = new ModelAndView("probe");

        postHandle(handler(LayoutProbe.class), modelAndView);

        assertEquals("管理者", modelAndView.getModel().get("headerUserName"));
        assertEquals("情報システム部", modelAndView.getModel().get("headerDepartmentName"));
        assertEquals(Boolean.TRUE, modelAndView.getModel().get("headerIsAdmin"));
    }

    @Test
    void postHandle_populatesForGeneralUser() throws Exception {
        authenticate("SM0", "ROLE_USER");
        when(userReferenceService.getUserName("SM0")).thenReturn("一般");
        when(userReferenceService.getUserDepartmentId("SM0")).thenReturn(20);
        when(departmentReferenceService.getDepartmentName(20)).thenReturn("営業部");
        ModelAndView modelAndView = new ModelAndView("probe");

        postHandle(handler(LayoutProbe.class), modelAndView);

        assertEquals(Boolean.FALSE, modelAndView.getModel().get("headerIsAdmin"));
    }

    @Test
    void postHandle_throwsWhenDepartmentNotFound() throws Exception {
        authenticate("SM0", "ROLE_USER");
        when(userReferenceService.getUserName("SM0")).thenReturn("一般");
        when(userReferenceService.getUserDepartmentId("SM0")).thenReturn(20);
        when(departmentReferenceService.getDepartmentName(20)).thenReturn(null);
        HandlerMethod handlerMethod = handler(LayoutProbe.class);
        ModelAndView modelAndView = new ModelAndView("probe");

        assertThrows(IllegalStateException.class, () -> postHandle(handlerMethod, modelAndView));
    }

    @Test
    void postHandle_throwsWhenDepartmentIdNull() throws Exception {
        authenticate("SM0", "ROLE_USER");
        when(userReferenceService.getUserName("SM0")).thenReturn("一般");
        when(userReferenceService.getUserDepartmentId("SM0")).thenReturn(null);
        HandlerMethod handlerMethod = handler(LayoutProbe.class);
        ModelAndView modelAndView = new ModelAndView("probe");

        assertThrows(IllegalStateException.class, () -> postHandle(handlerMethod, modelAndView));
    }

    @Test
    void postHandle_skipsControllerWithoutAnnotation() throws Exception {
        authenticate("SM0", "ROLE_USER");
        ModelAndView modelAndView = new ModelAndView("probe");

        postHandle(handler(PlainProbe.class), modelAndView);

        assertNull(modelAndView.getModel().get("headerUserName"));
    }

    @Test
    void postHandle_skipsRedirectResponse() throws Exception {
        authenticate("SM0", "ROLE_USER");
        ModelAndView modelAndView = new ModelAndView("redirect:/projects");

        postHandle(handler(LayoutProbe.class), modelAndView);

        assertNull(modelAndView.getModel().get("headerUserName"));
    }
}
