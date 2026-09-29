package com.system_server.ai_demo.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Security 設定。フォームログイン・BCrypt・URL ベース認可・例外時の応答（未認証/権限不足）を構成する。
 *
 * <p>
 * 認証は {@code AuthService}（UserDetailsService）と {@link #passwordEncoder()} による。認可は本設定の URL
 * ルールが強制する（マスタ管理＝管理者のみ／案件情報管理・API＝認証必須）。
 */
@Configuration
public class SecurityConfig {

    private static final String API_PATTERN = "/api/**";

    /** マスタ管理の CSV ダウンロード。画面ではないため復帰先にしない。 */
    private static final String CSV_DOWNLOAD_PATTERN = "/master/*/csv";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings) throws Exception {
        RequestMatcher screenExists = new HandlerMappedRequestMatcher(handlerMappings);
        AuthenticationEntryPoint entryPoint = authenticationEntryPoint(screenExists);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/logout", "/error", "/error/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/master/**").hasRole("ADMIN").anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/").loginProcessingUrl("/login")
                        .usernameParameter("userId").passwordParameter("password")
                        .defaultSuccessUrl("/projects", false).failureUrl("/?error").permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/").permitAll())
                .requestCache(cache -> cache.requestCache(loginRedirectRequestCache(screenExists)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler(entryPoint)));
        return http.build();
    }

    /**
     * 未ログイン時の画面遷移の保留（パーマリンク システム共通仕様書「未ログイン時の画面遷移の保留」）で復帰先を保持するリクエストキャッシュ。
     *
     * <p>
     * 保存対象は画面の GET リクエストに限る。更新系（POST 等）は、セッション失効時にログイン画面へ誘導したのち
     * 案件情報一覧画面へ遷移する仕様のため復帰先にしない（各機能仕様書「ログインの有効期限が切れている場合」）。 {@code /api/**} とマスタ管理の CSV
     * ダウンロードは画面ではなく復帰先になり得ないため除外する。{@code /login}・ {@code /logout} も、復帰先になると GET で到達できず 405
     * となるため除外する。非同期リクエスト （{@code X-Requested-With}）も画面遷移ではないため除外する。 存在しない URL は 404
     * を応答して復帰先になり得ないため除外する（パーマリンク システム共通仕様書「遷移を保留しない例外条件」）。
     *
     * @param screenExists リクエスト URL が実在の画面にマッピングされるかを判定するマッチャー
     * @return リクエストキャッシュ
     */
    private static RequestCache loginRedirectRequestCache(RequestMatcher screenExists) {
        RequestMatcher screenGet =
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/**");
        RequestMatcher notExcludedPath = new NegatedRequestMatcher(
                new OrRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(API_PATTERN),
                        PathPatternRequestMatcher.withDefaults().matcher(CSV_DOWNLOAD_PATTERN),
                        PathPatternRequestMatcher.withDefaults().matcher("/login"),
                        PathPatternRequestMatcher.withDefaults().matcher("/logout"),
                        PathPatternRequestMatcher.withDefaults().matcher("/favicon.ico")));
        RequestMatcher notAsync = new NegatedRequestMatcher(
                new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"));

        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(
                new AndRequestMatcher(screenGet, notExcludedPath, notAsync, screenExists));
        // Spring Security 6 既定の再生マーカー（復帰URLへ付く ?continue クエリ）を無効化する。
        // パーマリンク仕様の復帰先は保存時の URL そのものであり、余分なクエリを画面 URL に残さない
        // （代償は保存済みリクエスト有無のセッション参照が毎リクエスト行われることのみ）。
        cache.setMatchingRequestParameterName(null);
        return cache;
    }

    /**
     * 未認証アクセスの応答を振り分ける。API は 401（ボディなし）、存在しない URL は 404 を送出してエラー画面へ
     * ディスパッチし、それ以外（実在する画面）はログイン画面へ誘導する（パーマリンク システム共通仕様書「遷移を保留しない例外条件」「未ログイン時の画面遷移の保留」）。
     *
     * @param screenExists リクエスト URL が実在の画面にマッピングされるかを判定するマッチャー
     * @return 認証エントリポイント
     */
    private static AuthenticationEntryPoint authenticationEntryPoint(RequestMatcher screenExists) {
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher(API_PATTERN))
                .addEntryPointFor(
                        (request, response, ex) -> response.sendError(HttpStatus.NOT_FOUND.value()),
                        new NegatedRequestMatcher(screenExists))
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/")).build();
    }

    /**
     * 権限不足（403）の応答を振り分ける。API は 403（ボディなし）、それ以外は 403 を送出してエラー画面へディスパッチする。
     *
     * <p>
     * ただし {@link MissingCsrfTokenException}（セッション失効等でセッション内の CSRF トークンが失われた状態）は
     * 権限不足ではなく未認証として扱い、{@code entryPoint} へ委譲する（画面はログイン画面へ誘導・API は 401）。 CSRF
     * フィルタは認可フィルタより前段で本ハンドラを直接呼ぶため、未認証時の共通経路（
     * {@code ExceptionTranslationFilter}）を通らない。トークンが存在するのに一致しない {@code InvalidCsrfTokenException}
     * は本来の CSRF 検知であり、従来どおり 403 とする。
     *
     * @param entryPoint 未認証時の応答（画面＝ログイン画面へ誘導／API＝401）
     * @return アクセス拒否ハンドラ
     */
    private static AccessDeniedHandler accessDeniedHandler(AuthenticationEntryPoint entryPoint) {
        return (request, response, ex) -> {
            if (ex instanceof MissingCsrfTokenException) {
                entryPoint.commence(request, response,
                        new InsufficientAuthenticationException("セッションが失効しています。", ex));
                return;
            }
            String apiPrefix = request.getContextPath() + "/api/";
            if (request.getRequestURI().startsWith(apiPrefix)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
            } else {
                response.sendError(HttpStatus.FORBIDDEN.value());
            }
        };
    }
}
