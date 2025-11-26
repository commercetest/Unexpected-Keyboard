# Codex Recommended Improvements

## Assessment
- Accessibility foundation is solid (virtual keys via AccessibilityNodeProvider, hover-to-focus, activation announcements) but there are gaps: custom action labels use a direction helper that expects different indices, leading to wrong TalkBack labels; announcements are hard-coded in English; modifier/layout state is not exposed as state descriptions or live regions; traversal order hints and pane titles are missing; accessibility activations do not emit click events or haptic feedback; layout changes and deletions lack contextual announcements.
- Testability is limited: JVM tests for accessibility helpers are ignored due to framework coupling; instrumented tests are mostly placeholders; the rich TouchInstrumentation is not exercised; guidance in TESTING_EXAMPLES.md does not cover accessibility.

## Accessibility Recommendations
- Fix custom action direction labels so TalkBack Actions match corner swipe directions (1–8 mapped to NW/NE/E/SE/S/SW/W/N) in `KeyboardAccessibilityDelegate`.
- Add traversal hints (`setTraversalBefore/After`) to enforce predictable focus order across rows/columns.
- Mark the keyboard host view with `setAccessibilityPaneTitle` and set `setRoleDescription`/`setStateDescription` (where API allows) on modifier keys; expose modifier/layout changes as live regions.
- Emit `TYPE_VIEW_CLICKED` events and haptic feedback for accessibility activations to mirror touch feedback.
- Announce layout changes and modifier state transitions consistently via `AccessibilityHelper`; consider character echo on deletion when feasible.
- Localize accessibility announcements by moving strings to resources and avoid double-speaking by using a single announcement path per interaction.

## Testability Recommendations
- Decouple `AccessibilityHelper` from direct `AccessibilityManager`/`View` calls (inject interfaces) to enable fast JVM tests (Robolectric) and un-ignore existing unit tests.
- Add focused JVM tests for direction naming, swipe-option announcements, modifier state descriptions, and content descriptions.
- Expand instrumented Espresso tests: verify virtual key focus, TalkBack Actions list all swipe options with correct labels, execute custom actions and assert resulting input/accessibility events, and validate modifier/layout state descriptions.
- Leverage `TouchInstrumentation` to record and replay TalkBack-driven sessions in tests to cover hover focus + activation flows.
- Update `TESTING_EXAMPLES.md` (or add a new section) documenting how to run accessibility tests (`./gradlew test connectedAndroidTest`) and how to use instrumentation logs for regression detection.
