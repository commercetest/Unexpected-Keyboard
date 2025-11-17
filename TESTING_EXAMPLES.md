# Touch Instrumentation Testing Examples

This document provides examples of how to use the touch instrumentation testing utilities for automated testing of the Unexpected Keyboard.

## Table of Contents

1. [Setup](#setup)
2. [Recording Sessions](#recording-sessions)
3. [Analyzing Recorded Sessions](#analyzing-recorded-sessions)
4. [Writing Automated Tests](#writing-automated-tests)
5. [Test Synchronization](#test-synchronization)
6. [Validation and Assertions](#validation-and-assertions)

---

## Setup

### Enable Instrumentation

Before running tests, enable touch instrumentation in the keyboard settings:

1. Open keyboard settings
2. Scroll to "Developer options"
3. Enable "Enable touch instrumentation"
4. Set mode to "BROADCAST" for test synchronization
5. Set verbosity to "STANDARD" or "VERBOSE"

### Dependencies

Add to your test dependencies (if using JUnit):

```gradle
androidTestImplementation 'androidx.test:runner:1.5.2'
androidTestImplementation 'androidx.test:rules:1.5.0'
androidTestImplementation 'androidx.test.uiautomator:uiautomator:2.3.0'
```

---

## Recording Sessions

### Manual Recording

Record a user session for later analysis:

```java
import juloo.keyboard2.testing.TouchEventRecorder;

// Initialize recorder
TouchEventRecorder recorder = new TouchEventRecorder(context);

// Start recording
recorder.startRecording("test_session_001");

// ... perform keyboard interactions ...

// Stop recording
int eventCount = recorder.stopRecording();
Log.i("Test", "Recorded " + eventCount + " events");

// Save to file
recorder.saveSession("test_session_001.json");

// Get statistics
TouchEventRecorder.SessionStats stats = recorder.getStats();
Log.i("Test", stats.toString());
```

### Loading from Instrumentation Logs

Convert a TouchInstrumentation log file into a testable session:

```java
// Export log from settings, then pull it
// adb pull /sdcard/Android/data/juloo.keyboard2.debug/cache/touch_instrumentation_*.log

TouchEventRecorder recorder = new TouchEventRecorder(context);
File logFile = new File(externalCache, "touch_instrumentation_20231117_143022.log");

if (recorder.loadFromInstrumentationLog(logFile))
{
  // Analyze the loaded session
  List<TouchEvent> events = recorder.getRecordedEvents();

  // Save as a reusable test session
  recorder.saveSession("real_user_session.json");
}
```

---

## Analyzing Recorded Sessions

### Basic Analysis

```java
TouchEventRecorder recorder = new TouchEventRecorder(context);
recorder.loadSession("test_session_001.json");

// Get specific event types
List<TouchEvent> swipes = recorder.getEventsByType(EventType.SWIPE_DETECTED);
Log.i("Test", "Found " + swipes.size() + " swipes");

// Analyze swipe directions
for (TouchEvent swipe : swipes)
{
  String baseKey = swipe.getString("baseKey");
  Integer direction = swipe.getInt("direction");
  Log.i("Test", "Swipe: " + baseKey + " direction=" + direction);
}

// Get session statistics
SessionStats stats = recorder.getStats();
Log.i("Test", "Session duration: " + stats.durationMs + "ms");
Log.i("Test", "Touch down events: " + stats.touchDownCount);
Log.i("Test", "Swipes: " + stats.swipeCount);
Log.i("Test", "Gestures: " + stats.gestureCount);
```

---

## Writing Automated Tests

### Example Test Class Structure

```java
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import juloo.keyboard2.testing.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardTouchTests
{
  private UiDevice device;
  private TouchEventReceiver receiver;
  private Context context;

  @Before
  public void setUp()
  {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    // Setup event receiver for synchronization
    receiver = new TouchEventReceiver(context);
    receiver.register();
    receiver.clearEvents();
  }

  @After
  public void tearDown()
  {
    if (receiver != null)
    {
      receiver.unregister();
    }
  }

  @Test
  public void testSimpleKeyPress()
  {
    // Simulate a key press at coordinates
    device.click(150, 1800); // Adjust coordinates for your device

    // Wait for touch events
    TouchEvent downEvent = receiver.waitForEvent(EventType.TOUCH_DOWN, 1000);
    TouchEvent upEvent = receiver.waitForEvent(EventType.TOUCH_UP, 1000);

    // Validate events were received
    assertNotNull("Touch down event not received", downEvent);
    assertNotNull("Touch up event not received", upEvent);

    // Validate event data
    assertEquals("a", downEvent.getString("keyName"));
  }

  @Test
  public void testSwipeGesture()
  {
    // Simulate a swipe (would need actual gesture injection)
    // device.swipe(150, 1800, 200, 1800, 10);

    // Wait for swipe detection
    TouchEvent swipeEvent = receiver.waitForEvent(EventType.SWIPE_DETECTED, 2000);

    assertNotNull("Swipe not detected", swipeEvent);

    // Validate swipe data
    String baseKey = swipeEvent.getString("baseKey");
    Integer direction = swipeEvent.getInt("direction");

    assertNotNull("Base key not recorded", baseKey);
    assertNotNull("Direction not recorded", direction);
  }

  @Test
  public void testEventSequence()
  {
    // Type multiple keys
    device.click(150, 1800); // 'a'
    device.click(250, 1800); // 's'
    device.click(350, 1800); // 'd'

    // Wait for the sequence
    boolean sequenceComplete = receiver.waitForEventSequence(3000,
        EventType.TOUCH_DOWN,
        EventType.TOUCH_UP,
        EventType.TOUCH_DOWN,
        EventType.TOUCH_UP,
        EventType.TOUCH_DOWN,
        EventType.TOUCH_UP
    );

    assertTrue("Event sequence not completed", sequenceComplete);

    // Validate all events
    List<TouchEvent> events = receiver.getReceivedEvents();
    assertEquals(6, events.size());
  }
}
```

---

## Test Synchronization

### Using TouchEventReceiver

```java
// Basic synchronous waiting
TouchEventReceiver receiver = new TouchEventReceiver(context);
receiver.register();

// Perform action
device.click(x, y);

// Wait for specific event (with timeout)
TouchEvent event = receiver.waitForEvent(EventType.TOUCH_DOWN, 1000);
if (event != null)
{
  // Event received successfully
  String keyName = event.getString("keyName");
}

// Wait for any event
TouchEvent anyEvent = receiver.waitForAnyEvent(1000);

// Wait for event sequence
boolean success = receiver.waitForEventSequence(2000,
    EventType.TOUCH_DOWN,
    EventType.SWIPE_DETECTED,
    EventType.TOUCH_UP
);

receiver.unregister();
```

### Filtering Events by Time

```java
long testStartTime = System.currentTimeMillis();

// Perform test actions
device.click(x1, y1);
device.click(x2, y2);

// Wait a bit
Thread.sleep(500);

// Get only events from this test
List<TouchEvent> testEvents = receiver.getEventsSince(testStartTime);
```

---

## Validation and Assertions

### Using TouchEventMatcher

```java
// Collect events from a test
List<TouchEvent> events = receiver.getReceivedEvents();

// Create matcher
TouchEventMatcher matcher = new TouchEventMatcher(events);

// Assert specific key was pressed
assertTrue(matcher.assertKeyPressed("a"));

// Assert swipe occurred
assertTrue(matcher.assertSwipe("a", 2)); // direction 2 = right

// Assert gesture detected
assertTrue(matcher.assertGesture("Circle"));

// Assert long press
assertTrue(matcher.assertLongPress("shift"));

// Assert event count
assertTrue(matcher.assertEventCount(EventType.TOUCH_DOWN, 5));

// Assert balanced touches (all downs have corresponding ups)
assertTrue(matcher.assertBalancedTouches());

// Assert timing
assertTrue(matcher.assertTiming(EventType.LONG_PRESS, 500, 700));

// Assert no unexpected events
assertTrue(matcher.assertNoEvent(EventType.TOUCH_CANCEL));

// Check for failures
if (matcher.hasFailures())
{
  System.out.println(matcher.getFailureSummary());
}
```

### Pattern Matching

```java
// Build complex patterns
TouchEventMatcher.PatternBuilder pattern = new TouchEventMatcher.PatternBuilder();
pattern.expectEvent(EventType.TOUCH_DOWN)
       .withData("keyName", "a")
       .expectEvent(EventType.SWIPE_DETECTED)
       .withData("direction", 2)
       .expectEvent(EventType.TOUCH_UP);

// Check if pattern matches
boolean matches = pattern.matches(events);
assertTrue("Expected pattern not found", matches);
```

### Sequential Assertions

```java
TouchEventMatcher matcher = new TouchEventMatcher(events);

// Assert sequence of event types
assertTrue(matcher.assertEventSequence(
    EventType.TOUCH_DOWN,
    EventType.SWIPE_DETECTED,
    EventType.GESTURE_DETECTED,
    EventType.TOUCH_UP
));
```

---

## Advanced Testing Scenarios

### Test Layout Coverage

```java
@Test
public void testAllKeysReachable()
{
  // Test that all keys in the layout can be pressed
  KeyboardData layout = getCurrentLayout();

  for (KeyboardData.Row row : layout.rows)
  {
    for (KeyboardData.Key key : row.keys)
    {
      // Calculate key position and tap it
      // ... coordinate calculation ...

      device.click(x, y);

      TouchEvent event = receiver.waitForEvent(EventType.TOUCH_DOWN, 1000);
      assertNotNull("Key not reachable: " + key.keys[0], event);
    }
  }
}
```

### Test Swipe Accuracy

```java
@Test
public void testSwipeDirectionAccuracy()
{
  // Test all 8 swipe directions from center key
  int[][] directions = {
    {0, -50},   // Up
    {50, -50},  // Up-Right
    {50, 0},    // Right
    {50, 50},   // Down-Right
    {0, 50},    // Down
    {-50, 50},  // Down-Left
    {-50, 0},   // Left
    {-50, -50}  // Up-Left
  };

  for (int i = 0; i < directions.length; i++)
  {
    int dx = directions[i][0];
    int dy = directions[i][1];

    // Swipe in direction
    device.swipe(centerX, centerY, centerX + dx, centerY + dy, 10);

    // Verify correct direction detected
    TouchEvent swipe = receiver.waitForEvent(EventType.SWIPE_DETECTED, 1000);
    assertNotNull("Swipe " + i + " not detected", swipe);

    Integer detectedDir = swipe.getInt("direction");
    assertNotNull("Direction not recorded", detectedDir);

    // Verify direction is in expected range (allowing some tolerance)
    int expectedDir = i * 2; // Convert to 0-15 scale
    assertTrue("Direction mismatch for swipe " + i,
               Math.abs(detectedDir - expectedDir) <= 1);
  }
}
```

### Performance Testing

```java
@Test
public void testTouchToOutputLatency()
{
  List<Long> latencies = new ArrayList<>();

  for (int i = 0; i < 10; i++)
  {
    long startTime = System.currentTimeMillis();

    device.click(x, y);

    // Wait for output event (or completion)
    TouchEvent downEvent = receiver.waitForEvent(EventType.TOUCH_DOWN, 1000);
    TouchEvent upEvent = receiver.waitForEvent(EventType.TOUCH_UP, 1000);

    long endTime = System.currentTimeMillis();
    long latency = endTime - startTime;
    latencies.add(latency);

    Thread.sleep(100); // Brief pause between tests
  }

  // Calculate average latency
  long avgLatency = latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();

  // Assert reasonable latency (< 100ms)
  assertTrue("Average latency too high: " + avgLatency + "ms", avgLatency < 100);
}
```

---

## Tips and Best Practices

1. **Always cleanup**: Use `@After` to unregister receivers and clean up resources
2. **Use timeouts**: Always specify reasonable timeouts for `waitForEvent()` calls
3. **Clear between tests**: Call `receiver.clearEvents()` at the start of each test
4. **Coordinate calculation**: Device coordinates vary; consider calculating from layout dimensions
5. **Test isolation**: Each test should be independent and not rely on state from previous tests
6. **Record first, test later**: Record real user sessions, then write tests to match
7. **Broadcast mode**: Set instrumentation to BROADCAST mode for test synchronization
8. **Debug with logs**: Export instrumentation logs to debug failing tests

---

## Troubleshooting

### Events Not Received

- Check that instrumentation is enabled in settings
- Verify BROADCAST mode is selected
- Ensure receiver is registered before performing actions
- Check logcat for TouchInstrumentation broadcasts

### Timeout Issues

- Increase timeout values for slow devices
- Verify keyboard is actually visible and active
- Check that touches are actually reaching the keyboard view

### Coordinate Mismatches

- Device coordinates vary by screen size and resolution
- Calculate coordinates dynamically based on keyboard dimensions
- Use visual inspection to determine actual key positions

### Permission Errors

- Ensure test app has required permissions
- Check that keyboard is enabled and selected as input method
- Verify accessibility services if using UiAutomator

---

## Next Steps

- Implement actual JUnit test classes based on these examples
- Create test fixtures with common test data
- Add continuous integration for automated test runs
- Build a test report generator
- Create visual playback of recorded sessions

For more information, see TOUCH_INSTRUMENTATION_PLAN.md
