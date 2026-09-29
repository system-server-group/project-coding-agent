package com.system_server.ai_demo.commons.error;

import com.system_server.ai_demo.commons.validation.CheckMessages;
import com.system_server.ai_demo.commons.validation.CheckRank;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * 画面フォームの入力値チェック違反（{@link BindingResult}）を、画面項目ごとの表示用メッセージへ整形する共通部品。
 *
 * <p>
 * 画面部品仕様（システム共通仕様書）に従い、入力値チェックのエラーメッセージは該当する画面項目（部品）の直下に表示する。このため 項目名（フィールド物理名）とメッセージの対応を保持したまま返す。1
 * つの画面項目につきメッセージは 1 件とし、同一項目に複数違反が ある場合は入力値チェック一覧でより小さい番号（{@link CheckRank}）のメッセージを採用する。
 *
 * <p>
 * 日付項目に日付として解釈できない値が入力された場合、Bean Validation より前のバインディング段階で型変換に失敗し、 Spring
 * 既定の型変換エラーメッセージ（仕様外の文言）が付与される。これは入力値チェック仕様の「日付形式」違反に該当する
 * ため、{@link CheckMessages#INVALID_DATE}（「無効な日付です。」）へ読み替えて表示する。並びは最初に違反が現れた項目順を保つ。
 */
public final class FormErrorMessages {

    /** 型変換失敗（バインディング）時に付与されるエラーコード。 */
    private static final String TYPE_MISMATCH_CODE = "typeMismatch";

    /** 日付（{@link LocalDate}）への型変換失敗を表すエラーコード（{@code typeMismatch.<型のFQN>}）。 */
    private static final String DATE_TYPE_MISMATCH_CODE =
            TYPE_MISMATCH_CODE + "." + LocalDate.class.getName();

    private FormErrorMessages() {}

    /**
     * 各違反項目について最も優先順位の高い（番号の小さい）メッセージのみを残した、項目名→メッセージの対応を返す。
     *
     * @param bindingResult 検証結果
     * @return 画面項目の物理名をキー、表示メッセージを値とする対応（項目ごと1件）
     */
    public static Map<String, String> reduceByField(BindingResult bindingResult) {
        Map<String, FieldError> bestByField = new LinkedHashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            FieldError current = bestByField.get(error.getField());
            if (current == null
                    || CheckRank.rankOf(error.getCode()) < CheckRank.rankOf(current.getCode())) {
                bestByField.put(error.getField(), error);
            }
        }
        Map<String, String> messages = new LinkedHashMap<>();
        bestByField.forEach((field, error) -> messages.put(field, resolveMessage(error)));
        return messages;
    }

    /**
     * 表示メッセージを決定する。日付項目の型変換失敗（{@code typeMismatch}）は、日付として解釈できない入力であり
     * 入力値チェック仕様の「日付形式」に該当するため、Spring 既定の文言ではなく {@link CheckMessages#INVALID_DATE}
     * に読み替える。それ以外は違反から解決済みの既定メッセージをそのまま用いる。
     *
     * @param error 対象項目の違反
     * @return 表示メッセージ
     */
    private static String resolveMessage(FieldError error) {
        if (isDateTypeMismatch(error)) {
            return CheckMessages.INVALID_DATE;
        }
        return error.getDefaultMessage();
    }

    /**
     * 日付（{@link LocalDate}）への型変換失敗による違反かどうかを判定する。型変換失敗時に Spring が生成する
     * エラーコード配列に対象型のコード（{@link #DATE_TYPE_MISMATCH_CODE}）が含まれるかで判定し、対象を日付型に限定する。
     *
     * @param error 対象項目の違反
     * @return 日付の型変換失敗であれば {@code true}
     */
    private static boolean isDateTypeMismatch(FieldError error) {
        if (!TYPE_MISMATCH_CODE.equals(error.getCode())) {
            return false;
        }
        String[] codes = error.getCodes();
        if (codes == null) {
            return false;
        }
        for (String code : codes) {
            if (DATE_TYPE_MISMATCH_CODE.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
