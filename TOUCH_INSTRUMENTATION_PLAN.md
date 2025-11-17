# Touch & Gesture Instrumentation and Accessibility Implementation Plan

## Executive Summary

The Unexpected Keyboard has a well-structured touch handling architecture with three distinct layers:
1. **View layer** (Keyboard2View.java) - receives raw touch events
2. **Pointer management** (Pointers.java) - tracks multi-touch and gestures
3. **Event processing** (KeyEventHandler.java) - converts to keyboard output

Currently, there's minimal instrumentation (basic debug logging in Logs.java) and **zero accessibility implementation**. This plan outlines a phased approach to add comprehensive instrumentation and enable both automated testing and TalkBack support.

---

## Phase 1: Instrumentation Architecture

### 1.1 Create Instrumentation Framework

**Objective:** Build a centralized, extensible logging system that can be enabled/disabled at runtime.

**Recommended approach:**

Create a new `TouchInstrumentation.java` class with:
```java
public class TouchInstrumentation {
    public enum EventType {
        TOUCH_DOWN, TOUCH_MOVE, TOUCH_UP, TOUCH_CANCEL,
        SWIPE_DETECTED, GESTURE_DETECTED, LONG_PRESS,
        KEY_SELECTED, KEY_OUTPUT, POINTER_STATE_CHANGE
    }

    public static void logEvent(EventType type, Bundle data);
    public static void setEnabled(boolean enabled);
    public static void setOutputMode(OutputMode mode); // LOGCAT, FILE, BROADCAST
}
```

**Benefits:**
- Centralized control
- Can switch between logcat, file, or broadcast receivers
- Structured data format (JSON) for parsing
- Easy to disable in production

### 1.2 Key Instrumentation Points

Based on the codebase analysis, instrument these critical locations:

| Location | File:Line | Event Type | Data to Capture |
|----------|-----------|------------|-----------------|
| Touch down | Keyboard2View.java:197 | TOUCH_DOWN | x, y, pointerId, keyName, timestamp |
| Touch move | Keyboard2View.java:197 | TOUCH_MOVE | x, y, pointerId, distance, timestamp |
| Touch up | Keyboard2View.java:197 | TOUCH_UP | pointerId, duration, timestamp |
| Swipe start | Pointers.java:281 | SWIPE_DETECTED | direction, startKey, distance |
| Gesture recognized | Pointers.java:334 | GESTURE_DETECTED | gestureType, keys, duration |
| Long press | Pointers.java:438 | LONG_PRESS | keyName, duration, willRepeat |
| Key output | KeyEventHandler.java:87 | KEY_OUTPUT | inputKey, outputValue, modifiers |
| Pointer state | Pointers.java:138-332 | POINTER_STATE_CHANGE | flags, latched, locked |

### 1.3 Data Structure

**Recommended JSON format:**
```json
{
  "timestamp": 1700000000000,
  "eventType": "TOUCH_DOWN",
  "pointerId": 0,
  "position": {"x": 150.5, "y": 300.2},
  "key": {
    "name": "a",
    "row": 2,
    "position": 3
  },
  "metadata": {
    "sessionId": "abc123",
    "layoutName": "latn_qwerty"
  }
}
```

---

## Phase 2: Implementation Strategy

### 2.1 Minimal Invasive Approach

**Strategy:** Add instrumentation calls at the **Pointers.java** layer (not the raw View layer)

**Why?**
- Pointers.java already interprets raw touches into meaningful gestures
- Reduces noise (filters out moves within center region)
- Captures semantic information (swipe direction, gesture type)
- Single responsibility: Pointers.java handles touch logic, instrumentation records it

**Example implementation in Pointers.java:202-216:**

```java
public void onTouchDown(float x, float y, int pointerId, KeyboardData.Key key)
{
  Pointer ptr = getPtr(pointerId);
  if (ptr != null)
    return;
  Pointer new_ptr = new Pointer(pointerId, key, x, y, _handler);
  _ptrs.add(new_ptr);

  // ADD INSTRUMENTATION HERE
  if (TouchInstrumentation.isEnabled()) {
    Bundle data = new Bundle();
    data.putFloat("x", x);
    data.putFloat("y", y);
    data.putInt("pointerId", pointerId);
    data.putString("keyName", key.keys[0].toString());
    TouchInstrumentation.logEvent(EventType.TOUCH_DOWN, data);
  }

  _handler.onPointerDown(false);
}
```

