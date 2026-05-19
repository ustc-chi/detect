package com.anomalydetection.generator;

import java.util.List;

/**
 * Container for the generated test data output, matching what SnapdiffParser expects.
 */
public class SnapdiffOutput {
    public List<DiffEntry> diffs;
    public SummaryOutput summary;

    public SnapdiffOutput(List<DiffEntry> diffs, SummaryOutput summary) {
        this.diffs = diffs;
        this.summary = summary;
    }
}

class SummaryOutput {
    public int files_added;
    public int files_modified;
    public int files_deleted;

    public SummaryOutput(int added, int modified, int deleted) {
        this.files_added = added;
        this.files_modified = modified;
        this.files_deleted = deleted;
    }
}
