package com.system_server.ai_demo.commons.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

/**
 * {@link DateRange} の検証本体。
 *
 * <p>
 * 対象オブジェクトから {@link DateRange#from()}（自）・{@link DateRange#to()}（至）のプロパティ値を取得し、「自 ≤ 至
 * （同日を含む）」であることを検証する。違反時のエラー付与先とメッセージは {@link DateRange#bound()} に従い、下限モードは至の
 * 項目に「以降」の、上限モードは自の項目に「以前」の文言を紐づける。いずれかが {@link LocalDate} でない、または未入力 （{@code null}）の場合は対象外とする（値域は
 * {@link DateInBounds} 側で判定する）。
 */
public class DateRangeValidator implements ConstraintValidator<DateRange, Object> {

    private String from;
    private String to;
    private DateBound bound;
    private String message;

    @Override
    public void initialize(DateRange constraintAnnotation) {
        this.from = constraintAnnotation.from();
        this.to = constraintAnnotation.to();
        this.bound = constraintAnnotation.bound();
        this.message = constraintAnnotation.message();
    }

    @Override
    public boolean isValid(Object target, ConstraintValidatorContext context) {
        if (target == null) {
            return true;
        }
        BeanWrapper wrapper = new BeanWrapperImpl(target);
        Object fromValue = wrapper.getPropertyValue(from);
        Object toValue = wrapper.getPropertyValue(to);
        if (!(fromValue instanceof LocalDate) || !(toValue instanceof LocalDate)) {
            return true;
        }
        LocalDate fromDate = (LocalDate) fromValue;
        LocalDate toDate = (LocalDate) toValue;
        if (!toDate.isBefore(fromDate)) {
            return true;
        }
        String errorField = bound == DateBound.UPPER ? from : to;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(resolveTemplate()).addPropertyNode(errorField)
                .addConstraintViolation();
        return false;
    }

    private String resolveTemplate() {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return bound == DateBound.UPPER ? CheckMessages.DATE_UPPER_LIMIT
                : CheckMessages.DATE_LOWER_LIMIT;
    }
}
