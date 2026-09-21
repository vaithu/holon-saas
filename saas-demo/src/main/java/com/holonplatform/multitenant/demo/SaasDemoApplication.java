package com.holonplatform.multitenant.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

import com.vaadin.flow.component.page.AppShellConfigurator;

/**
 * Runnable demonstration of the holon-saas framework on H2 with one schema per tenant.
 *
 * <p>The demo walks through the full SaaS lifecycle:
 * <ol>
 *   <li>self-service tenant sign-up, which provisions a dedicated {@code tenant_<id>} schema</li>
 *   <li>tenant login (workspace + email + password)</li>
 *   <li>the owner inviting other users</li>
 *   <li>invitees accepting their invitation and choosing a password</li>
 *   <li>invitees signing in</li>
 *   <li>view-level authorization — each job role reaches only its own view</li>
 *   <li>method-level authorization — {@code @RequiresPermission} on the service layer</li>
 * </ol>
 *
 * <p>{@code @EntityScan} covers {@code com.holonplatform.multitenant} so Hibernate sees the
 * framework's own entities ({@code TenantDetails}, {@code TenantUser}, {@code UserInvitation},
 * the permission entities); several modules additionally contribute their packages through
 * their own entity-scan registrars.
 */
@SpringBootApplication
@EntityScan("com.holonplatform.multitenant")
public class SaasDemoApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(SaasDemoApplication.class, args);
    }
}