### 2.2 Configuration Integration

**Add to Config.java:**
- `instrumentation_enabled` boolean preference
- `instrumentation_mode` string (logcat, file, broadcast)
- `instrumentation_verbosity` int (minimal, standard, verbose)

**Add to Settings UI:**
- New "Developer Options" section
- Toggle for instrumentation
- Mode selector
- "Export logs" button

### 2.3 Build Variants

**Recommended:**
- Create debug build variant with instrumentation always available
- Release build with instrumentation compiled out (or stub implementation)
- Use ProGuard to remove in production

---

## Phase 3: Automated Testing Support

### 3.1 Test Infrastructure

**Create test utilities that leverage instrumentation:**

1. **TouchEventRecorder.java** - Records real user sessions
2. **TouchEventPlayer.java** - Replays recorded sessions
3. **TouchEventMatcher.java** - Validates expected vs actual events

### 3.2 Integration with Android Testing Framework

**Approach: Use BroadcastReceiver for test synchronization**

```java
// In test code
TouchInstrumentation.setOutputMode(OutputMode.BROADCAST);

// Register receiver
IntentFilter filter = new IntentFilter("juloo.keyboard2.TOUCH_EVENT");
registerReceiver(new TouchEventReceiver(), filter);

// Inject touch events via UiAutomator
device.click(x, y);

// Wait for corresponding instrumentation event
TouchEvent event = receiver.waitForEvent(TIMEOUT);
assertEquals("a", event.getKeyName());
```

### 3.3 Test Scenarios to Enable

With instrumentation, these tests become feasible:

1. **Swipe accuracy tests** - Verify correct key selected for each direction
2. **Gesture recognition tests** - Validate circle/roundtrip detection
3. **Multi-touch tests** - Ensure correct handling of simultaneous touches
4. **Modifier tests** - Verify latching/locking behavior
5. **Layout tests** - Test all keys in all layouts are reachable
6. **Performance tests** - Measure touch-to-output latency

**Test file structure:**
```
androidTest/
├── TouchEventTests.java
├── GestureRecognitionTests.java
├── ModifierBehaviorTests.java
├── LayoutCoverageTests.java
└── fixtures/
    ├── recorded_sessions/
    │   ├── basic_typing.json
    │   ├── swipe_typing.json
    │   └── gesture_typing.json
    └── expected_outputs/
```

---

## Phase 4: Accessibility Implementation

### 4.1 Critical Gap Analysis

**Current state:** Zero accessibility implementation
- No `contentDescription` on keys
- No `AccessibilityEvent` announcements
- No TalkBack support
- No touch exploration mode

### 4.2 Accessibility Architecture

**Recommended approach: Parallel implementation**

Create `AccessibilityManager.java` that:
1. Listens to the same events as TouchInstrumentation
2. Generates appropriate AccessibilityEvents
3. Provides contentDescription for touch exploration
4. Announces gestures and key presses

**Integration points:**

| Integration Point | File:Line | Accessibility Action |
|-------------------|-----------|---------------------|
| Key selection | Keyboard2View.java:159 | Announce key name + swipe directions |
| Key press | KeyEventHandler.java:87 | Announce output character |
| Swipe | Pointers.java:281 | Announce "Swiped [direction] on [key]" |
| Gesture | Pointers.java:334 | Announce gesture type + result |
| Mode change | Pointers.java | Announce "Shift locked", "Sliding mode", etc. |

### 4.3 Implementation Details

**In Keyboard2View.java, add:**

```java
@Override
public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(Keyboard2View.class.getName());
    info.setContentDescription("Virtual keyboard");

    // Add custom actions for each key
    for (Row row : _keyboard.rows) {
        for (KeyboardData.Key key : row.keys) {
            AccessibilityNodeInfo.AccessibilityAction action =
                new AccessibilityNodeInfo.AccessibilityAction(
                    generateId(key),
                    key.keys[0].toString()
                );
            info.addAction(action);
        }
    }
}

@Override
public boolean performAccessibilityAction(int action, Bundle arguments) {
    KeyboardData.Key key = getKeyForActionId(action);
    if (key != null) {
        // Simulate key press
        announceForAccessibility("Pressed " + key.keys[0].toString());
        _handler.key_up(key.keys[0], /* ... */);
        return true;
    }
    return super.performAccessibilityAction(action, arguments);
}
```

