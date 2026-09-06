package com.holonplatform.multitenant.invitations.exception;

/** Thrown when the invitation token is valid but its validity window has passed. */
public class InvitationExpiredException extends InvitationException {
    private static final long serialVersionUID = 1L;
    private final String token;
    public InvitationExpiredException(String token) {
        super("Invitation token '" + token + "' has expired.");
        this.token = token;
    }
    public String getToken() { return token; }
}

