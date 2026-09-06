package com.holonplatform.multitenant.config;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers {@code com.holonplatform.multitenant} (which contains the JPA-mapped
 * {@link com.holonplatform.multitenant.TenantDetails}) as an additional entity-scan
 * package via Spring Boot's {@link EntityScanPackages}, so consuming applications don't
 * need to widen their own {@code @EntityScan}/{@code @EnableJpaRepositories} to include
 * holon-saas's internal packages just to make {@code TenantDetails} visible to Hibernate.
 *
 * <p>Without this, any {@code BeanDatastoreHelper}-backed query against
 * {@code TenantDetails} (e.g. {@code TenantDetailsDatastoreHelper}, used by
 * {@code tenant-onboarding}) fails at runtime with
 * {@code UnknownEntityException: Could not resolve root entity 'tenantdetails'} — the
 * entity is never registered with Hibernate at all, regardless of its
 * {@code @Entity(name = ...)} value.
 */
class TenantCoreEntityScanRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		EntityScanPackages.register(registry, "com.holonplatform.multitenant");
	}
}
