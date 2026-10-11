# R-13 Adaptive layouts, foldables, Windows desktop UX

Path: `docs/gauntlet/research/R-13-adaptive-layouts.md`. Task card: `coordination/tasks/cc-research-13.md`. Author: researcher (Sonnet). Access date for every source below is 2026-10-11 unless stated. Anything not confirmed by a source is marked UNVERIFIED.

STATUS: draft in progress (sections are added one at a time; see the end of the file for the completion marker).

## Question

How does each candidate stack (S1 Kotlin Multiplatform + Compose Multiplatform, S2 Flutter, S3 .NET MAUI, S4 Avalonia, S5 React Native + React Native for Windows, S6 Tauri 2) support window size classes, list-detail and supporting-pane layouts, foldable postures, and Windows desktop interaction and accessibility? This feeds GAUNTLET.md 2.J, 2.G (tabletop posture) and the R-01 adaptive and accessibility criteria.

## Findings

### F1. Window size classes (Android developer docs)

- Width classes: compact < 600dp; medium 600dp to < 840dp; expanded 840dp to < 1200dp; large 1200dp to < 1600dp; extra-large >= 1600dp. Source: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes (accessed 2026-10-11; page says "Last updated 2026-09-22 UTC").
- Height classes: compact < 480dp; medium 480dp to < 900dp; expanded >= 900dp. Source: same URL.
- Large and extra-large were added beyond the three Material classes "to better target desktop and connected displays". They are opt-in: `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass`. Source: same URL.
- Docs guidance: width is usually the primary factor; consider height for phones or open flippables in landscape (width medium, height compact, so two-pane is impractical); do not use size classes as `isTablet` logic; class can change at runtime with rotation, multitasking or folding; design for expanded first, then decide a specialized medium layout. Source: same URL.
- API: `androidx.window.core.layout.WindowSizeClass`, `currentWindowAdaptiveInfo()` returning `WindowAdaptiveInfo`, `WindowAdaptiveInfo.windowSizeClass`, `isHeightAtLeastBreakpoint(...)`, `WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND`. Source: same URL.
- Consequence for GAUNTLET.md 2.J: the three-class mapping in 2.J (compact, medium, expanded) matches the Material definitions but omits large and extra-large. A 1920x1080 desktop window at 100% scaling is 1920dp wide, so it is extra-large, not merely expanded. See Recommendation.

