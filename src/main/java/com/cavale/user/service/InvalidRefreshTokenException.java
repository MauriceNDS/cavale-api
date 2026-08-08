package com.cavale.user.service;

/**
 * The presented refresh token is unknown, expired or already spent. All three
 * read the same to the client on purpose: telling them apart would confirm to
 * a thief that a stolen secret was once real.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
