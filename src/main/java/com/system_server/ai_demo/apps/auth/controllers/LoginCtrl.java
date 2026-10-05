package com.system_server.ai_demo.apps.auth.controllers;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ログイン画面コントローラー。ログイン処理自体は Spring Security フォームログインが担う。
 *
 * <p>
 * 共通レイアウトは使用しない。
 */
@Controller
public class LoginCtrl {

    /**
     * ドメインURL ( / ) へのアクセスに対してログイン画面を表示する。ログイン済みの場合は案件情報一覧画面へリダイレクトする。
     *
     * @param authentication 現在の認証情報（未認証時は匿名）
     * @return ログイン画面テンプレート、またはログイン済み時は案件情報一覧へのリダイレクト
     */
    @GetMapping("/")
    public String loginPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/projects";
        }
        return "login/login";
    }
}
