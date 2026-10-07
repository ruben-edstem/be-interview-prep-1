package com.edstem.interviewprep.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.interviewprep.urlshortener.entity.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository repository;

    @Test
    void savesWithGeneratedIdCreatedAtAndZeroVisits() {
        ShortUrl saved = repository.saveAndFlush(shortUrl("abc12345"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVisitCount()).isZero();
    }

    @Test
    void findsByCodeAndReportsExistence() {
        repository.saveAndFlush(shortUrl("abc12345"));

        assertThat(repository.findByCode("abc12345")).isPresent();
        assertThat(repository.findByCode("missing1")).isEmpty();
        assertThat(repository.existsByCode("abc12345")).isTrue();
        assertThat(repository.existsByCode("missing1")).isFalse();
    }

    @Test
    void rejectsDuplicateCode() {
        repository.saveAndFlush(shortUrl("abc12345"));

        assertThatThrownBy(() -> repository.saveAndFlush(shortUrl("abc12345")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void incrementsVisitCountOnlyForMatchingCode() {
        repository.saveAndFlush(shortUrl("abc12345"));
        repository.saveAndFlush(shortUrl("other123"));

        int updated = repository.incrementVisitCount("abc12345");
        repository.incrementVisitCount("abc12345");

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findByCode("abc12345").orElseThrow().getVisitCount()).isEqualTo(2);
        assertThat(repository.findByCode("other123").orElseThrow().getVisitCount()).isZero();
    }

    @Test
    void incrementReturnsZeroForUnknownCode() {
        int updated = repository.incrementVisitCount("missing1");

        assertThat(updated).isZero();
    }

    private ShortUrl shortUrl(String code) {
        return ShortUrl.builder().code(code).originalUrl("https://example.com/" + code).build();
    }
}
