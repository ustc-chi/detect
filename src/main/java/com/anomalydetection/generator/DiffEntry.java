package com.anomalydetection.generator;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Output record for a single diff entry. Field names are kept snake_case to match
 * the SnapdiffParser JSON format.
 */
public class DiffEntry {
    public String path;
    public String type;        // "added", "modified", "deleted"
    public String size;        // keep as string to mirror sample JSON
    @JsonProperty("change_time")
    public String change_time; // ISO-8601 UTC string

    public DiffEntry(String path, String type, String size, String change_time) {
        this.path = path;
        this.type = type;
        this.size = size;
        this.change_time = change_time;
    }
}
