package juloo.keyboard2.testing;

import juloo.keyboard2.TouchInstrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TouchEventMatcher provides utilities for validating touch events in automated tests.
 * Supports pattern matching, sequence validation, and assertion-style checks.
 *
 * Example usage:
 * <pre>
 * TouchEventMatcher matcher = new TouchEventMatcher(events);
 * matcher.assertEventSequence(
 *     EventType.TOUCH_DOWN,
 *     EventType.TOUCH_UP
 * );
 * matcher.assertEventExists(EventType.SWIPE_DETECTED, "keyName", "a");
 * </pre>
 */
public class TouchEventMatcher
{
  private final List<TouchEvent> events;
  private final List<AssertionFailure> failures;

  public TouchEventMatcher(List<TouchEvent> events)
  {
    this.events = new ArrayList<>(events);
    this.failures = new ArrayList<>();
  }

  /**
   * Assert that a specific event sequence occurs in order
   */
  public boolean assertEventSequence(TouchInstrumentation.EventType... expectedTypes)
  {
    if (expectedTypes.length > events.size())
    {
      failures.add(new AssertionFailure(
          "Event sequence too short",
          "Expected " + expectedTypes.length + " events, found " + events.size()
      ));
      return false;
    }

    for (int i = 0; i < expectedTypes.length; i++)
    {
      int eventIndex = findNextEvent(expectedTypes[i], i);
      if (eventIndex == -1)
      {
        failures.add(new AssertionFailure(
            "Event sequence mismatch at position " + i,
            "Expected " + expectedTypes[i] + " but not found after position " + i
        ));
        return false;
      }
    }

    return true;
  }

  /**
   * Assert that an event of a specific type exists with matching criteria
   */
  public boolean assertEventExists(TouchInstrumentation.EventType eventType,
                                   String dataKey, Object expectedValue)
  {
    for (TouchEvent event : events)
    {
      if (event.eventType == eventType)
      {
        Object actualValue = event.data.get(dataKey);
        if (expectedValue == null ? actualValue == null : expectedValue.equals(actualValue))
        {
          return true;
        }
      }
    }

    failures.add(new AssertionFailure(
        "Event not found",
        String.format("Expected %s with %s=%s", eventType, dataKey, expectedValue)
    ));
    return false;
  }

  /**
   * Assert that a specific key was pressed (TOUCH_DOWN on that key)
   */
  public boolean assertKeyPressed(String keyName)
  {
    return assertEventExists(TouchInstrumentation.EventType.TOUCH_DOWN, "keyName", keyName);
  }

  /**
   * Assert that a swipe was performed on a key in a specific direction
   */
  public boolean assertSwipe(String baseKey, int direction)
  {
    for (TouchEvent event : events)
    {
      if (event.eventType == TouchInstrumentation.EventType.SWIPE_DETECTED)
      {
        String eventBaseKey = event.getString("baseKey");
        Integer eventDirection = event.getInt("direction");

        if (baseKey.equals(eventBaseKey) && eventDirection != null && eventDirection == direction)
        {
          return true;
        }
      }
    }

    failures.add(new AssertionFailure(
        "Swipe not found",
        String.format("Expected swipe on %s in direction %d", baseKey, direction)
    ));
    return false;
  }

  /**
   * Assert that a gesture was detected
   */
  public boolean assertGesture(String gestureName)
  {
    return assertEventExists(TouchInstrumentation.EventType.GESTURE_DETECTED, "gestureName", gestureName);
  }

  /**
   * Assert that a long press occurred on a key
   */
  public boolean assertLongPress(String keyName)
  {
    for (TouchEvent event : events)
    {
      if (event.eventType == TouchInstrumentation.EventType.LONG_PRESS)
      {
        String eventKeyValue = event.getString("keyValue");
        if (eventKeyValue != null && eventKeyValue.contains(keyName))
        {
          return true;
        }
      }
    }

    failures.add(new AssertionFailure(
        "Long press not found",
        "Expected long press on key: " + keyName
    ));
    return false;
  }

  /**
   * Assert event count matches expected
   */
  public boolean assertEventCount(TouchInstrumentation.EventType eventType, int expectedCount)
  {
    int actualCount = countEvents(eventType);
    if (actualCount != expectedCount)
    {
      failures.add(new AssertionFailure(
          "Event count mismatch",
          String.format("Expected %d %s events, found %d", expectedCount, eventType, actualCount)
      ));
      return false;
    }
    return true;
  }

  /**
   * Assert that touch down and touch up counts match (no orphaned touches)
   */
  public boolean assertBalancedTouches()
  {
    int downCount = countEvents(TouchInstrumentation.EventType.TOUCH_DOWN);
    int upCount = countEvents(TouchInstrumentation.EventType.TOUCH_UP);

    if (downCount != upCount)
    {
      failures.add(new AssertionFailure(
          "Unbalanced touches",
          String.format("DOWN=%d, UP=%d", downCount, upCount)
      ));
      return false;
    }
    return true;
  }

