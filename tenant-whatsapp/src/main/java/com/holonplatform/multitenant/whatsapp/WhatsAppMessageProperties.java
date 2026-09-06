package com.holonplatform.multitenant.whatsapp;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.PropertySet;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;

import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for {@link WhatsAppMessage}.
 *
 * <p>Defines the subset of {@link WhatsAppMessage} fields used as query/sort criteria by
 * {@code internal.WhatsAppMessageDatastoreHelper}. Field name strings match the JavaBean
 * accessor names on {@link WhatsAppMessage} (required by Holon's {@code BeanDatastoreHelper},
 * which uses {@code java.beans.Introspector} internally). Enum-valued columns are declared as
 * {@link StringProperty} since they are persisted as {@code @Enumerated(EnumType.STRING)} and
 * filtered/sorted using the enum's {@code name()}.</p>
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #LOCAL_ID} — natural primary key (UUID assigned at creation)</li>
 *   <li>{@link #MESSAGE_ID} — Meta-assigned message id, used for webhook status lookups</li>
 *   <li>{@link #TENANT_ID} — owning tenant</li>
 *   <li>{@link #TO} — recipient phone (E.164)</li>
 *   <li>{@link #FROM} — sender phone-number-id</li>
 *   <li>{@link #CREATED_AT} — local creation timestamp, used for history ordering and
 *       month-range usage-stats queries</li>
 * </ul>
 */
public interface WhatsAppMessageProperties {

    /** Local UUID assigned before the message is submitted to Meta; natural primary key. */
    StringProperty LOCAL_ID = StringProperty.create("localId")
        .messageCode("whatsapp.message.local_id")
        .withValidator(Validator.notBlank());

    /** Meta-assigned message ID (wamid.…), used to correlate webhook delivery-status updates. */
    StringProperty MESSAGE_ID = StringProperty.create("messageId")
        .messageCode("whatsapp.message.message_id");

    /** Tenant that owns this message. */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .messageCode("whatsapp.message.tenant_id")
        .withValidator(Validator.notBlank());

    /** Recipient phone number in E.164 format. */
    StringProperty TO = StringProperty.create("to")
        .messageCode("whatsapp.message.to")
        .withValidator(Validator.notBlank());

    /** Sender phone-number-id from the Meta Business account. */
    StringProperty FROM = StringProperty.create("from")
        .messageCode("whatsapp.message.from");

    /** When the message was created locally; used for history ordering and month-range queries. */
    TemporalProperty<Instant> CREATED_AT = TemporalProperty.instant("createdAt")
        .messageCode("whatsapp.message.created_at");
}
