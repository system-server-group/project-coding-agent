package com.system_server.ai_demo.apps.common.controllers;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * エラー画面コントローラー。各画面で処理されないエラーの HTTP ステータスから種別を判定し、エラー画面を表示する。
 *
 * <p>
 * Spring Boot の既定エラーコントローラーを置き換える。共通レイアウトは使用しない。
 */
@Controller
public class ErrorCtrl implements ErrorController {

    /**
     * 発生したエラーのステータスから種別を判定し、タイトル・内容をバインドしてエラー画面を返す。
     *
     * @param request エラーディスパッチのリクエスト
     * @param model モデル
     * @return エラー画面テンプレート
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        return render(resolveStatus(request), model);
    }

    /**
     * ステータス指定のエラー画面。エラー種別を表示へ反映し、応答の HTTP ステータスにも設定する。
     *
     * <p>
     * fetch による API 呼び出しはエラー応答を受けても画面遷移が起きないため、エラー応答を受けた クライアントが本 URL
     * へ遷移し、エラー種別を保ったままエラー画面を表示する（API共通仕様 システム共通仕様書「HTTPステータスの使い分け」のクライアント動作）。表示を区別しない
     * ステータスは内部エラー（500）として扱う。
     *
     * @param status 遷移元のエラー応答の HTTP ステータス
     * @param response レスポンス
     * @param model モデル
     * @return エラー画面テンプレート
     */
    @RequestMapping("/error/{status:\\d{3}}")
    public String handleErrorByStatus(
            @PathVariable int status,
            HttpServletResponse response,
            Model model) {
        int kind = status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.FORBIDDEN.value()
                ? status
                : HttpStatus.INTERNAL_SERVER_ERROR.value();
        response.setStatus(kind);
        return render(kind, model);
    }

    /** エラーのステータスから種別（タイトル・内容）を決めてモデルへ載せ、エラー画面を返す。 */
    private static String render(int status, Model model) {
        String errorTitle;
        String errorMessage;
        if (status == HttpStatus.NOT_FOUND.value()) {
            errorTitle = "404 Not Found";
            errorMessage = "ページが見つかりませんでした。";
        } else if (status == HttpStatus.FORBIDDEN.value()) {
            errorTitle = "403 Forbidden";
            errorMessage = "ページにアクセスできません。";
        } else {
            errorTitle = "An Error Occurred";
            errorMessage = "エラーが発生しました。";
        }
        model.addAttribute("errorTitle", errorTitle);
        model.addAttribute("errorMessage", errorMessage);
        return "error/error";
    }

    private static int resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer value) {
            return value;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
