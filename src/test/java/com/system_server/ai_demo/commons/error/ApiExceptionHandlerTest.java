package com.system_server.ai_demo.commons.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.system_server.ai_demo.commons.validation.AllowedCharacters;
import com.system_server.ai_demo.commons.validation.CheckMessages;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleMethodArgumentNotValid_keepsSmallerRank_andGlobal() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "form");
        binding.addError(new FieldError("form", "name", null, false,
                new String[] {"AllowedCharacters"}, null, CheckMessages.ALLOWED_CHARACTERS));
        binding.addError(new FieldError("form", "name", null, false, new String[] {"Size"}, null,
                "3文字以内で入力してください。"));
        binding.addError(new ObjectError("form", "全体エラー"));
        MethodParameter parameter = new MethodParameter(
                ApiExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(parameter, binding);

        ErrorResponse response = handler.handleMethodArgumentNotValid(ex);

        // name は Size(番号小) が AllowedCharacters(番号大) より優先され 1 件に集約される
        List<ErrorItem> nameItems =
                response.errors().stream().filter(e -> "name".equals(e.field())).toList();
        assertEquals(1, nameItems.size());
        assertEquals("3文字以内で入力してください。", nameItems.get(0).message());
        // 項目に紐づかないグローバルエラーは field=null で保持される
        assertTrue(response.errors().stream()
                .anyMatch(e -> e.field() == null && "全体エラー".equals(e.message())));
    }

    @Test
    void handleConstraintViolation_keepsSmallerRank() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample("ｱｲｳｴ"));
            ConstraintViolationException ex = new ConstraintViolationException(violations);

            ErrorResponse response = handler.handleConstraintViolation(ex);

            assertEquals(1, response.errors().size());
            assertEquals("name", response.errors().get(0).field());
            assertEquals("3文字以内で入力してください。", response.errors().get(0).message());
        }
    }

    @Test
    void handleTypeMismatch_returnsIntegerMessage() throws Exception {
        MethodParameter parameter = new MethodParameter(
                ApiExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("abc",
                Integer.class, "page", parameter, new NumberFormatException());

        ErrorResponse response = handler.handleTypeMismatch(ex);

        // 型変換失敗は「整数」チェックの違反として、当該項目に INTEGER_VALUE を 1 件返す
        assertEquals(1, response.errors().size());
        assertEquals("page", response.errors().get(0).field());
        assertEquals(CheckMessages.INTEGER_VALUE, response.errors().get(0).message());
    }

    @Test
    void handleMissingParameter_returnsRequiredMessage() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("keyword", "String");

        ErrorResponse response = handler.handleMissingParameter(ex);

        // 必須リクエストパラメータ欠落は「必須入力」チェックの違反として、当該項目に REQUIRED_INPUT を 1 件返す
        assertEquals(1, response.errors().size());
        assertEquals("keyword", response.errors().get(0).field());
        assertEquals(CheckMessages.REQUIRED_INPUT, response.errors().get(0).message());
    }

    @SuppressWarnings("unused")
    private void sample(String arg) {}

    public static class Sample {

        @Size(max = 3, message = CheckMessages.MAX_LENGTH)
        @AllowedCharacters
        private final String name;

        public Sample(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
