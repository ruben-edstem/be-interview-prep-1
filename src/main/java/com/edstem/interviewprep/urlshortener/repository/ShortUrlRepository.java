package com.edstem.interviewprep.urlshortener.repository;

import com.edstem.interviewprep.urlshortener.entity.ShortUrl;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    @Modifying(clearAutomatically = true)
    @Query("update ShortUrl s set s.visitCount = s.visitCount + 1 where s.code = :code")
    int incrementVisitCount(@Param("code") String code);
}
