package com.system_server.ai_demo.commons.code;

import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.boot.jackson.ObjectValueSerializer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

/**
 * {@link CodeEnum} を API レスポンスで {@code {"code": ..., "label": ...}} 形式に出力する共通シリアライザ。
 *
 * <p>
 * 画面側でコード→ラベルの対応表を重複定義させないため、表示は {@code label}・判定は {@code code} とする。各列挙型・DTO
 * に個別のシリアライズ注釈を付けず、本シリアライザが一括で担う（{@link JacksonComponent} で登録）。デシリアライズ（入力）は
 * 既定どおりコード文字列で行うため本シリアライザの対象外。
 */
@JacksonComponent
public class CodeEnumSerializer extends ObjectValueSerializer<CodeEnum> {

    @Override
    protected void serializeObject(
            CodeEnum value,
            JsonGenerator jgen,
            SerializationContext context) {
        jgen.writeStringProperty("code", value.getCode());
        jgen.writeStringProperty("label", value.getLabel());
    }
}
