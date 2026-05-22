package com.anomalydetection.detector.v2;

/**
 * Warmup phase detection status classification.
 */
public enum WarmupStatus {
    /** Triggered by deterministic or strong heuristic rule. Not added to baseline. */
    ANOMALY,
    /** Triggered by statistical anomaly (dynamic threshold). Not added to baseline. */
    SUSPICIOUS,
    /** Normal sample. Added to baseline. */
    NORMAL
}
