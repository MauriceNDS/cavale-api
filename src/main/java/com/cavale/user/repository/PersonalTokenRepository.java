package com.cavale.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cavale.user.domain.PersonalToken;

public interface PersonalTokenRepository extends JpaRepository<PersonalToken, UUID> {

    List<PersonalToken> findByUserIdOrderByIssuedAtDesc(UUID userId);

    Optional<PersonalToken> findByIdAndUserId(UUID id, UUID userId);

    /** The access gate's question: is this jti still an honoured credential? */
    boolean existsByJtiAndRevokedAtIsNull(UUID jti);

    /** Dead rows (revoked or expired), newest first — pruning fodder. */
    @Query("select t from PersonalToken t where t.userId = :userId"
            + " and (t.revokedAt is not null or t.expiresAt < :now)"
            + " order by t.issuedAt desc")
    List<PersonalToken> findDeadByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
