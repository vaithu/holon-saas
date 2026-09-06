package com.holonplatform.multitenant.whatsapp;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.PropertySet;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;

import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for {@link WhatsAppConversation}.
 *
 * <p>Defines the subset of {@link WhatsAppConversation} fields used as query/sort criteria by
 * {@code internal.WhatsAppConversationDatastoreHelper}. Field name strings match the JavaBean
 * accessor names on {@link WhatsAppConversation} (required by Holon's
 * {@code BeanDatastoreHelper}, which uses {@code java.beans.Introspector} internally).</p>
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #WINDOW_KEY} — natural primary key ({@code tenantId::customerPhone})</li>
 *   <li>{@link #TENANT_ID} — owning tenant</li>
 *   <li>{@link #CUSTOMER_PHONE} — customer phone (E.164)</li>
 *   <li>{@link #EXPIRES_AT} — when the 24-hour free-reply window closes; a window is
 *       considered "open" while {@code EXPIRES_AT} is in the future</li>
 * </ul>
 */
public interface WhatsAppConversationProperties {

    /** Composite natural key: {@code tenantId::customerPhone}. */
    StringProperty WINDOW_KEY = StringProperty.create("windowKey")
        .messageCode("whatsapp.conversation.window_key")
        .withValidator(Validator.notBlank());

    /** Tenant that owns this conversation window. */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .messageCode("whatsapp.conversation.tenant_id")
        .withValidator(Validator.notBlank());

    /** Customer phone in E.164 format. */
    StringProperty CUSTOMER_PHONE = StringProperty.create("customerPhone")
        .messageCode("whatsapp.conversation.customer_phone")
        .withValidator(Validator.notBlank());

    /** When the 24-hour free-reply window closes. */
    TemporalProperty<Instant> EXPIRES_AT = TemporalProperty.instant("expiresAt")
        .messageCode("whatsapp.conversation.expires_at");
}
