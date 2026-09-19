package com.holonplatform.multitenant.permissions.view;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Registry containing all registered views and screens available across the platform.
 */
public interface ViewRegistry {

    /**
     * Registers a view resource.
     *
     * @param viewResource the view to register
     */
    void register(ViewResource viewResource);

    /**
     * Finds a view resource by its identifier.
     *
     * @param viewId the view identifier
     * @return optional containing the view resource if found
     */
    Optional<ViewResource> getView(String viewId);

    /**
     * Returns all registered views in the system.
     */
    List<ViewResource> getAllViews();

    /**
     * Returns all unique categories/modules of registered views.
     */
    List<String> getCategories();

    /**
     * Returns all registered views under the specified category.
     *
     * @param category the category name
     */
    List<ViewResource> getViewsByCategory(String category);

    /**
     * Registers multiple views at once.
     */
    default void registerAll(Collection<ViewResource> views) {
        if (views != null) {
            views.forEach(this::register);
        }
    }
}
