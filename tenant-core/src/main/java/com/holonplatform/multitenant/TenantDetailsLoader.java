package com.holonplatform.multitenant;

import com.holonplatform.multitenant.exception.TenantNotFoundException;

/**
 * SPI for loading a fully populated {@link TenantDetails} from a persistent store
 * given a raw tenant identifier string resolved by a {@link TenantResolver}.
 *
 * <p><strong>Responsibility separation:</strong> {@link TenantResolver} answers
 * <em>"what is the tenant ID for this HTTP request?"</em>; {@code TenantDetailsLoader}
 * answers <em>"given this ID, what are the tenant's full details?"</em>.
 *
 * <p>Applications must register exactly one {@code TenantDetailsLoader} bean.
 * The availability of this bean is the trigger that activates the
 * {@link com.holonplatform.multitenant.filter.TenantContextFilter}.
 *
 * <p>Typical implementation:
 * <pre>{@code
 * @Service
 * public class JpaTenantDetailsLoader implements TenantDetailsLoader {
 *
 *     private final TenantRepository repository;
 *
 *     @Override
 *     public TenantDetails load(String tenantId) {
 *         return repository.findByTenantId(tenantId)
 *                 .map(entity -> TenantDetails.builder(entity.getTenantId())
 *                         .name(entity.getName())
 *                         .plan(entity.getPlan())
 *                         .status(entity.getStatus())
 *                         .build())
 *                 .orElseThrow(() -> new TenantNotFoundException(tenantId));
 *     }
 * }
 * }</pre>
 *
 * <p>This is a {@link FunctionalInterface} — lambda wiring is valid in tests and
 * auto-configuration fixtures.
 */
@FunctionalInterface
public interface TenantDetailsLoader {

	/**
	 * Loads the {@link TenantDetails} for the given tenant identifier.
	 *
	 * @param tenantId the raw tenant ID as resolved by a {@link TenantResolver};
	 *                 never null
	 * @return a fully populated {@link TenantDetails}; never null
	 * @throws TenantNotFoundException if no tenant with the given ID exists
	 * @throws RuntimeException        on data-access errors (callers treat any
	 *                                 unchecked exception as a load failure)
	 */
	TenantDetails load(String tenantId);
}

