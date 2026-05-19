package com.anomalydetection.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Immutable representation of a single diff entry in a Snapdiff report.
 */
public final class SnapdiffRecord {
    private final String path;
    private final String type; // added, modified, deleted
    private final long size;
    private final Instant changeTime;

    @JsonCreator
    public SnapdiffRecord(
            @JsonProperty("path") String path,
            @JsonProperty("type") String type,
            @JsonProperty("size") String size,
            @JsonProperty("change_time") String changeTime) {
        this.path = path;
        this.type = type;
        long s = 0L;
        if (size != null && !size.isEmpty()) {
            try {
                s = Long.parseLong(size);
            } catch (NumberFormatException ignore) {
                s = 0L;
            }
        }
        this.size = s;
        Instant ct = Instant.EPOCH;
        if (changeTime != null && !changeTime.isEmpty()) {
            try {
                ct = Instant.parse(changeTime);
            } catch (Exception ignore) {
                ct = Instant.EPOCH;
            }
        }
        this.changeTime = ct;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public Instant getChangeTime() {
        return changeTime;
    }
}
