package com.system_server.ai_demo.commons.code;

/**
 * 区分コードと {@link CodeEnum} 列挙値の相互変換を一元化する共通部品。
 *
 * <p>
 * 各列挙型に変換ロジックを重複実装させないため、突合は各定数の {@link CodeEnum#getCode()} の値で行う。
 */
public final class CodeEnums {

    private CodeEnums() {}

    /**
     * 区分コードから列挙値を解決する（必須項目向け）。
     *
     * @param <E> 対象の列挙型
     * @param enumType 対象の列挙型クラス
     * @param code 区分コード
     * @return 区分コードに対応する列挙値
     * @throws IllegalArgumentException 対応する列挙値が存在しない場合
     */
    public static <E extends Enum<E> & CodeEnum> E fromCode(Class<E> enumType, String code) {
        for (E constant : enumType.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "未知の区分コードです: type=" + enumType.getSimpleName() + ", code=" + code);
    }

    /**
     * 区分コードから列挙値を解決する（任意項目向け）。
     *
     * <p>
     * {@code code} が {@code null} の場合は {@code null} を返す。非 {@code null} かつ未知のコードの場合は
     * {@link #fromCode(Class, String)} と同様に例外を送出する（不正値を握り潰さない）。
     *
     * @param <E> 対象の列挙型
     * @param enumType 対象の列挙型クラス
     * @param code 区分コード（{@code null} 可）
     * @return 区分コードに対応する列挙値、または {@code null}
     * @throws IllegalArgumentException 非 {@code null} かつ対応する列挙値が存在しない場合
     */
    public static <E extends Enum<E> & CodeEnum> E fromCodeOrNull(Class<E> enumType, String code) {
        if (code == null) {
            return null;
        }
        return fromCode(enumType, code);
    }
}
