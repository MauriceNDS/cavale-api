package com.cavale.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cavale.common.security.JwtProperties;
import com.cavale.user.domain.RefreshToken;
import com.cavale.user.domain.User;
import com.cavale.user.repository.RefreshTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private UserService userService;

    private RefreshTokenService service;

    /** Stands in for the table: rows by hash, so rotation can be followed. */
    private final Map<String, RefreshToken> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("test-only-jwt-secret-0123456789-abcdefghij",
                Duration.ofHours(24), Duration.ofDays(180), Duration.ofDays(60), true);
        service = new RefreshTokenService(repository, userService, properties);

        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
            stored.put(token.getTokenHash(), token);
            return token;
        });
        lenient().when(repository.findByTokenHash(any()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        lenient().when(userService.getById(USER)).thenReturn(user());
    }

    private static User user() {
        User user = new User("a@b.c", "hash", "Arsène");
        ReflectionTestUtils.setField(user, "id", USER);
        return user;
    }

    @Test
    void issuesASecretThatIsNeverItselfStored() {
        var issued = service.issueFor(USER);

        assertThat(issued.secret()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(Instant.now().plus(Duration.ofDays(59)));
        assertThat(stored).hasSize(1);
        assertThat(stored.keySet()).doesNotContain(issued.secret());
        assertThat(stored.values().iterator().next().getTokenHash())
                .isEqualTo(RefreshTokenService.hash(issued.secret()));
    }

    @Test
    void twoTokensNeverCollide() {
        assertThat(service.issueFor(USER).secret()).isNotEqualTo(service.issueFor(USER).secret());
    }

    @Test
    void rotatingSpendsTheOldTokenAndReturnsANewOne() {
        var first = service.issueFor(USER);

        var rotated = service.rotate(first.secret());

        assertThat(rotated.user().getId()).isEqualTo(USER);
        assertThat(rotated.refresh().secret()).isNotEqualTo(first.secret());
        RefreshToken spent = stored.get(RefreshTokenService.hash(first.secret()));
        assertThat(spent.getRevokedAt()).isNotNull();
        assertThat(spent.getReplacedBy()).isNotNull();
    }

    @Test
    void theSuccessorItselfRotates() {
        var first = service.issueFor(USER);
        var second = service.rotate(first.secret()).refresh();

        assertThat(service.rotate(second.secret()).refresh().secret()).isNotBlank();
    }

    /**
     * The theft case: two holders of one secret. Whichever uses it second
     * presents an already-spent token, and since there is no telling the thief
     * from the athlete, every live token of the account is cut.
     */
    @Test
    void replayingASpentTokenCutsEveryLiveTokenOfTheAccount() {
        var first = service.issueFor(USER);
        service.rotate(first.secret());

        assertThatThrownBy(() -> service.rotate(first.secret()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeAllForUser(eq(USER), any(Instant.class));
    }

    @Test
    void anUnknownTokenIsRejectedWithoutCuttingAnything() {
        assertThatThrownBy(() -> service.rotate("never-issued"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void anExpiredTokenIsRejected() {
        var issued = service.issueFor(USER);
        RefreshToken token = stored.get(RefreshTokenService.hash(issued.secret()));
        ReflectionTestUtils.setField(token, "expiresAt", Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.rotate(issued.secret()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // Expiry is not theft — the rest of the account's tokens stand.
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void signingOutRevokesOnlyThisDevicesToken() {
        var mine = service.issueFor(USER);
        var otherDevice = service.issueFor(USER);

        service.revoke(mine.secret());

        assertThat(stored.get(RefreshTokenService.hash(mine.secret())).getRevokedAt()).isNotNull();
        assertThat(stored.get(RefreshTokenService.hash(otherDevice.secret())).getRevokedAt()).isNull();
    }

    @Test
    void signingOutOnAnAlreadyDeadTokenIsHarmless() {
        service.revoke("never-issued");

        assertThat(stored).isEmpty();
    }
}
