package com.system_server.ai_demo.commons.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateInBoundsValidatorTest {

    private final DateInBoundsValidator validator = new DateInBoundsValidator();

    @Test
    void valid_whenInRange() {
        assertTrue(validator.isValid(LocalDate.of(2026, 6, 25), null));
    }

    @Test
    void valid_atBoundaries() {
        assertTrue(validator.isValid(LocalDate.of(1900, 1, 1), null));
        assertTrue(validator.isValid(LocalDate.of(9999, 12, 31), null));
    }

    @Test
    void valid_whenNull() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void invalid_whenOutsideBounds() {
        assertFalse(validator.isValid(LocalDate.of(1899, 12, 31), null));
        assertFalse(validator.isValid(LocalDate.of(10000, 1, 1), null));
    }
}
