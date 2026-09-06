package com.holonplatform.multitenant.invitations;

/** Lifecycle states of a {@link UserInvitation}. */
public enum InvitationStatus {

    /** Created, email sent — waiting for the invitee to accept. */
    PENDING,

    /** The invitee clicked the link, set their password, and joined the tenant. */
    ACCEPTED,

    /** Revoked by a tenant admin before acceptance. */
    REVOKED,

    /** Validity window passed without acceptance. Set by {@code cleanupExpired()}. */
    EXPIRED
}

