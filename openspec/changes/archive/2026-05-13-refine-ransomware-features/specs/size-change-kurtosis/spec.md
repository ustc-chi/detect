## REMOVED Requirements

### Requirement: Size change kurtosis feature extraction
**Reason**: Fundamentally broken — computes excess kurtosis of `log1p(NEW_SIZE)` for modified files. This measures the shape of the post-encryption file-size distribution, not the shape of size changes. Without `old_size` in `SnapdiffRecord`, the feature cannot distinguish encryption from normal file composition. The ×1.03 log-space multiplier from encryption adds a constant that doesn't meaningfully change kurtosis.
**Migration**: No metadata-only replacement exists. If `old_size` becomes available, a `size_delta_cv` feature would be the proper replacement.

### Requirement: Size change kurtosis feature weight
**Reason**: Feature removed from the vector entirely.
**Migration**: Weight slot repurposed.
