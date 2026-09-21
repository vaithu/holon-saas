package com.holonplatform.multitenant.demo.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * A trivial in-memory, per-tenant record of what the demo services did.
 *
 * <p>Kept in memory on purpose: the point of the demo is to show <em>who is allowed to call
 * what</em>, so persisting these entries would add a table without adding any insight. Entries
 * are still keyed by tenant so the UI can show that two workspaces never see each other's rows.
 */
@Component
public class DemoLedger {

    private final Map<String, List<String>> entriesByTenant = new ConcurrentHashMap<>();

    public void append(String tenantId, String entry) {
        entriesByTenant.computeIfAbsent(tenantId, key -> new CopyOnWriteArrayList<>()).add(entry);
    }

    public List<String> entries(String tenantId) {
        return List.copyOf(entriesByTenant.getOrDefault(tenantId, List.of()));
    }
}