  /**
   * Assert that events occur within a specific time window
   */
  public boolean assertTiming(TouchInstrumentation.EventType eventType,
                              long minDurationMs, long maxDurationMs)
  {
    TouchEvent firstEvent = findFirstEvent(eventType);
    TouchEvent lastEvent = findLastEvent(eventType);

    if (firstEvent == null || lastEvent == null)
    {
      failures.add(new AssertionFailure(
          "Timing check failed",
          "Event type " + eventType + " not found"
      ));
      return false;
    }

    long duration = lastEvent.timestamp - firstEvent.timestamp;
    if (duration < minDurationMs || duration > maxDurationMs)
    {
      failures.add(new AssertionFailure(
          "Timing out of range",
          String.format("%s duration %dms not in range [%d, %d]",
                       eventType, duration, minDurationMs, maxDurationMs)
      ));
      return false;
    }

    return true;
  }

  /**
   * Assert that no events of a specific type occurred
   */
  public boolean assertNoEvent(TouchInstrumentation.EventType eventType)
  {
    int count = countEvents(eventType);
    if (count > 0)
    {
      failures.add(new AssertionFailure(
          "Unexpected events found",
          String.format("Found %d %s events, expected 0", count, eventType)
      ));
      return false;
    }
    return true;
  }

  /**
   * Find the next event of a specific type starting from a position
   */
  private int findNextEvent(TouchInstrumentation.EventType eventType, int startIndex)
  {
    for (int i = startIndex; i < events.size(); i++)
    {
      if (events.get(i).eventType == eventType)
      {
        return i;
      }
    }
    return -1;
  }

  /**
   * Count events of a specific type
   */
  public int countEvents(TouchInstrumentation.EventType eventType)
  {
    int count = 0;
    for (TouchEvent event : events)
    {
      if (event.eventType == eventType)
      {
        count++;
      }
    }
    return count;
  }

  /**
   * Find the first event of a specific type
   */
  private TouchEvent findFirstEvent(TouchInstrumentation.EventType eventType)
  {
    for (TouchEvent event : events)
    {
      if (event.eventType == eventType)
      {
        return event;
      }
    }
    return null;
  }

  /**
   * Find the last event of a specific type
   */
  private TouchEvent findLastEvent(TouchInstrumentation.EventType eventType)
  {
    for (int i = events.size() - 1; i >= 0; i--)
    {
      if (events.get(i).eventType == eventType)
      {
        return events.get(i);
      }
    }
    return null;
  }

  /**
   * Get all assertion failures
   */
  public List<AssertionFailure> getFailures()
  {
    return new ArrayList<>(failures);
  }

  /**
   * Check if all assertions passed
   */
  public boolean hasFailures()
  {
    return !failures.isEmpty();
  }

  /**
   * Get a summary of all failures
   */
  public String getFailureSummary()
  {
    if (failures.isEmpty())
    {
      return "All assertions passed";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Assertion failures (").append(failures.size()).append("):\n");
    for (int i = 0; i < failures.size(); i++)
    {
      sb.append("  ").append(i + 1).append(". ").append(failures.get(i)).append("\n");
    }
    return sb.toString();
  }

  /**
   * Clear all failures
   */
  public void clearFailures()
  {
    failures.clear();
  }

  /**
   * Represents an assertion failure
   */
  public static class AssertionFailure
  {
    public final String message;
    public final String details;

    public AssertionFailure(String message, String details)
    {
      this.message = message;
      this.details = details;
    }

    @Override
    public String toString()
    {
      return message + ": " + details;
    }
  }

  /**
   * Builder for creating complex event pattern matchers
   */
  public static class PatternBuilder
  {
    private final List<EventPattern> patterns = new ArrayList<>();

    public PatternBuilder expectEvent(TouchInstrumentation.EventType eventType)
    {
      patterns.add(new EventPattern(eventType, new HashMap<>()));
      return this;
    }

    public PatternBuilder withData(String key, Object value)
    {
      if (!patterns.isEmpty())
      {
        patterns.get(patterns.size() - 1).requiredData.put(key, value);
      }
      return this;
    }

    public boolean matches(List<TouchEvent> events)
    {
      int patternIndex = 0;
      for (TouchEvent event : events)
      {
        if (patternIndex >= patterns.size())
        {
          break;
        }

        EventPattern pattern = patterns.get(patternIndex);
        if (pattern.matches(event))
        {
          patternIndex++;
        }
      }

      return patternIndex == patterns.size();
    }

    private static class EventPattern
    {
      final TouchInstrumentation.EventType eventType;
      final Map<String, Object> requiredData;

      EventPattern(TouchInstrumentation.EventType eventType, Map<String, Object> requiredData)
      {
        this.eventType = eventType;
        this.requiredData = requiredData;
      }

      boolean matches(TouchEvent event)
      {
        if (event.eventType != eventType)
        {
          return false;
        }

        for (Map.Entry<String, Object> entry : requiredData.entrySet())
        {
          Object actualValue = event.data.get(entry.getKey());
          if (!entry.getValue().equals(actualValue))
          {
            return false;
          }
        }

        return true;
      }
    }
  }
}
