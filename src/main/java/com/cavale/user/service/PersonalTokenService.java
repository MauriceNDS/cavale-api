package com.cavale.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.user.domain.PersonalToken;
import com.cavale.user.domain.User;
import com.cavale.user.dto.PersonalTokenResponse;
import com.cavale.user.repository.PersonalTokenRepository;

/**
 * Lifecycle of personal access tokens: mint (with a label naming the app that
 * holds it), list, revoke one. The JWT is returned exactly once at issue time;
 * only its jti is kept, so a leaked database still leaks no credentials.
 */
@Service
public class PersonalTokenService {

    private final PersonalTokenRepository personalTokenRepository;
    private final TokenService tokenService;
    private final UserService userService;

    public PersonalTokenService(PersonalTokenRepository personalTokenRepository,
                                TokenService tokenService, UserService userService) {
        this.personalTokenRepository = personalTokenRepository;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @Transactional
    public IssuedPat issue(UUID userId, String label) {
        User user = userService.getById(userId);
        UUID jti = UUID.randomUUID();
        TokenService.IssuedToken issued = tokenService.issuePersonalToken(user, jti);
        PersonalToken row = personalTokenRepository.save(
                new PersonalToken(userId, label.trim(), jti, Instant.now(), issued.expiresAt()));
        return new IssuedPat(row.getId(), row.getLabel(), issued.token(), issued.expiresAt());
    }

    @Transactional(readOnly = true)
    public List<PersonalTokenResponse> list(UUID userId) {
        return personalTokenRepository.findByUserIdOrderByIssuedAtDesc(userId).stream()
                .map(PersonalTokenResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        PersonalToken token = personalTokenRepository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Personal token", tokenId));
        token.revoke(Instant.now());
    }

    /** Issue-time payload: the raw token appears here and never again. */
    public record IssuedPat(UUID id, String label, String token, Instant expiresAt) {
    }
}
