# Canonical set comparison verification

Real full-corpus mapping found two duplicate-oracle groups with keyword order differences, not different keyword membership. Canonical comparison now sorts keyword/color/identity lists while preserving multiplicity and keeping face order significant. Source records retain their original order; catalog digest uses the same comparison normalization and never replaces exact comparison.

Verification: 13 actual core/common JUnit tests pass, including meaningful order/duplicate/removal/face-order cases. A private genuine-source probe compares 13 duplicate pairs across the two conflict groups through actual ScryfallCodec and sameCanonicalGameData; all agree. Raw source remains outside git. This does not fix the 83 reversible printings missing parent oracle IDs, prove full app import, or constitute Android/physical-device evidence.
