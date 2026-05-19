## Context

Filesystem snapshots are compared over time to produce "snapdiff" files that record what changed (files added, removed, modified). These diffs form a time series where anomalies may indicate security incidents, misconfigurations, or operational issues. Random Cut Forest (RCF) is an unsupervised ensemble method designed for streaming anomaly detection. Amazon's open-source RCF implementation (`random-cut-forest-by-aws`, Apache 2.0) provides production-grade Java bindings.

Current state: No anomaly detection exists. Snapdiff files are produced but not analyzed for patterns.

## Goals / Non-Goals

**Goals:**
- Parse snapdiff files and extract meaningful numerical features
- Build an RCF baseline model from historical snapdiffs (Phase 1)
- Detect anomalies in newly produced snapdiffs against the established baseline (Phase 2)
- Allow the RCF forest to adapt via insert/forget semantics during Phase 2
- Generate human-readable explanations for detected anomalies
- Expose all tunable parameters via CLI arguments
- Achieve high test coverage with synthetic data simulating 50+ snapshots
- Validate hyperparameters against the test suite

**Non-Goals:**
- Distributed processing (single-node only)
- Real-time filesystem monitoring (batch processing of snapshot diffs)
- GUI or web interface (CLI only)
- Persistence of model state across restarts (rebuild from file each run)
- Alerting/notification integration (output file only)
- Dynamic threshold adjustment in Phase 2 (threshold is fixed after Phase 1)

## Decisions

### Decision 1: Use Amazon's `random-cut-forest-by-aws` Java library
- **Rationale**: Production-grade, Apache 2.0 licensed, actively maintained, supports streaming with insert/forget, provides attribution vectors for explainability
- **Alternatives considered**: `rrcf` (pure Python, slower, less mature), custom implementation (too much effort), Isolation Forest (not streaming-native)

### Decision 2: Maven build system with Java 11+
- **Rationale**: Standard Java ecosystem, easy dependency management, integrates well with RCF's Maven artifacts
- **Alternatives considered**: Gradle (either works, Maven is more common in enterprise), Python (user explicitly requested Maven)

### Decision 3: Shingle-based temporal features
- **Rationale**: Filesystem changes have temporal context. A spike after quiet periods is different from sustained high activity. Shingle size of 4 means each feature vector includes the current diff plus 3 previous diffs
- **Alternatives considered**: Point-in-time only (loses context), sliding window averages (smoothes away spikes)

### Decision 4: Two-phase execution (baseline → detection)
- **Rationale**: Separating baseline building from detection allows immediate anomaly scoring on new data. Phase 1 feeds historical snapdiffs to build the forest without alerting. Phase 2 processes new snapdiffs, scoring each against the mature baseline. This also supports re-running phase 2 with the same baseline against new incoming data.
- **Phase 1**: Baseline building — ingest all historical snapdiffs, populate RCF, compute per-feature mean/std statistics
- **Phase 2**: Detection — score new snapdiffs, use insert/forget to adapt forest, alert when score exceeds threshold calibrated from phase 1 scores

### Decision 5: Feature vector structure (17 base features)
- **Rationale**: Covers count, size, metadata, and rate dimensions. Normalized ratios prevent bias toward large filesystems
- **Features**: files_added, files_removed, files_modified, dirs_added, dirs_removed, symlinks_changed, bytes_added, bytes_removed, bytes_modified_delta, bytes_growth_rate, permissions_changed, ownership_changed, timestamps_changed, xattrs_changed, modification_ratio, churn_rate, metadata_change_ratio

### Decision 5: Forest adaptation via insert/forget in Phase 2
- **Rationale**: Filesystem behavior changes over time (concept drift). The forest must adapt by forgetting old points and inserting new ones. This maintains relevance without requiring full retraining. Each tree uses a bounded sample (tree_size), and the oldest point is evicted when a new one arrives.
- **Alternatives considered**: Static forest (becomes stale), periodic full retraining (expensive), sliding window rebuild (complex)

### Decision 6: Post-hoc explanation via z-score deviation
- **Rationale**: RCF attribution vectors show feature importance but aren't human-readable. Comparing anomalous point to historical mean/std produces intuitive explanations
- **Alternatives considered**: SHAP (overkill for 17 features), raw attribution (hard to interpret)

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Insufficient baseline data produces unreliable threshold | Enforce minimum baseline snapshots (default: 100) before allowing Phase 2 |
| Forest becomes stale if baseline never adapts | Phase 2 uses insert/forget so forest evolves; document that threshold is fixed from Phase 1 |
| False positives during concept drift (e.g., system upgrade) | Expose `--max-baseline-age` to recommend periodic Phase 1 rebuilds |
| Feature scale differences bias RCF | Normalize ratios and log-transform sizes; RCF handles it but normalization improves stability |
| Cold start (no historical baseline) | Document that Phase 1 requires sufficient historical data; provide guidance on minimum snapshots |
| Single-threaded processing bottleneck | RCF tree updates are embarrassingly parallel; future enhancement can parallelize |
| Snapdiff format changes break parser | Define clear format contract in spec; validate headers before parsing |

## Migration Plan

N/A — this is a new tool, not a migration.

## Open Questions

1. What is the exact snapdiff file format? (User mentioned an "exemplar snapshot" but none was attached)
2. Should the tool support incremental runs (append to result file) or always overwrite?
3. What is the expected snapshot frequency? (Affects shingle size recommendation)
