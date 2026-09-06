package com.holonplatform.multitenant.invitations.exception;

public class InvitationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public InvitationException(String message) { super(message); }
    public InvitationException(String message, Throwable cause) { super(message, cause); }
}

