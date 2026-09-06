package com.holonplatform.multitenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Constructs Spring Data JPA repository proxies programmatically, for
 * {@code @AutoConfiguration} classes that need a repository bean without relying on
 * {@code @EnableJpaRepositories} component scanning — which never discovers repository
 * interfaces that live outside a consuming application's own base package (e.g. framework
 * repositories declared in {@code tenant-core} or other {@code holon-saas} modules).
 *
 * <h2>Why {@link JpaRepositoryFactoryBean}, not {@code JpaRepositoryFactory} directly</h2>
 * <p>An earlier version of this pattern (originally written as a one-off for
 * {@code TenantDetailsRepository}) built the repository via
 * {@code new JpaRepositoryFactory(entityManager).getRepository(...)} directly. That is a
 * genuine, empirically-confirmed bug: Spring Data's transactional behavior for repository
 * proxies (the {@code @Transactional} semantics backing {@code save()}/{@code delete()}/etc.
 * — see {@code SimpleJpaRepository}) is wired by
 * {@link org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport#createRepositoryFactory()},
 * which only runs when the repository is built through the proper {@link JpaRepositoryFactoryBean}
 * lifecycle ({@code setEntityManager}/{@code setBeanFactory}/{@code afterPropertiesSet}) — never
 * when a bare {@code JpaRepositoryFactory} is used standalone. Building the repository the
 * bare way meant every write went straight to {@code EntityManager.merge(...)} with no
 * transaction ever started, failing at runtime with:
 * <pre>{@code
 * org.springframework.dao.InvalidDataAccessApiUsageException: No EntityManager with actual
 * transaction available for current thread - cannot reliably process 'merge' call
 * }</pre>
 * Using {@link JpaRepositoryFactoryBean} here reproduces exactly the same repository proxy
 * {@code @EnableJpaRepositories} component scanning would have produced, transactional
 * behavior included.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MyRepository repo = HolonSaasRepositoryFactory.create(MyRepository.class, entityManagerFactory, beanFactory);
 * }</pre>
 *
 * @see TenantDetailsRepositoryFactory
 */
public final class HolonSaasRepositoryFactory {

	private HolonSaasRepositoryFactory() {
	}

	/**
	 * Builds a fully-functional, transactional Spring Data JPA repository proxy for the given
	 * repository interface.
	 *
	 * @param repositoryInterface   the Spring Data repository interface to build a proxy for;
	 *                              must not be null
	 * @param entityManagerFactory  the JPA {@code EntityManagerFactory} to bind to; must not be
	 *                              null
	 * @param beanFactory           the enclosing Spring {@code BeanFactory}, needed to resolve
	 *                              the {@code PlatformTransactionManager} bean (by the
	 *                              conventional name {@code "transactionManager"}) backing the
	 *                              transactional proxy; must not be null
	 * @param <R>                   the repository type
	 * @param <T>                   the managed entity type
	 * @param <ID>                  the entity identifier type
	 * @return a ready-to-use repository proxy of type {@code R}
	 */
	public static <R extends JpaRepository<T, ID>, T, ID> R create(Class<R> repositoryInterface,
			EntityManagerFactory entityManagerFactory, BeanFactory beanFactory) {
		try {
			JpaRepositoryFactoryBean<R, T, ID> factoryBean = new JpaRepositoryFactoryBean<>(repositoryInterface);
			factoryBean.setEntityManager(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
			factoryBean.setBeanFactory(beanFactory);
			factoryBean.afterPropertiesSet();
			return factoryBean.getObject();
		} catch (Exception e) {
			throw new IllegalStateException(
					"Failed to construct repository proxy for " + repositoryInterface.getName(), e);
		}
	}
}
