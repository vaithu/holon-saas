package com.holonplatform.multitenant.permissions.view.internal;

import com.holonplatform.multitenant.permissions.view.ViewRegistry;
import com.holonplatform.multitenant.permissions.view.ViewResource;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe implementation of {@link ViewRegistry}.
 *
 * <p>Discovers and registers Spring beans annotated with {@link SecuredView} automatically
 * during application startup, while also supporting programmatic registration.</p>
 */
public class DefaultViewRegistry implements ViewRegistry, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(DefaultViewRegistry.class);

    private final Map<String, ViewResource> views = new ConcurrentHashMap<>();

    @Override
    public void register(ViewResource viewResource) {
        Objects.requireNonNull(viewResource, "viewResource must not be null");
        views.put(viewResource.viewId(), viewResource);
        log.debug("Registered view resource: id='{}', category='{}', actions={}",
                viewResource.viewId(), viewResource.category(), viewResource.supportedActions());
    }

    @Override
    public Optional<ViewResource> getView(String viewId) {
        if (viewId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(views.get(viewId));
    }

    @Override
    public List<ViewResource> getAllViews() {
        return views.values().stream()
                .sorted(Comparator.comparing(ViewResource::category).thenComparing(ViewResource::displayName))
                .toList();
    }

    @Override
    public List<String> getCategories() {
        return views.values().stream()
                .map(ViewResource::category)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<ViewResource> getViewsByCategory(String category) {
        if (category == null) {
            return List.of();
        }
        return views.values().stream()
                .filter(v -> category.equalsIgnoreCase(v.category()))
                .sorted(Comparator.comparing(ViewResource::displayName))
                .toList();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // Auto-discover @SecuredView annotated beans
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(SecuredView.class);
        for (Object bean : beansWithAnnotation.values()) {
            Class<?> targetClass = bean.getClass();
            SecuredView annotation = targetClass.getAnnotation(SecuredView.class);
            if (annotation != null) {
                String id = annotation.id();
                String displayName = annotation.displayName().isBlank() ? id : annotation.displayName();
                String category = annotation.category();
                String description = annotation.description();
                Set<String> actions = Set.of(annotation.actions());

                ViewResource resource = new ViewResource(id, displayName, category, description, actions);
                register(resource);
            }
        }
        log.info("DefaultViewRegistry initialized with {} view resources", views.size());
    }
}
