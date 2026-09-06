package com.holonplatform.multitenant.convert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JPA {@link AttributeConverter} that persists a {@code Map<String, String>} as a single
 * JSON-serialized text column, instead of mapping it as a separate {@code @ElementCollection}
 * join table.
 *
 * <h2>Why this exists</h2>
 * <p>Entities across {@code holon-saas} (e.g. {@code TenantUser#attributes},
 * {@code UserInvitation#attributes}, {@code WhatsAppMessage#templateParams}) originally mapped
 * this kind of "arbitrary key-value metadata" field as {@code @ElementCollection(fetch =
 * FetchType.EAGER)} backed by a
 * separate join table. This is a genuine, serious bug: Holon's {@code BeanDatastoreHelper}
 * always projects the entire bean in a single query, and its generated JPQL references the
 * collection-valued property directly in the {@code SELECT} list — which Hibernate always
 * translates to an <strong>inner join</strong> against the collection table (a JPQL-level
 * behavior, not something a mapping-level {@code @Fetch} hint can override). The practical
 * effect: whenever the map happens to be empty (the common case — most entities are created
 * with no extra attributes), the inner join has no matching row, and the <em>entire parent
 * entity silently disappears</em> from every query result — not an error, just an empty
 * result set, making it look like the row doesn't exist even though it does.
 *
 * <p>Storing the map as a single JSON column on the owning row eliminates the join (and
 * therefore the entire class of bug) permanently: there is no second table to join against,
 * so an empty map is just the literal string {@code "{}"} in one column, never an absent row.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Convert(converter = StringMapJsonConverter.class)
 * @Column(name = "attributes", columnDefinition = "TEXT")
 * private Map<String, String> attributes = new LinkedHashMap<>();
 * }</pre>
 */
@Converter
public class StringMapJsonConverter implements AttributeConverter<Map<String, String>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<LinkedHashMap<String, String>> TYPE_REF = new TypeReference<>() {
	};
	private static final String EMPTY_OBJECT = "{}";

	@Override
	public String convertToDatabaseColumn(Map<String, String> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return EMPTY_OBJECT;
		}
		try {
			return MAPPER.writeValueAsString(attribute);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize attribute map to JSON: " + attribute, e);
		}
	}

	@Override
	public Map<String, String> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return new LinkedHashMap<>();
		}
		try {
			return MAPPER.readValue(dbData, TYPE_REF);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to deserialize attribute map from JSON: " + dbData, e);
		}
	}
}
