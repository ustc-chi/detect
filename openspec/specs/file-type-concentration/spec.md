## REMOVED Requirements

### Requirement: File type concentration feature extraction
**Reason**: Redundant with `extension_diversity` — both measure extension distribution using different statistics (HHI vs Shannon entropy). Both produce zero signal for 10/12 ORIG attacks that don't change extensions. Replaced by `per_type_entropy` which measures operation type distribution (signal for all attacks).
**Migration**: Use `per_type_entropy` (new idx 11) instead. The operation type distribution provides the same concentration-detection capability but with universal signal.

### Requirement: File type concentration feature weight
**Reason**: Feature removed from the vector entirely.
**Migration**: Weight slot repurposed for `per_type_entropy` (weight 2.0).
