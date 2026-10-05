package com.system_server.ai_demo.apps.common;

import com.system_server.ai_demo.commons.conversion.IsoLocalDateEditor;
import com.system_server.ai_demo.commons.conversion.SpaceTrimEditor;
import java.time.LocalDate;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * 全コントローラー共通のフォームバインダ設定。画面入力の型変換・整形をここへ集約し、画面 （コントローラー）ごとに個別登録しない。
 *
 * <p>
 * 文字列項目は前後の半角・全角スペースを除去してからバインドする（画面部品仕様 システム共通仕様書 「トリム」）。日付項目は日付入力部品の ISO 形式（{@code yyyy-MM-dd}）を
 * {@link LocalDate} に変換し、 空文字（任意項目の未入力）は {@code null} として扱う。
 */
@ControllerAdvice
public class FormBindingAdvice {

    /**
     * 共通のカスタムエディタを登録する。
     *
     * @param binder データバインダ
     */
    @InitBinder
    void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new SpaceTrimEditor());
        binder.registerCustomEditor(LocalDate.class, new IsoLocalDateEditor());
    }
}
