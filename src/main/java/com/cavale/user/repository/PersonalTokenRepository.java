package com.cavale.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.user.domain.PersonalToken;

public interface PersonalTokenRepository extends JpaRepository<PersonalToken, UUID> {

    List<PersonalToken> findByUserIdOrderByIssuedAtDesc(UUID userId);

    Optional<PersonalToken> findByIdAndUserId(UUID id, UUID userId);

    /** The access gate's question: is this jti still an honoured credential? */
    boolean existsByJtiAndRevokedAtIsNull(UUID jti);
}
