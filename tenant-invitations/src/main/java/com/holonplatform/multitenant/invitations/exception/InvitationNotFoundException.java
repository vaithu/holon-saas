package com.holonplatform.multitenant.invitations.exception;

/** Thrown when no invitation can be found for the supplied token. */
public class InvitationNotFoundException extends InvitationException {
    private static final long serialVersionUID = 1L;
    private final String token;
    public InvitationNotFoundException(String token) {
        super("No invitation found for token '" + token + "'.");
        this.token = token;
    }
    public String getToken() { return token; }
}

