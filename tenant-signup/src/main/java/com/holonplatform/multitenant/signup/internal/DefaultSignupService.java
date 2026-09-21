package com.holonplatform.multitenant.signup.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.onboarding.TenantOnboardingRequest;
import com.holonplatform.multitenant.onboarding.TenantOnboardingResult;
import com.holonplatform.multitenant.onboarding.TenantOnboardingService;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import com.holonplatform.multitenant.onboarding.exception.TenantAlreadyExistsException;
import com.holonplatform.multitenant.signup.AccountCredentialsStore;
import com.holonplatform.multitenant.signup.SignupException;
import com.holonplatform.multitenant.signup.SignupRequest;
import com.holonplatform.multitenant.signup.SignupService;
import com.holonplatform.multitenant.users.TenantRole;
import com.holonplatform.multitenant.users.TenantUserException;
import com.holonplatform.multitenant.users.TenantUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Default {@link SignupService} implementation. See the interface Javadoc for the
 * four-step orchestration this class performs.
 *
 * <h2>Why {@code signup(...)} is deliberately NOT {@code @Transactional}</h2>
 * <p>An earlier version of this class wrapped the whole method in a single
 * {@code @Transactional} boundary. That is fundamentally incompatible with a
 * schema-per-tenant architecture and caused a genuine, hard-to-diagnose bug: the very first
 * SQL statement in the method — {@code credentialsStore.existsByEmail(...)}, which runs
 * against the shared <em>platform</em> schema, before the tenant even exists — causes
 * Hibernate to acquire a physical JDBC connection for the whole transaction right then, with
 * {@link TenantContext} correctly empty at that moment. That single physical connection is
 * then <strong>reused for every subsequent statement in the same transaction</strong>
 * (standard JPA/Hibernate behavior — a connection cannot switch schemas mid-transaction),
 * completely ignoring the later {@code TenantContext.set(tenant)} call below: every
 * downstream write (this class's own {@code invite()}/{@code activate()} calls, and any
 * {@code @EventListener} reacting to the onboarding-completed event) silently kept using the
 * platform connection instead of the new tenant's own schema, failing with
 * {@code Table "TENANT_USERS" not found} (or similar) despite {@link TenantContext} being
 * bound correctly in the source code.
 *
 * <p>Removing {@code @Transactional} here means each step below (platform check,
 * onboarding, tenant-user creation, credential storage) commits independently, each
 * correctly resolving its own JDBC connection via whatever {@link TenantContext} is bound
 * at the time it runs. This sacrifices cross-schema atomicity — a failure partway through
 * (e.g. {@code invite()} throwing) can leave a provisioned tenant with no owner user — but
 * that trade-off is unavoidable in this architecture: a single local JDBC transaction can
 * never legitimately span both the platform schema and a dynamically-selected tenant schema
 * without distributed/XA transactions, which are not configured here.
 *
 * <h2>Resuming a partially-failed signup</h2>
 * <p>Rather than permanently stranding a tenant whose onboarding succeeded but whose
 * owner-user-creation or credential-storage step failed afterwards, {@link #signup(SignupRequest)}
 * is safe to simply call again with the identical request: if {@code onboardingService.onboard(...)}
 * reports the tenant already exists (see {@link TenantAlreadyExistsException}), and that
 * existing tenant is {@link TenantStatus#ACTIVE}, this method resumes from wherever the
 * previous attempt left off — skipping owner-user creation entirely if a user with the
 * requested email already exists for that tenant (avoiding a duplicate owner), then always
 * (re)attempting credential storage. This is a best-effort compensating measure for the
 * lack of cross-schema atomicity above, not a full replacement for it. A signup whose
 * <em>onboarding</em> failed is a different case and needs no intervention: onboarding removes
 * the record it wrote, so the identifier is free and calling {@link #signup(SignupRequest)}
 * again simply starts over. Only a repository that cannot remove records leaves the
 * {@code DELETED} tombstone that does require manual cleanup.
 */
public class DefaultSignupService implements SignupService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSignupService.class);

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");

    private final TenantOnboardingService onboardingService;
    private final TenantRepository tenantRepository;
    private final TenantUserService tenantUserService;
    private final AccountCredentialsStore credentialsStore;
    private final PasswordEncoder passwordEncoder;

    public DefaultSignupService(TenantOnboardingService onboardingService,
                                 TenantRepository tenantRepository,
                                 TenantUserService tenantUserService,
                                 AccountCredentialsStore credentialsStore,
                                 PasswordEncoder passwordEncoder) {
        this.onboardingService = Objects.requireNonNull(onboardingService);
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
        this.tenantUserService = Objects.requireNonNull(tenantUserService);
        this.credentialsStore = Objects.requireNonNull(credentialsStore);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    @Override
    public TenantDetails signup(SignupRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String tenantId = request.tenantId();
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new SignupException(
                    "Workspace ID must be 3-64 lowercase letters, digits or hyphens (e.g. 'acme-corp')");
        }
        String email = request.ownerEmail().trim().toLowerCase(Locale.ROOT);
        if (credentialsStore.existsByEmail(tenantId, email)) {
            throw new SignupException("An account for '" + email + "' already exists in this workspace");
        }

        TenantDetails tenant = onboardOrResumeTenant(request, tenantId, email);

        // tenantUserService.invite()/activate() write through the generic, tenant-scoped
        // Datastore (TenantAwareDataSource), which routes by TenantContext.peekTenantId() —
        // NOT by an argument passed explicitly. Signup requests commonly arrive on a route
        // that is deliberately excluded from tenant-path resolution (no tenant exists yet
        // for the filter to resolve), so TenantContext is otherwise empty here. Without
        // binding it explicitly, both calls would silently fall back to the platform/
        // bootstrap connection instead of the tenant's own freshly-migrated schema, failing
        // with "Table 'TENANT_USERS' not found" (that schema-qualified table only exists in
        // the new tenant's own schema, not the platform one).
        //
        // Deliberately scoped to ONLY this block: credentialsStore.save(...) below
        // must keep routing to the platform/bootstrap connection (AccountCredentialsStore
        // implementations commonly store credentials in a shared, cross-tenant table), so
        // TenantContext must be cleared again before that call. TenantContextTemplate.run(...)
        // handles the set/finally-clear boilerplate (and correctly restores rather than blindly
        // clears, in case this ever runs nested inside an already-bound context).
        TenantContextTemplate.run(tenant, () -> {
            // Signup is, by definition, anonymous: there is no authenticated principal, and the
            // tenant has no members yet who could authorise anything. The regular
            // invite()/activate() pair is @RequiresPermission-gated, so calling it here would
            // fail with PermissionDenied for every self-service signup whenever the permissions
            // module is on the classpath. acceptInvitation(...) is the framework's deliberately
            // ungated bootstrap entry point for exactly this situation — "the caller has already
            // established authority out of band" — and it creates the user ACTIVE in one step.
            //
            // The userId is derived deterministically from tenant + email rather than being a
            // random UUID, which is what keeps a retried signup (see class Javadoc) idempotent:
            // the second attempt hits the "already belongs" guard inside acceptInvitation and is
            // treated as "the owner already exists", instead of silently creating a duplicate
            // owner. A permission-gated findByEmail() pre-check cannot be used for that purpose
            // here, for the same anonymity reason.
            String userId = ownerUserId(tenantId, email);
            try {
                tenantUserService.acceptInvitation(tenantId, userId, email, TenantRole.OWNER, null);
            } catch (TenantUserException alreadyOwner) {
                log.debug("Owner '{}' already exists for tenant '{}'; resuming signup", email, tenantId);
            }
        });

        credentialsStore.save(tenantId, email, passwordEncoder.encode(request.rawPassword()));

        return tenant;
    }

    /**
     * Provisions a new tenant, or — if one with this exact ID already exists and is
     * {@link TenantStatus#ACTIVE} — resumes an earlier, partially-failed signup attempt for it
     * instead of failing outright. See the class Javadoc "Resuming a partially-failed signup"
     * section.
     */
    private TenantDetails onboardOrResumeTenant(SignupRequest request, String tenantId, String email) {
        try {
            TenantOnboardingResult result = onboardingService.onboard(
                    TenantOnboardingRequest.builder(tenantId)
                            .name(request.companyName())
                            .plan(request.plan())
                            .adminEmail(email)
                            .attributes(request.attributes())
                            .build());
            result.orThrow();
            return result.tenant();
        } catch (TenantAlreadyExistsException alreadyExists) {
            return tenantRepository.findById(tenantId)
                    .filter(existing -> existing.status() == TenantStatus.ACTIVE)
                    .orElseThrow(() -> new SignupException(
                            "Workspace '" + tenantId + "' already exists", alreadyExists));
        }
    }

    /**
     * Derives the owner's user id deterministically from the workspace and email, so that a
     * retried signup produces the same id and is therefore recognised as a duplicate rather
     * than creating a second owner.
     */
    private static String ownerUserId(String tenantId, String email) {
        return UUID.nameUUIDFromBytes(("owner|" + tenantId + "|" + email).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}




