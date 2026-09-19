package com.holonplatform.multitenant.permissions.internal;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.BooleanProperty;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.PropertySet;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;

import java.time.Instant;

/**
 * Holon {@link PropertySet} for {@link TenantPermissionOverrideEntity}.
 */
public interface TenantPermissionOverrideProperties {

    NumericProperty<Long> ID = NumericProperty.create("id", Long.class);

    StringProperty TENANT_ID = StringProperty.create("tenantId")
            .withValidator(Validator.notBlank());

    StringProperty ROLE_NAME = StringProperty.create("roleName")
            .withValidator(Validator.notBlank());

    StringProperty VIEW_ID = StringProperty.create("viewId")
            .withValidator(Validator.notBlank());

    StringProperty ACTION = StringProperty.create("action")
            .withValidator(Validator.notBlank());

    BooleanProperty ALLOWED = BooleanProperty.create("allowed");

    TemporalProperty<Instant> CREATED_AT = TemporalProperty.create("createdAt", Instant.class);

    TemporalProperty<Instant> UPDATED_AT = TemporalProperty.create("updatedAt", Instant.class);
}
