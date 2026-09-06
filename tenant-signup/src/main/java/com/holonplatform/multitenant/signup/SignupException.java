package com.holonplatform.multitenant.signup;

/**
 * Thrown when a self-service sign-up request is invalid (malformed workspace id)
 * or conflicts with an existing tenant/account.
 */
public class SignupException extends RuntimeException {

    public SignupException(String message) {
        super(message);
    }

    public SignupException(String message, Throwable cause) {
        super(message, cause);
    }
}
