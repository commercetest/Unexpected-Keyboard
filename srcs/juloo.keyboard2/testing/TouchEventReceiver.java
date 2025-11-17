package juloo.keyboard2.testing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import juloo.keyboard2.TouchInstrumentation;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TouchEventReceiver provides synchronous event reception for automated tests.
 * Receives broadcast events from TouchInstrumentation and allows tests to wait
 * for specific events to occur.
 *
 * Example usage in tests:
 * <pre>
 * TouchEventReceiver receiver = new TouchEventReceiver(context);
 * receiver.register();
 *
 * // Perform touch action
 * device.click(x, y);
 *
 * // Wait for and verify the event
 * TouchEvent event = receiver.waitForEvent(EventType.TOUCH_DOWN, 1000);
 * assertNotNull(event);
 * assertEquals("a", event.getString("keyName"));
 *
 * receiver.unregister();
 * </pre>
 */
public class TouchEventReceiver extends BroadcastReceiver
{
  private static final String TAG = "TouchEventReceiver";
  private static final String BROADCAST_ACTION = "juloo.keyboard2.TOUCH_EVENT";

  private final Context context;
  private final List<TouchEvent> receivedEvents;
  private final Handler handler;

  private CountDownLatch eventLatch;
  private TouchInstrumentation.EventType waitingForType;
  private TouchEvent matchedEvent;
  private boolean registered;

  public TouchEventReceiver(Context context)
  {
    this.context = context;
    this.receivedEvents = new ArrayList<>();
    this.handler = new Handler(Looper.getMainLooper());
    this.registered = false;
  }

  /**
   * Register this receiver to start receiving broadcasts
   */
  public void register()
  {
    if (registered)
    {
      Log.w(TAG, "Already registered");
      return;
    }

    IntentFilter filter = new IntentFilter(BROADCAST_ACTION);
    context.registerReceiver(this, filter);
    registered = true;
    Log.i(TAG, "Receiver registered");
  }

  /**
   * Unregister this receiver to stop receiving broadcasts
   */
  public void unregister()
  {
    if (!registered)
    {
      Log.w(TAG, "Not registered");
      return;
    }

    context.unregisterReceiver(this);
    registered = false;
    Log.i(TAG, "Receiver unregistered");
  }

  @Override
  public void onReceive(Context context, Intent intent)
  {
    String eventJson = intent.getStringExtra("event");
    if (eventJson == null)
    {
      Log.w(TAG, "Received broadcast with no event data");
      return;
    }

    try
    {
      JSONObject json = new JSONObject(eventJson);
      TouchEvent event = TouchEvent.fromInstrumentationJSON(json);

      synchronized (receivedEvents)
      {
        receivedEvents.add(event);
      }

      Log.d(TAG, "Received event: " + event.eventType);

      // Check if this is the event we're waiting for
      if (eventLatch != null && waitingForType != null)
      {
        if (event.eventType == waitingForType)
        {
          matchedEvent = event;
          eventLatch.countDown();
        }
      }
    }
    catch (JSONException e)
    {
      Log.e(TAG, "Error parsing event JSON", e);
    }
  }

  /**
   * Wait for a specific event type to be received
   *
   * @param eventType The type of event to wait for
   * @param timeoutMs Maximum time to wait in milliseconds
   * @return The received event, or null if timeout
   */
  public TouchEvent waitForEvent(TouchInstrumentation.EventType eventType, long timeoutMs)
  {
    eventLatch = new CountDownLatch(1);
    waitingForType = eventType;
    matchedEvent = null;

    try
    {
      boolean received = eventLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
      if (!received)
      {
        Log.w(TAG, "Timeout waiting for " + eventType);
        return null;
      }
      return matchedEvent;
    }
    catch (InterruptedException e)
    {
      Log.e(TAG, "Interrupted while waiting for event", e);
      return null;
    }
    finally
    {
      eventLatch = null;
      waitingForType = null;
      matchedEvent = null;
    }
  }

  /**
   * Wait for any event to be received
   *
   * @param timeoutMs Maximum time to wait in milliseconds
   * @return The received event, or null if timeout
   */
  public TouchEvent waitForAnyEvent(long timeoutMs)
  {
    int currentSize = receivedEvents.size();
    long startTime = System.currentTimeMillis();

    while (System.currentTimeMillis() - startTime < timeoutMs)
    {
      synchronized (receivedEvents)
      {
        if (receivedEvents.size() > currentSize)
        {
          return receivedEvents.get(receivedEvents.size() - 1);
        }
      }

      try
      {
        Thread.sleep(10);
      }
      catch (InterruptedException e)
      {
        return null;
      }
    }

    return null;
  }

  /**
   * Wait for a specific event sequence
   *
   * @param timeoutMs Maximum time to wait for the entire sequence
   * @param eventTypes Sequence of event types to wait for
   * @return true if the sequence was received, false if timeout
   */
  public boolean waitForEventSequence(long timeoutMs, TouchInstrumentation.EventType... eventTypes)
  {
    long startTime = System.currentTimeMillis();
    int startIndex = receivedEvents.size();

    for (TouchInstrumentation.EventType eventType : eventTypes)
    {
      long remaining = timeoutMs - (System.currentTimeMillis() - startTime);
      if (remaining <= 0)
      {
        return false;
      }

      TouchEvent event = waitForEvent(eventType, remaining);
      if (event == null)
      {
        return false;
      }
    }

    return true;
  }

  /**
   * Get all received events
   */
  public List<TouchEvent> getReceivedEvents()
  {
    synchronized (receivedEvents)
    {
      return new ArrayList<>(receivedEvents);
    }
  }

  /**
   * Get events received since a specific time
   */
  public List<TouchEvent> getEventsSince(long timestamp)
  {
    List<TouchEvent> filtered = new ArrayList<>();
    synchronized (receivedEvents)
    {
      for (TouchEvent event : receivedEvents)
      {
        if (event.timestamp >= timestamp)
        {
          filtered.add(event);
        }
      }
    }
    return filtered;
  }

  /**
   * Clear all received events
   */
  public void clearEvents()
  {
    synchronized (receivedEvents)
    {
      receivedEvents.clear();
    }
    Log.i(TAG, "Cleared all received events");
  }

  /**
   * Get the count of received events
   */
  public int getEventCount()
  {
    synchronized (receivedEvents)
    {
      return receivedEvents.size();
    }
  }

  /**
   * Get the count of events of a specific type
   */
  public int getEventCount(TouchInstrumentation.EventType eventType)
  {
    int count = 0;
    synchronized (receivedEvents)
    {
      for (TouchEvent event : receivedEvents)
      {
        if (event.eventType == eventType)
        {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Check if the receiver is currently registered
   */
  public boolean isRegistered()
  {
    return registered;
  }
}
