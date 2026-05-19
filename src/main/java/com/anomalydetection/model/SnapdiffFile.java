package com.anomalydetection.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Container for a Snapdiff report: list of records and a summary map.
 */
public final class SnapdiffFile {
    private final List<SnapdiffRecord> diffs;
    private final Map<String, Long> summary;

    @JsonCreator
    public SnapdiffFile(
            @JsonProperty("diffs") List<SnapdiffRecord> diffs,
            @JsonProperty("summary") Map<String, Long> summary) {
        this.diffs = diffs == null ? List.of() : List.copyOf(diffs);
        this.summary = summary == null ? Map.of() : Map.copyOf(summary);
    }

    public List<SnapdiffRecord> getDiffs() {
        return diffs;
    }

    public Map<String, Long> getSummary() {
        return summary;
    }
}