**In Pointers.java, add accessibility announcements:**

```java
// In onPointerUp()
if (AccessibilityManager.isEnabled()) {
    String announcement = generateAnnouncement(key, direction, modifiers);
    _handler.announceForAccessibility(announcement);
}

private String generateAnnouncement(Key key, int direction, int modifiers) {
    StringBuilder sb = new StringBuilder();

    // Announce modifiers
    if ((modifiers & KeyValue.FLAG_SHIFT) != 0) sb.append("Shift ");
    if ((modifiers & KeyValue.FLAG_CTRL) != 0) sb.append("Control ");

    // Announce key
    sb.append(key.keys[direction].toString());

    // Announce direction if not center
    if (direction != 0) {
        sb.append(" (").append(directionName(direction)).append(")");
    }

    return sb.toString();
}
```

### 4.4 TalkBack Testing Checklist

Once implemented, verify:
- [ ] Each key announces its label when touched
- [ ] Swipe directions are announced
- [ ] Output is announced when key is pressed
- [ ] Modifier states are announced (shift, ctrl, etc.)
- [ ] Gestures are announced with their results
- [ ] Touch exploration works without activating keys
- [ ] Users can navigate between keys using swipe gestures
- [ ] Alternative input methods work (e.g., accessibility scanner)

---

## Phase 5: Advanced Features

### 5.1 Visual Touch Feedback (for testing)

Create debug overlay that shows:
- Touch points with pointer IDs
- Swipe trajectories
- Gesture paths (for circles/roundtrips)
- Detected key boundaries
- Current pointer states

**Implementation:** Custom View overlay in debug builds

### 5.2 Remote Monitoring

**Use case:** Record user sessions remotely for bug reports

**Approach:**
1. TouchInstrumentation writes to file
2. Add "Export instrumentation log" in settings
3. Users can share log with bug reports
4. Developer tools to visualize replay

### 5.3 Machine Learning Integration

**Future possibility:**
- Collect touch patterns to improve gesture recognition
- Personalize swipe distance thresholds
- Predict user intent from partial gestures

---

## Implementation Priorities

### Must-Have (Phase 1-2)
1. TouchInstrumentation framework
2. Logcat output for basic events
3. Instrument Pointers.java touch handlers
4. Configuration toggle in settings

### Should-Have (Phase 3)
5. BroadcastReceiver for test integration
6. Basic automated test suite
7. Recording/playback utilities

### Nice-to-Have (Phase 4-5)
8. Full accessibility implementation
9. TalkBack support
10. Debug visualization overlay
11. Export/import session logs

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Performance impact | High | Use conditional compilation, disable in release |
| Log file size | Medium | Implement rotation, max size limits |
| Privacy concerns | High | Add clear user consent, don't log output content |
| Accessibility complexity | Medium | Start with basic announcements, iterate |
| Test flakiness | Medium | Use event synchronization, proper timeouts |

---

## Success Metrics

### Instrumentation Success
- Can capture 100% of touch events with <5ms overhead
- Logs contain sufficient data to replay sessions
- Can be toggled without app restart
- Zero crashes related to instrumentation

### Testing Success
- 80%+ code coverage of touch handling paths
- Can automatically test all keys in all layouts
- Gesture recognition has <1% false positive rate
- Test suite runs in <5 minutes

### Accessibility Success
- TalkBack users can type effectively
- Touch exploration announces all keys correctly
- All gestures have audio feedback
- Meets WCAG 2.1 Level AA standards

---

## Next Steps

1. **Start with Phase 1** - Create the TouchInstrumentation framework
2. **Instrument 3-5 key points** - Touch down/up, swipe detection, key output
3. **Validate with manual testing** - Ensure logs are useful and complete
4. **Iterate on data format** - Adjust based on what you need for testing/accessibility
5. **Expand coverage** - Add remaining instrumentation points
6. **Build test infrastructure** - Once instrumentation is stable
7. **Implement accessibility** - Leverage instrumentation for announcements
