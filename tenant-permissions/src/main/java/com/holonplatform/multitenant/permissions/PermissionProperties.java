package com.holonplatform.multitenant.permissions;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;
import com.holonplatform.core.property.PropertySet;

import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for
 * {@link com.holonplatform.multitenant.permissions.internal.PermissionEntity}.
 *
 * <p>Defines metadata for tenant-scoped role permission grants, used for type-safe
 * Holon Datastore query filters ({@code QueryFilter.eq(...)}) instead of raw property name strings.</p>
 */
public interface PermissionProperties {

    /**
     * Surrogate identity of the permission grant.
     */
    NumericProperty<Long> ID = NumericProperty.create("id", Long.class);

    /**
     * Tenant identifier this permission grant belongs to (required).
     */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .withValidator(Validator.notBlank());

    /**
     * Role name this permission is granted to (required).
     */
    StringProperty ROLE_NAME = StringProperty.create("roleName")
        .withValidator(Validator.notBlank());

    /**
     * Permission name granted to the role (required).
     */
    StringProperty PERMISSION = StringProperty.create("permission")
        .withValidator(Validator.notBlank());

    /**
     * UTC timestamp when the permission grant was created.
     */
    TemporalProperty<Instant> CREATED_AT = TemporalProperty.create("createdAt", Instant.class);
}

