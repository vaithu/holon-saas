package com.holonplatform.multitenant.demo.account;

/**
 * One workspace an address can sign in to, described well enough for a person to tell it apart
 * from the others when the login view has to ask which one they meant.
 *
 * @param workspaceId   the tenant id, which is what the authentication actually uses
 * @param workspaceName the workspace's display name, falling back to the id when it has none
 * @param userName      the member's display name inside that workspace
 * @param role          the member's role inside that workspace
 * @param active        {@code false} when the workspace is suspended, so the view can say so
 *                      instead of reporting bad credentials
 */
public record WorkspaceMembership(String workspaceId,
                                  String workspaceName,
                                  String userName,
                                  String role,
                                  boolean active) {
}
