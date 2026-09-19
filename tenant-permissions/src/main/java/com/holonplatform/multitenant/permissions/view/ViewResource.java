package com.holonplatform.multitenant.permissions.view;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata descriptor representing a view or UI screen in the system.
 *
 * @param viewId           unique string identifier for the view (e.g. {@code "customer-details"})
 * @param displayName      human-readable label (e.g. {@code "Customer Details"})
 * @param category         module or functional grouping (e.g. {@code "CRM"}, {@code "Billing"})
 * @param description      optional text description of the view
 * @param supportedActions set of actions supported by this view (e.g. {@code READ}, {@code CREATE}, {@code UPDATE})
 */
public record ViewResource(
        String viewId,
        String displayName,
        String category,
        String description,
        Set<String> supportedActions
) {

    public ViewResource {
        Objects.requireNonNull(viewId, "viewId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(category, "category must not be null");
        if (supportedActions == null || supportedActions.isEmpty()) {
            supportedActions = Set.of(ViewAction.READ);
        } else {
            supportedActions = Collections.unmodifiableSet(supportedActions);
        }
    }

    /**
     * Builder for constructing a {@link ViewResource}.
     */
    public static Builder builder(String viewId) {
        return new Builder(viewId);
    }

    public static final class Builder {
        private final String viewId;
        private String displayName;
        private String category = "General";
        private String description = "";
        private Set<String> supportedActions = Set.of(ViewAction.READ);

        public Builder(String viewId) {
            this.viewId = viewId;
            this.displayName = viewId;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder supportedActions(Set<String> supportedActions) {
            this.supportedActions = supportedActions;
            return this;
        }

        public Builder supportedActions(String... actions) {
            this.supportedActions = Set.of(actions);
            return this;
        }

        public ViewResource build() {
            return new ViewResource(viewId, displayName, category, description, supportedActions);
        }
    }
}
