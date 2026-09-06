package com.holonplatform.multitenant.resolver;

import com.holonplatform.multitenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link TenantResolver} that delegates to an ordered list of other resolvers
 * and returns the first non-empty result.
 *
 * <p>Resolvers are tried in ascending {@link TenantResolver#getOrder()} value order
 * (lower order = higher priority). Resolvers with equal order values are tried in
 * the order they were supplied to the constructor.
 *
 * <p>Usage:
 * <pre>{@code
 * TenantResolver resolver = new CompositeTenantResolver(
 *     new SubdomainTenantResolver(),         // order 10  — tried first
 *     new HeaderTenantResolver(),            // order 20  — fallback
 *     new PathPrefixTenantResolver()         // order 30  — last resort
 * );
 * }</pre>
 */
public class CompositeTenantResolver implements TenantResolver {

	private final List<TenantResolver> resolvers;

	/**
	 * Creates a composite from the supplied resolvers, sorted by
	 * {@link TenantResolver#getOrder()} ascending.
	 *
	 * @param resolvers one or more resolvers; must not be null or empty
	 */
	public CompositeTenantResolver(TenantResolver... resolvers) {
		Objects.requireNonNull(resolvers, "resolvers must not be null");
		if (resolvers.length == 0) {
			throw new IllegalArgumentException("At least one TenantResolver is required");
		}
		List<TenantResolver> list = new ArrayList<>(List.of(resolvers));
		list.sort(Comparator.comparingInt(TenantResolver::getOrder));
		this.resolvers = List.copyOf(list);
	}

	/**
	 * Creates a composite from a list of resolvers, sorted by
	 * {@link TenantResolver#getOrder()} ascending.
	 *
	 * @param resolvers resolver list; must not be null or empty
	 */
	public CompositeTenantResolver(List<TenantResolver> resolvers) {
		Objects.requireNonNull(resolvers, "resolvers must not be null");
		if (resolvers.isEmpty()) {
			throw new IllegalArgumentException("At least one TenantResolver is required");
		}
		List<TenantResolver> list = new ArrayList<>(resolvers);
		list.sort(Comparator.comparingInt(TenantResolver::getOrder));
		this.resolvers = List.copyOf(list);
	}

	/**
	 * Returns the first non-empty tenant ID produced by the delegate resolver chain.
	 */
	@Override
	public Optional<String> resolveTenantId(HttpServletRequest request) {
		for (TenantResolver resolver : resolvers) {
			Optional<String> tenantId = resolver.resolveTenantId(request);
			if (tenantId.isPresent()) {
				return tenantId;
			}
		}
		return Optional.empty();
	}

	/** Returns the immutable, ordered list of delegate resolvers. */
	public List<TenantResolver> getResolvers() {
		return resolvers;
	}
}

