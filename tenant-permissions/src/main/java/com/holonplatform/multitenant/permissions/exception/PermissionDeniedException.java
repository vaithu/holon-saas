package com.holonplatform.multitenant.permissions.exception;

/**
 * Thrown when the current user does not hold a required permission.
 * Callers (e.g. a Spring MVC exception resolver, or any UI-layer navigation
 * guard) can catch this to show an appropriate access-denied response.
 */
public class PermissionDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String permission;

    public PermissionDeniedException(String permission, String message) {
        super(message);
        this.permission = permission;
    }

    public PermissionDeniedException(String permission) {
        super("Permission denied: '" + permission + "'");
        this.permission = permission;
    }

    /** The permission string that was required but not held by the current user. */
    public String getPermission() {
        return permission;
    }
}

