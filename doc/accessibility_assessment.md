# Accessibility Assessment for Unexpected Keyboard

This document provides a detailed assessment of the accessibility implementation for the Unexpected Keyboard, with a focus on user experience, testability, and the use of Android APIs for services like TalkBack.

## 1. User Experience

The keyboard has a strong foundation for accessibility by using an `AccessibilityNodeProvider`. This is the correct modern approach, as it exposes individual keys as separate entities to the accessibility framework. However, the user interaction can be refined.

### Current State
- **Exploration:** Users can explore the keyboard by dragging their finger over it. As their finger moves over a key, TalkBack announces the key's primary character and its available swipe actions.
- **Selection:** To type a character, the user lifts their finger from a key and then double-taps anywhere on the screen. This selects the *last focused key*.

While functional, this selection model can be unintuitive for users accustomed to the standard TalkBack pattern of double-tapping the focused element itself.

### Recommendations

- **Recommendation 1.1: Context-Aware Double-Tap**
  Refine the double-tap action to be context-aware. A double-tap should only activate a key if the tap occurs *within the bounds of that key*. This aligns with standard TalkBack behavior and makes the interaction more predictable. The current implementation of `performAccessibilityKeyPress` can be modified to check the coordinates of the click action.

- **Recommendation 1.2: Enhanced Swipe Gesture Announcements**
  Improve the announcements for swipe gestures to provide more context. Instead of just announcing the resulting character, include the *type* of action. For example:
  - *Current:* "swipe up for A"
  - *Recommended:* "swipe up to type capital A" or "swipe left for delete"
  This helps the user understand the *result* of the action, not just the gesture itself. This can be implemented in `AccessibilityHelper.buildSwipeOptionsAnnouncement`.

- **Recommendation 1.3: Announce All Characters on a Key**
  For keys with multiple actions (e.g., long-press for a pop-up menu of accented characters), the initial announcement should inform the user that more options are available. For example: "e, long-press for more options". This prevents users from having to guess which keys have hidden characters.

## 2. Testability

The project currently lacks a dedicated suite of accessibility tests. This makes it difficult to verify the accessibility implementation and prevent regressions.

### Recommendations

- **Recommendation 2.1: Unit Tests for `AccessibilityHelper`**
  Create unit tests for the `AccessibilityHelper` class. These tests should verify that the `getKeyDescription` method and other announcement-building methods produce the correct, human-readable strings for a wide variety of `KeyValue` types, including edge cases.

- **Recommendation 2.2: UI Tests with AccessibilityChecks**
  Implement UI tests using Espresso and enable `AccessibilityChecks`. This will automatically scan for common accessibility issues like insufficient touch target size, missing content descriptions, and inadequate color contrast.

- **Recommendation 2.3: Integration Tests for `KeyboardAccessibilityDelegate`**
  Develop integration tests that specifically target the `KeyboardAccessibilityDelegate`. These tests should:
  - Verify that a virtual view is created for each key.
  - Ensure that focus is correctly managed as the user moves their finger across the keyboard.
  - Confirm that accessibility actions like `ACTION_CLICK` and `ACTION_ACCESSIBILITY_FOCUS` are handled correctly.

## 3. API Usage and Best Practices

The project makes good use of modern accessibility APIs. The following recommendations are for further refinement and to align with best practices.

### Recommendations

- **Recommendation 3.1: More Detailed Javadoc**
  Enhance the Javadoc comments in `Keyboard2View`, `KeyboardAccessibilityDelegate`, and `AccessibilityHelper`. The comments should not only describe *what* the code does, but also *why* certain implementation choices were made, especially around focus management. This is crucial for long-term maintainability.

- **Recommendation 3.2: Use `AccessibilityNodeInfo.CollectionInfo`**
  To provide even more context to the user, consider using `AccessibilityNodeInfo.CollectionInfo`. This would describe the keyboard as a grid (a collection of rows and columns) to the accessibility service. TalkBack can then announce the position of a key within the grid, for example: "e, row 2, column 4". This can significantly improve spatial awareness for users.

- **Recommendation 3.3: Implement Custom Accessibility Actions**
  For keys with multiple functions (e.g., a primary character plus alternatives on swipe or long-press), consider implementing custom accessibility actions. This would allow a TalkBack user to bring up a context menu for a specific key and choose from a list of actions (e.g., "Type e", "Type é", "Type è"). This provides a clear, discoverable alternative to gestures, which can be difficult for some users.

## Summary

The keyboard has a strong accessibility foundation. By focusing on a more intuitive user experience, implementing a dedicated testing strategy, and leveraging more advanced accessibility APIs, it has the potential to become a best-in-class example of an accessible custom keyboard for Android.
