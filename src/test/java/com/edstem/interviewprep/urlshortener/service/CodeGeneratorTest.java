package com.edstem.interviewprep.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edstem.interviewprep.urlshortener.entity.ShortUrl;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void generatesUrlSafeCodesOfConfiguredLength() {
        for (int i = 0; i < 1000; i++) {
            String code = generator.generate();

            assertThat(code).hasSize(ShortUrl.CODE_LENGTH).matches("[A-Za-z0-9]+");
        }
    }

    @Test
    void generatesDistinctCodes() {
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }

        assertThat(codes).hasSize(1000);
    }
}
