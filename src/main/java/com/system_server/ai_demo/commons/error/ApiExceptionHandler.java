package com.system_server.ai_demo.commons.error;

import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.commons.validation.CheckRank;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * API 共通の入力チェック違反ハンドラ。違反を HTTP 400 の {@link ErrorResponse}（{@code errors[]}）へ整形する。
 *
 * <p>
 * 1 つの画面項目につきメッセージは 1 件とし、同一項目に複数違反がある場合は入力値チェック一覧でより小さい番号のチェックの
 * メッセージを採用する（チェック種別の優先順位はシステム共通であり、機能固有の項目名・項目順は持たない）。{@code errors[]} の項目間の順序は規定しない。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * リクエストボディ等の {@code @Valid} 検証違反を整形する。
     *
     * @param ex 検証違反例外
     * @return エラーレスポンス
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<Ranked> violations = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            violations.add(new Ranked(fe.getField(), fe.getDefaultMessage(), rankOf(fe.getCode())));
        }
        for (ObjectError ge : ex.getBindingResult().getGlobalErrors()) {
            violations.add(new Ranked(null, ge.getDefaultMessage(), CheckRank.DEFAULT));
        }
        return reduce(violations);
    }

    /**
     * メソッドパラメータ等の {@code @Validated} 検証違反を整形する。
     *
     * @param ex 制約違反例外
     * @return エラーレスポンス
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        List<Ranked> violations = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String field = lastNode(v.getPropertyPath());
            String checkName =
                    v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            violations.add(new Ranked(field, v.getMessage(), rankOf(checkName)));
        }
        return reduce(violations);
    }

    /**
     * コントローラーメソッドのパラメータ単項目検証違反（Spring MVC ネイティブ検証）を整形する。
     *
     * @param ex メソッドパラメータ検証例外
     * @return エラーレスポンス
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ErrorResponse handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<Ranked> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                int rank = rankOf(lastCode(error));
                violations.add(new Ranked(field, error.getDefaultMessage(), rank));
            }
        }
        return reduce(violations);
    }

    /**
     * メソッドパラメータの型変換失敗（数値項目への非整数入力など）を整形する。「整数」チェックの違反として扱う。
     *
     * @param ex 型不一致例外
     * @return エラーレスポンス
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse(List.of(new ErrorItem(ex.getName(), CheckMessages.INTEGER_VALUE)));
    }

    /**
     * 必須リクエストパラメータの欠落を整形する。「必須入力」チェックの違反として扱う。
     *
     * @param ex パラメータ欠落例外
     * @return エラーレスポンス
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ErrorResponse handleMissingParameter(MissingServletRequestParameterException ex) {
        String field = ex.getParameterName();
        return new ErrorResponse(List.of(new ErrorItem(field, CheckMessages.REQUIRED_INPUT)));
    }

    /**
     * リクエストボディの変換失敗を整形する。日付項目（{@link LocalDate}）のパース失敗は「日付形式」チェックの違反として扱い、
     * 違反した項目名と「無効な日付です。」を返す（入力値チェック システム共通仕様書「日付形式」）。
     *
     * <p>
     * ボディの変換はオブジェクト全体の生成前に失敗するため、他項目の値のマッピング・検証には到達しない（同時に返る違反は 変換に失敗した1項目のみとなる）。日付以外の変換失敗（不正な JSON
     * 等）は項目に紐づけられないため、空の違反一覧を返す。
     *
     * @param ex ボディ変換失敗例外
     * @return エラーレスポンス
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handleMessageNotReadable(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException ife
                    && LocalDate.class.equals(ife.getTargetType())) {
                String field = lastFieldName(ife);
                if (field != null) {
                    return new ErrorResponse(
                            List.of(new ErrorItem(field, CheckMessages.INVALID_DATE)));
                }
            }
        }
        return new ErrorResponse(List.of());
    }

    private static String lastFieldName(InvalidFormatException ex) {
        String field = null;
        for (var reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                field = reference.getPropertyName();
            }
        }
        return field;
    }

    /**
     * 項目ごとに最も優先順位の高い（番号の小さい）違反のみを残してレスポンスへ整形する。項目に紐づかない違反はそのまま残す。
     *
     * @param violations 整形対象の違反一覧
     * @return エラーレスポンス
     */
    private static ErrorResponse reduce(List<Ranked> violations) {
        Map<String, Ranked> bestByField = new LinkedHashMap<>();
        List<ErrorItem> globals = new ArrayList<>();
        for (Ranked r : violations) {
            if (r.field() == null) {
                globals.add(new ErrorItem(null, r.message()));
                continue;
            }
            Ranked current = bestByField.get(r.field());
            if (current == null || r.rank() < current.rank()) {
                bestByField.put(r.field(), r);
            }
        }
        List<ErrorItem> items = new ArrayList<>();
        for (Ranked r : bestByField.values()) {
            items.add(new ErrorItem(r.field(), r.message()));
        }
        items.addAll(globals);
        return new ErrorResponse(items);
    }

    private static int rankOf(String checkName) {
        return CheckRank.rankOf(checkName);
    }

    private static String lastNode(Path path) {
        String last = null;
        for (Path.Node node : path) {
            last = node.getName();
        }
        return last;
    }

    private static String lastCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        if (codes == null || codes.length == 0) {
            return null;
        }
        return codes[codes.length - 1];
    }

    /**
     * 整形途中の違反（項目物理名・メッセージ・優先順位）。
     *
     * @param field 違反項目の物理名（{@code null} 可）
     * @param message エラーメッセージ
     * @param rank 入力値チェック種別の優先順位（小さいほど優先）
     */
    private record Ranked(String field, String message, int rank) {
    }
}
