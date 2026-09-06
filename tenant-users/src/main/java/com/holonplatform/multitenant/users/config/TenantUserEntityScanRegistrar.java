package com.holonplatform.multitenant.users.config;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers {@code com.holonplatform.multitenant.users} (containing {@code TenantUser})
 * as an additional entity-scan package via Spring Boot's {@link EntityScanPackages}, so
 * consuming applications don't need to widen their own {@code @EntityScan} to include
 * holon-saas's internal packages.
 */
class TenantUserEntityScanRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		EntityScanPackages.register(registry, "com.holonplatform.multitenant.users");
	}
}
