package com.holonplatform.multitenant.invitations.exception;

/** Thrown when attempting to accept an invitation that is not in PENDING state. */
public class InvitationAlreadyAcceptedException extends InvitationException {
    private static final long serialVersionUID = 1L;
    public InvitationAlreadyAcceptedException(String token, String status) {
        super("Invitation token '" + token + "' cannot be accepted — current status: " + status);
    }
}

