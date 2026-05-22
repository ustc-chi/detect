## ADDED Requirements

### Requirement: FeatureVector14 数据结构
The system SHALL define a 14-dimensional feature vector class `FeatureVector14` that encapsulates:
- `values`: a `double[14]` array of feature values
- `supplementaryData`: a `Map<Integer, Map<String, Object>>` storing per-dimension supplementary data keyed by dimension index

#### Scenario: Construct with values and supplementary data
- **WHEN** a `FeatureVector14` is constructed with a valid 14-element double array and supplementary data map
- **THEN** `getValue(index)` SHALL return the value at that index
- **AND** `getSupplementary(index)` SHALL return the supplementary data map for that dimension

#### Scenario: Reject invalid array length
- **WHEN** a `FeatureVector14` is constructed with a double array of length other than 14
- **THEN** an `IllegalArgumentException` SHALL be thrown

### Requirement: Feature names and descriptions
The system SHALL provide static definitions for all 14 feature names, descriptions, and units as arrays indexed to match the vector positions.

#### Scenario: Feature metadata is accessible by index
- **WHEN** accessing `FeatureVector14.FEATURE_NAMES[6]`
- **THEN** it SHALL return `"peak_burst_velocity"`
- **AND** `FeatureVector14.FEATURE_DESCRIPTIONS[6]` SHALL return a human-readable description
- **AND** `FeatureVector14.FEATURE_UNITS[6]` SHALL return `"ops/hour"`

### Requirement: Supplementary data conventions
The system SHALL document the expected supplementary data keys for each feature dimension that carries extra information.

#### Scenario: peak_burst_velocity supplementary data
- **WHEN** a `FeatureVector14` is constructed with supplementary data for index 6 (peak_burst_velocity)
- **THEN** the supplementary map SHALL contain keys: `"peak_window_start"`, `"peak_window_end"`, `"ops_in_window"`, `"window_seconds"`
- **AND** missing keys SHALL NOT cause errors (values default to empty string/0)

#### Scenario: burst_mod_purity supplementary data
- **WHEN** supplementary data for index 10 (burst_mod_purity) is present
- **THEN** it SHALL contain keys: `"burst_window_start"`, `"burst_window_end"`, `"burst_total_ops"`, `"burst_mod_ops"`

#### Scenario: suspicious_extension_ratio supplementary data
- **WHEN** supplementary data for index 5 (suspicious_extension_ratio) is present
- **THEN** it SHALL contain key: `"matched_extensions"` with value of type `List<String>`

### Requirement: DimensionReport class
The system SHALL define a `DimensionReport` class representing a single feature dimension's analysis result.

#### Scenario: DimensionReport contains full analysis
- **WHEN** a `DimensionReport` is created for a feature dimension
- **THEN** it SHALL contain: `index`, `name`, `value`, `zScore`, `contribution` (w_i × z_i²), `weight`, `description`, `unit`, `isAnomalyDimension`, `supplementary`
- **AND** `isAnomalyDimension` SHALL be `true` when |zScore| > 2.0

### Requirement: 14 feature dimensions definition
The system SHALL define exactly the following 14 features with their indices:

| Index | Name | Unit | Supplementary |
|-------|------|------|--------------|
| 0 | total_operations | ops/day | daysBetween |
| 1 | modification_ratio | ratio | total_operations |
| 2 | deletion_intensity | score | — |
| 3 | directory_spread | dirs/day | — |
| 4 | extension_diversity | exts/day | — |
| 5 | suspicious_extension_ratio | ratio | matched_extensions[] |
| 6 | peak_burst_velocity | ops/hour | peak_window_start/end, ops_in_window |
| 7 | avg_modified_size | log(bytes) | — |
| 8 | size_std_dev | log(bytes) | — |
| 9 | high_value_ext_ratio | ratio | — |
| 10 | burst_mod_purity | ratio | burst_window_start/end, total/mod_ops |
| 11 | file_type_concentration | ratio | — |
| 12 | size_change_kurtosis | score | — |
| 13 | inter_op_time_cv | CV | — |

#### Scenario: All 14 features are defined
- **WHEN** accessing `FeatureVector14.FEATURE_NAMES`
- **THEN** its length SHALL be `14`

#### Scenario: Each feature has a description
- **WHEN** accessing `FeatureVector14.FEATURE_DESCRIPTIONS`
- **THEN** the array length SHALL be `14`
- **AND** every element SHALL be non-null and non-empty
