package com.system_server.ai_demo.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 名前を省略した Web バインド系注釈（@RequestParam 等）について、コンパイル済みクラスに引数名が保持されている （= javac の {@code -parameters}
 * が有効）ことを検証する。
 *
 * <p>
 * 名前を省略した注釈は、Spring が {@code -parameters} で埋め込まれた引数名に依存して解決する。ビルドから
 * {@code -parameters}（build.gradle.kts）が外れると引数名が失われ、実行時に {@code IllegalArgumentException} で失敗する。
 * 本テストはその退行をビルド時に検出する（IDE 起動経路は .settings/org.eclipse.jdt.core.prefs で別途担保）。
 */
class ControllerParameterNameRetentionTest {

    private static final String BASE_PACKAGE = "com.system_server.ai_demo";

    @Test
    void unnamedWebBindingParametersRetainCompiledNames() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> missing = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller = Class.forName(definition.getBeanClassName(), false,
                    ControllerParameterNameRetentionTest.class.getClassLoader());
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (needsCompiledName(parameter) && !parameter.isNamePresent()) {
                        missing.add(controller.getName() + "#" + method.getName());
                    }
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "名前省略の Web バインド注釈に対し引数名が保持されていません"
                        + "（javac の -parameters が無効の可能性。build.gradle.kts を確認）:\n"
                        + String.join("\n", missing));
    }

    private static boolean needsCompiledName(Parameter parameter) {
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if (requestParam != null && requestParam.name().isEmpty()
                && requestParam.value().isEmpty()) {
            return true;
        }
        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
        if (pathVariable != null && pathVariable.name().isEmpty()
                && pathVariable.value().isEmpty()) {
            return true;
        }
        RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
        if (requestHeader != null && requestHeader.name().isEmpty()
                && requestHeader.value().isEmpty()) {
            return true;
        }
        CookieValue cookieValue = parameter.getAnnotation(CookieValue.class);
        if (cookieValue != null && cookieValue.name().isEmpty() && cookieValue.value().isEmpty()) {
            return true;
        }
        MatrixVariable matrixVariable = parameter.getAnnotation(MatrixVariable.class);
        return matrixVariable != null && matrixVariable.name().isEmpty()
                && matrixVariable.value().isEmpty();
    }
}
