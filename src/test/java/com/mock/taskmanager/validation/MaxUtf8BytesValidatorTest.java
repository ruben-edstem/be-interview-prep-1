package com.mock.taskmanager.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

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
    void acceptsAValueWithinTheByteLimit() {
        assertThat(validator.validate(new Holder("a".repeat(5)))).isEmpty();
    }

    @Test
    void acceptsAValueOfExactlyTheByteLimit() {
        assertThat(validator.validate(new Holder("é".repeat(2) + "a"))).isEmpty();
    }

    @Test
    void rejectsAValueWhoseCharactersFitButWhoseBytesDoNot() {
        String value = "é".repeat(3);

        assertThat(value.length()).isLessThanOrEqualTo(5);
        assertThat(validator.validate(new Holder(value))).hasSize(1);
    }

    @Test
    void acceptsNullBecauseRequirednessIsAnotherConstraint() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
    }

    private record Holder(@MaxUtf8Bytes(5) String value) {
    }
}
