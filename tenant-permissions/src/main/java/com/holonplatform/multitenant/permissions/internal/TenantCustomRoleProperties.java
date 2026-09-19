package com.holonplatform.multitenant.permissions.internal;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.PropertySet;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;

import java.time.Instant;

/**
 * Holon {@link PropertySet} for {@link TenantCustomRoleEntity}.
 */
public interface TenantCustomRoleProperties {

    NumericProperty<Long> ID = NumericProperty.create("id", Long.class);

    StringProperty TENANT_ID = StringProperty.create("tenantId")
            .withValidator(Validator.notBlank());

    StringProperty ROLE_NAME = StringProperty.create("roleName")
            .withValidator(Validator.notBlank());

    StringProperty DISPLAY_NAME = StringProperty.create("displayName");

    StringProperty DESCRIPTION = StringProperty.create("description");

    StringProperty BASE_ROLE = StringProperty.create("baseRole");

    TemporalProperty<Instant> CREATED_AT = TemporalProperty.create("createdAt", Instant.class);
}
