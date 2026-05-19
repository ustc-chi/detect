## ADDED Requirements

### Requirement: Snapdiff JSON schema parsing
The system SHALL parse snapdiff files conforming to the following schema:
```json
{
  "diffs": [
    {
      "path": "string (required)",
      "type": "string (required, one of: added, modified, deleted)",
      "size": "string (optional, numeric)",
      "change_time": "string (optional, ISO-8601 timestamp)"
    }
  ],
  "summary": {
    "files_added": "number (optional)",
    "files_modified": "number (optional)",
    "files_deleted": "number (optional)"
  }
}
```

#### Scenario: Parse complete snapdiff
- **WHEN** a snapdiff file contains all required and optional fields
- **THEN** the system extracts path, type, size, change_time for each diff entry and summary statistics

#### Scenario: Parse minimal snapdiff
- **WHEN** a snapdiff file contains only required fields (path, type)
- **THEN** the system uses size=0 and current timestamp as defaults

#### Scenario: Handle invalid type
- **WHEN** a diff entry has a type not in {added, modified, deleted}
- **THEN** the system logs a warning and skips that entry

### Requirement: Feature extraction rules
The system SHALL compute features according to these rules:

#### Scenario: Count features
- **WHEN** processing a snapdiff
- **THEN** the system counts: files_added (type="added" and path not ending in "/"), files_removed (type="deleted" and path not ending in "/"), files_modified (type="modified" and path not ending in "/"), dirs_added (type="added" and path ending in "/"), dirs_removed (type="deleted" and path ending in "/"), symlinks_changed (path containing "/symlink" or type indicating symlink)

#### Scenario: Size features
- **WHEN** processing a snapdiff with size information
- **THEN** the system computes: bytes_added (sum of sizes for added files), bytes_removed (sum for deleted), bytes_modified_delta (sum of absolute size changes for modified), bytes_growth_rate (net change / previous total bytes, or 0 if first snapshot)

#### Scenario: Metadata change detection
- **WHEN** a file is marked as "modified"
- **THEN** the system increments metadata counters based on available indicators: permissions_changed, ownership_changed, timestamps_changed, xattrs_changed (if the snapdiff format provides this detail; otherwise inferred from modification type)

#### Scenario: Ratio features
- **WHEN** count features are computed
- **THEN** the system computes: modification_ratio = files_modified / total_files, churn_rate = (files_added + files_removed) / total_files, metadata_change_ratio = metadata_changes / total_files

### Requirement: Feature normalization
The system SHALL normalize features before feeding to RCF.

#### Scenario: Log-transform size features
- **WHEN** size features exceed 1000
- **THEN** the system applies log1p transformation to prevent extreme values from dominating

#### Scenario: Clip ratio features
- **WHEN** ratio features exceed 1.0
- **THEN** the system clips to [0, 1] range

#### Scenario: Handle zero division
- **WHEN** computing ratios with zero denominator
- **THEN** the system uses 0.0 as the result
