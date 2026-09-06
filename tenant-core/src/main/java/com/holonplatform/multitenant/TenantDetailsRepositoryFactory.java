package com.holonplatform.multitenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.BeanFactory;

/**
 * Constructs a {@link TenantDetailsRepository} proxy programmatically, for
 * {@code @AutoConfiguration} classes that need it without relying on
 * {@code @EnableJpaRepositories} component scanning (which would never discover this
 * interface — it lives in {@code tenant-core}, outside any consuming application's own base
 * package).
 *
 * <p>This is a thin, {@code TenantDetailsRepository}-specific convenience wrapper around
 * {@link HolonSaasRepositoryFactory}, kept for backward compatibility and call-site brevity.
 * See {@link HolonSaasRepositoryFactory} for the generic factory (and the rationale for why it
 * must build the proxy via {@code JpaRepositoryFactoryBean} rather than a bare
 * {@code JpaRepositoryFactory}) — use that class directly for any other framework repository
 * needing the same kind of programmatic construction.
 */
public final class TenantDetailsRepositoryFactory {

	private TenantDetailsRepositoryFactory() {
	}

	/**
	 * Builds a fully-functional, transactional {@link TenantDetailsRepository}.
	 *
	 * @param entityManagerFactory the JPA {@code EntityManagerFactory} to bind to; must not be null
	 * @param beanFactory           the enclosing Spring {@code BeanFactory}, needed to resolve the
	 *                              {@code PlatformTransactionManager} bean (by the conventional
	 *                              name {@code "transactionManager"}) backing the transactional
	 *                              proxy; must not be null
	 * @return a ready-to-use {@link TenantDetailsRepository}
	 */
	public static TenantDetailsRepository create(EntityManagerFactory entityManagerFactory, BeanFactory beanFactory) {
		return HolonSaasRepositoryFactory.create(TenantDetailsRepository.class, entityManagerFactory, beanFactory);
	}
}
