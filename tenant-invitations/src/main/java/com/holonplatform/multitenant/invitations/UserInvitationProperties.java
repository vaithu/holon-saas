package com.holonplatform.multitenant.invitations;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.PropertySet;
import com.holonplatform.core.property.PathProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;

import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for {@link UserInvitation}.
 *
 * Defines metadata for user invitations including field names, labels, validation rules,
 * and type information. Used by Holon's UI components (PropertyListing, PropertyForm)
 * for dynamic field generation, validation, and data binding, and by
 * {@code UserInvitationDatastoreHelper} for type-safe Datastore queries.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #TOKEN} — UUID token embedded in the invitation link (primary key)</li>
 *   <li>{@link #TENANT_ID} — tenant the user is being invited to (required, max 64 chars)</li>
 *   <li>{@link #EMAIL} — invitee's email address (required, max 255 chars)</li>
 *   <li>{@link #INVITED_BY_USER_ID} — userId of the admin who sent the invitation (optional)</li>
 *   <li>{@link #PLATFORM_ROLE} — the tenant role the user will receive on acceptance (required)</li>
 *   <li>{@link #JOB_ROLE} — CRM job role stored in user attributes on acceptance (optional)</li>
 *   <li>{@link #STATUS} — current lifecycle state (required)</li>
 *   <li>{@link #CREATED_AT} — when the invitation was sent (server-generated)</li>
 *   <li>{@link #EXPIRES_AT} — when the invitation token becomes invalid (server-generated)</li>
 *   <li>{@link #ACCEPTED_AT} — when the invitee accepted (optional, null until accepted)</li>
 * </ul>
 */
public interface UserInvitationProperties {

    // ── Identifiers ────────────────────────────────────────────────────────────

    /**
     * UUID token embedded in the invitation link (primary key).
     */
    StringProperty TOKEN = StringProperty.create("token")
        .messageCode("invitation.token")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(64));

    /**
     * Tenant identifier the user is being invited to.
     * Required, max 64 characters.
     */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .messageCode("invitation.tenant_id")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(64));

    // ── Invitation details ─────────────────────────────────────────────────────

    /**
     * Invitee's email address.
     * Required, max 255 characters.
     */
    StringProperty EMAIL = StringProperty.create("email")
        .messageCode("invitation.email")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    /**
     * userId of the admin who sent the invitation (optional).
     */
    StringProperty INVITED_BY_USER_ID = StringProperty.create("invitedByUserId")
        .messageCode("invitation.invited_by_user_id")
        .withValidator(maxLength(255));

    /**
     * The tenant role the user will receive on acceptance (VIEWER, MEMBER, ADMIN, OWNER).
     * Required.
     */
    StringProperty PLATFORM_ROLE = StringProperty.create("platformRole")
        .messageCode("invitation.platform_role")
        .withValidator(Validator.notBlank());

    /**
     * CRM job role (e.g. "ACCOUNTANT", "DRIVER") stored in user attributes on acceptance (optional).
     */
    StringProperty JOB_ROLE = StringProperty.create("jobRole")
        .messageCode("invitation.job_role")
        .withValidator(maxLength(100));

    /**
     * Current lifecycle state of the invitation (PENDING, ACCEPTED, REVOKED, EXPIRED).
     * Required.
     *
     * <p>Typed as the {@link InvitationStatus} enum rather than a String because the mapped
     * entity field is an enum: filtering this path with a String value is rejected by the JPA
     * provider ("argument [PENDING] is not assignable to InvitationStatus").
     */
    PathProperty<InvitationStatus> STATUS = PathProperty.create("status", InvitationStatus.class)
        .messageCode("invitation.status")
        .withValidator(Validator.notNull());

    // ── Timestamps ─────────────────────────────────────────────────────────────

    /**
     * UTC timestamp when the invitation was sent.
     * Server-generated, always set when the record is created.
     */
    TemporalProperty<Instant> CREATED_AT = TemporalProperty.instant("createdAt")
        .messageCode("invitation.created_at");

    /**
     * UTC timestamp when the invitation token becomes invalid.
     * Server-generated, always set when the record is created.
     */
    TemporalProperty<Instant> EXPIRES_AT = TemporalProperty.instant("expiresAt")
        .messageCode("invitation.expires_at");

    /**
     * UTC timestamp of the invitee's acceptance.
     * Optional, null until the invitation is accepted.
     */
    TemporalProperty<Instant> ACCEPTED_AT = TemporalProperty.instant("acceptedAt")
        .messageCode("invitation.accepted_at");

    // ── Validators ─────────────────────────────────────────────────────────────

    /**
     * Builds a validator rejecting {@link String} values longer than {@code max} characters.
     * Null values are considered valid (combine with {@link Validator#notBlank()} to also require a value).
     */
    private static Validator<String> maxLength(int max) {
        return Validator.create(value -> value == null || value.length() <= max,
            "must not exceed " + max + " characters");
    }
}
