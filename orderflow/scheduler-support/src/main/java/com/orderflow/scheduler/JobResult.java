package com.orderflow.scheduler;

public record JobResult(long processedCount, long failureCount) {
    public static JobResult success(long processed) { return new JobResult(processed, 0); }
}
