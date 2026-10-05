package com.system_server.ai_demo.apps.common.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class ErrorCtrlTest {

    private final ErrorCtrl controller = new ErrorCtrl();

    private String handle(Integer status, Model model) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        return controller.handleError(request, model);
    }

    @Test
    void notFound_showsNotFoundMessage() {
        Model model = new ConcurrentModel();
        assertEquals("error/error", handle(404, model));
        assertEquals("404 Not Found", model.getAttribute("errorTitle"));
        assertEquals("ページが見つかりませんでした。", model.getAttribute("errorMessage"));
    }

    @Test
    void forbidden_showsForbiddenMessage() {
        Model model = new ConcurrentModel();
        assertEquals("error/error", handle(403, model));
        assertEquals("403 Forbidden", model.getAttribute("errorTitle"));
        assertEquals("ページにアクセスできません。", model.getAttribute("errorMessage"));
    }

    @Test
    void internalError_showsGenericMessage() {
        Model model = new ConcurrentModel();
        assertEquals("error/error", handle(500, model));
        assertEquals("An Error Occurred", model.getAttribute("errorTitle"));
        assertEquals("エラーが発生しました。", model.getAttribute("errorMessage"));
    }

    @Test
    void unknownStatus_defaultsToInternalError() {
        Model model = new ConcurrentModel();
        handle(null, model);
        assertEquals("An Error Occurred", model.getAttribute("errorTitle"));
        assertEquals("エラーが発生しました。", model.getAttribute("errorMessage"));
    }

    @Test
    void statusPath_forbidden_showsForbiddenAndSetsStatus() {
        Model model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals("error/error", controller.handleErrorByStatus(403, response, model));
        assertEquals(403, response.getStatus());
        assertEquals("403 Forbidden", model.getAttribute("errorTitle"));
        assertEquals("ページにアクセスできません。", model.getAttribute("errorMessage"));
    }

    @Test
    void statusPath_notFound_showsNotFoundAndSetsStatus() {
        Model model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals("error/error", controller.handleErrorByStatus(404, response, model));
        assertEquals(404, response.getStatus());
        assertEquals("404 Not Found", model.getAttribute("errorTitle"));
        assertEquals("ページが見つかりませんでした。", model.getAttribute("errorMessage"));
    }

    @Test
    void statusPath_unknownStatus_showsGenericErrorWithInternalErrorStatus() {
        Model model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.handleErrorByStatus(418, response, model);
        assertEquals(500, response.getStatus());
        assertEquals("An Error Occurred", model.getAttribute("errorTitle"));
        assertEquals("エラーが発生しました。", model.getAttribute("errorMessage"));
    }
}
