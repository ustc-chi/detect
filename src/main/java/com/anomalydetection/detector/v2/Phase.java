package com.anomalydetection.detector.v2;

/**
 * Detection phase for a resource.
 * <p>
 * <b>WARMUP</b>: Accumulating baseline data, using heuristic rules + dynamic threshold.
 * <b>ACTIVE</b>: Sufficient baseline data available, using weighted Euclidean distance.
 */
public enum Phase {
    WARMUP,
    ACTIVE
}
