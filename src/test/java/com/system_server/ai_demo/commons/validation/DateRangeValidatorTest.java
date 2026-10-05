package com.system_server.ai_demo.commons.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DateRangeValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void valid_whenToIsOnOrAfterFrom() {
        assertTrue(
                validator.validate(new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
                        .isEmpty());
        assertTrue(
                validator.validate(new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                        .isEmpty());
    }

    @Test
    void valid_whenEitherNull() {
        assertTrue(validator.validate(new Period(LocalDate.of(2026, 1, 1), null)).isEmpty());
        assertTrue(validator.validate(new Period(null, LocalDate.of(2026, 1, 1))).isEmpty());
    }

    @Test
    void invalid_whenToIsBeforeFrom_lowerBoundFlagsToField() {
        Set<ConstraintViolation<Period>> violations = validator
                .validate(new Period(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));
        assertEquals(1, violations.size());
        ConstraintViolation<Period> violation = violations.iterator().next();
        assertEquals("to", violation.getPropertyPath().toString());
        assertEquals("開始日以降の日付を入力してください。", violation.getMessage());
    }

    @Test
    void valid_upperBound_whenFromIsOnOrBeforeTo() {
        assertTrue(validator
                .validate(new UpperBoundPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
                .isEmpty());
        assertTrue(validator
                .validate(
                        new UpperBoundPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .isEmpty());
    }

    @Test
    void invalid_upperBound_whenFromIsAfterTo_flagsFromField() {
        Set<ConstraintViolation<UpperBoundPeriod>> violations = validator.validate(
                new UpperBoundPeriod(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));
        assertEquals(1, violations.size());
        ConstraintViolation<UpperBoundPeriod> violation = violations.iterator().next();
        assertEquals("from", violation.getPropertyPath().toString());
        assertEquals("終了日以前の日付を入力してください。", violation.getMessage());
    }

    @DateRange(from = "from", to = "to", boundLabel = "開始日")
    public static class Period {

        private final LocalDate from;
        private final LocalDate to;

        public Period(LocalDate from, LocalDate to) {
            this.from = from;
            this.to = to;
        }

        public LocalDate getFrom() {
            return from;
        }

        public LocalDate getTo() {
            return to;
        }
    }

    @DateRange(from = "from", to = "to", bound = DateBound.UPPER, boundLabel = "終了日")
    public static class UpperBoundPeriod {

        private final LocalDate from;
        private final LocalDate to;

        public UpperBoundPeriod(LocalDate from, LocalDate to) {
            this.from = from;
            this.to = to;
        }

        public LocalDate getFrom() {
            return from;
        }

        public LocalDate getTo() {
            return to;
        }
    }
}
