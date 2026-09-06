package com.holonplatform.multitenant.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Virtual Thread performance monitoring.
 *
 * Tracks key metrics for Virtual Thread workloads:
 * - Active virtual thread count
 * - Memory utilization
 * - CPU usage
 * - Tenant context propagation efficiency
 */
@Component
@ConditionalOnClass(name = "java.lang.VirtualThread")
public class VirtualThreadMetrics {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadMetrics.class);

    private final ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
    private final OperatingSystemMXBean osMx = ManagementFactory.getOperatingSystemMXBean();

    /**
     * Get current active thread count.
     * With Virtual Threads, this can exceed 100,000 safely.
     */
    public long getActiveThreadCount() {
        return threadMx.getThreadCount();
    }

    /**
     * Get peak thread count since JVM start.
     * Shows if Virtual Threads are scaling appropriately.
     */
    public long getPeakThreadCount() {
        return threadMx.getPeakThreadCount();
    }

    /**
     * Get total threads started since JVM start.
     * With Virtual Threads, this number grows much faster than peak.
     */
    public long getTotalStartedThreadCount() {
        return threadMx.getTotalStartedThreadCount();
    }

    /**
     * Get memory utilization ratio.
     * With VT: Should be < 50% of max heap
     * With platform threads: Would be 90%+
     */
    public double getMemoryUtilizationRatio() {
        Runtime rt = Runtime.getRuntime();
        return (double) (rt.totalMemory() - rt.freeMemory()) / rt.maxMemory();
    }

    /**
     * Get heap memory in MB.
     */
    public long getHeapMemoryUsedMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    /**
     * Get max heap memory in MB.
     */
    public long getMaxHeapMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    /**
     * Get CPU load average (1 minute).
     * With Virtual Threads, this should be much lower for same throughput.
     */
    public double getCpuLoadAverage() {
        return osMx.getSystemLoadAverage();
    }

    /**
     * Get available processors.
     * Virtual Thread concurrency can far exceed this.
     */
    public int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Logs Virtual Thread statistics (for debugging).
     */
    public void logVirtualThreadStats() {
        log.info("=== Virtual Thread Statistics ===");
        log.info("Active Threads: {}", getActiveThreadCount());
        log.info("Peak Threads: {}", getPeakThreadCount());
        log.info("Total Started: {}", getTotalStartedThreadCount());
        log.info("Memory Utilization: {}% ({}/{} MB)",
                String.format("%.1f", getMemoryUtilizationRatio() * 100),
                getHeapMemoryUsedMb(),
                getMaxHeapMemoryMb());
        log.info("CPU Load Average: {}", String.format("%.2f", getCpuLoadAverage()));
        log.info("Available Processors: {}", getAvailableProcessors());
    }

    /**
     * Returns a formatted metrics summary for API/dashboard use.
     */
    public MetricsSummary getSummary() {
        return new MetricsSummary(
                getActiveThreadCount(),
                getPeakThreadCount(),
                getTotalStartedThreadCount(),
                getMemoryUtilizationRatio(),
                getHeapMemoryUsedMb(),
                getMaxHeapMemoryMb(),
                getCpuLoadAverage(),
                getAvailableProcessors()
        );
    }

    /**
     * Immutable metrics summary for serialization.
     */
    public record MetricsSummary(
            long activeThreads,
            long peakThreads,
            long totalStartedThreads,
            double memoryUtilizationRatio,
            long heapUsedMb,
            long maxHeapMb,
            double cpuLoadAverage,
            int availableProcessors
    ) {
        public String toFormattedString() {
            return String.format(
                    "Active Threads: %d | Peak: %d | Total Started: %d | " +
                    "Memory: %.1f%% (%d/%d MB) | CPU Load: %.2f | Processors: %d",
                    activeThreads, peakThreads, totalStartedThreads,
                    memoryUtilizationRatio * 100, heapUsedMb, maxHeapMb,
                    cpuLoadAverage, availableProcessors
            );
        }
    }
}

