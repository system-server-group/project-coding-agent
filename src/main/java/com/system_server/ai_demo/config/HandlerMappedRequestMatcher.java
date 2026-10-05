package com.system_server.ai_demo.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * リクエスト URL が実装済みの画面（MVC ハンドラ）にマッピングされるかを判定するリクエストマッチャー。
 *
 * <p>
 * 未認証アクセスの応答振り分け（パーマリンク システム共通仕様書「遷移を保留しない例外条件」）に用いる。 コントローラーの URL パターン一覧を初回判定時に
 * {@link RequestMappingHandlerMapping} から取得して保持し、 リクエストのパス（コンテキストパス除去後）がいずれかの
 * パターンに一致するかで実在を判定する。HTTP メソッドは考慮しない（URL としての実在のみを判定する）。
 *
 * <p>
 * ハンドラマッピングが取得できない場合は安全側に「実在する」と判定し、従来動作（ログイン画面への誘導）へ フォールバックする。静的リソース（{@code /css/**}
 * 等）は認可設定で許可済みのため本判定の対象にならない。
 */
final class HandlerMappedRequestMatcher implements RequestMatcher {

    private final ObjectProvider<RequestMappingHandlerMapping> mappingProvider;

    /** コントローラーの URL パターン一覧（初回判定時に解決。解決前は null）。 */
    private volatile Set<PathPattern> patterns;

    HandlerMappedRequestMatcher(ObjectProvider<RequestMappingHandlerMapping> mappingProvider) {
        this.mappingProvider = mappingProvider;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        Set<PathPattern> resolved = resolvePatterns();
        if (resolved == null) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.isEmpty()) {
            path = "/";
        }
        PathContainer container = PathContainer.parsePath(path);
        return resolved.stream().anyMatch(pattern -> pattern.matches(container));
    }

    private Set<PathPattern> resolvePatterns() {
        Set<PathPattern> resolved = this.patterns;
        if (resolved != null) {
            return resolved;
        }
        RequestMappingHandlerMapping mapping = mappingProvider.getIfAvailable();
        if (mapping == null) {
            return null;
        }
        Set<PathPattern> collected = new HashSet<>();
        mapping.getHandlerMethods().keySet().forEach(info -> {
            var condition = info.getPathPatternsCondition();
            if (condition != null) {
                collected.addAll(condition.getPatterns());
            } else {
                // PathPattern 非使用の構成へのフォールバック（本プロジェクトの既定では通らない）
                info.getPatternValues().forEach(
                        value -> collected.add(PathPatternParser.defaultInstance.parse(value)));
            }
        });
        this.patterns = Set.copyOf(collected);
        return this.patterns;
    }
}
