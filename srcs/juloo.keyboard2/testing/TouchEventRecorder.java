package juloo.keyboard2.testing;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import juloo.keyboard2.TouchInstrumentation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TouchEventRecorder provides utilities for recording and managing touch event sessions.
 * This enables test recording, playback, and analysis.
 *
 * Features:
 * - Session-based recording
 * - JSON-based storage format
 * - Load/save recorded sessions
 * - Event filtering and querying
 * - Compatible with TouchInstrumentation output
 */
public class TouchEventRecorder
{
  private static final String TAG = "TouchEventRecorder";

  private Context _context;
  private List<TouchEvent> _recordedEvents;
  private String _sessionId;
  private long _sessionStartTime;
  private boolean _isRecording;

  public TouchEventRecorder(Context context)
  {
    _context = context;
    _recordedEvents = new ArrayList<>();
    _isRecording = false;
  }

  /**
   * Start recording a new session
   */
  public void startRecording(String sessionId)
  {
    if (_isRecording)
    {
      Log.w(TAG, "Already recording, stopping previous session");
      stopRecording();
    }

    _sessionId = sessionId;
    _sessionStartTime = System.currentTimeMillis();
    _recordedEvents.clear();
    _isRecording = true;
    Log.i(TAG, "Started recording session: " + sessionId);
  }

  /**
   * Stop recording and return the number of events recorded
   */
  public int stopRecording()
  {
    if (!_isRecording)
    {
      Log.w(TAG, "Not currently recording");
      return 0;
    }

    _isRecording = false;
    int eventCount = _recordedEvents.size();
    Log.i(TAG, "Stopped recording session: " + _sessionId + ", recorded " + eventCount + " events");
    return eventCount;
  }

  /**
   * Record a touch event (called manually or via instrumentation)
   */
  public void recordEvent(TouchInstrumentation.EventType eventType, Bundle data)
  {
    if (!_isRecording)
    {
      return;
    }

    TouchEvent event = TouchEvent.fromBundle(eventType, data, System.currentTimeMillis());
    _recordedEvents.add(event);
  }

  /**
   * Get all recorded events
   */
  public List<TouchEvent> getRecordedEvents()
  {
    return new ArrayList<>(_recordedEvents);
  }

  /**
   * Get events of a specific type
   */
  public List<TouchEvent> getEventsByType(TouchInstrumentation.EventType eventType)
  {
    List<TouchEvent> filtered = new ArrayList<>();
    for (TouchEvent event : _recordedEvents)
    {
      if (event.eventType == eventType)
      {
        filtered.add(event);
      }
    }
    return filtered;
  }

  /**
   * Save recorded session to a file
   */
  public boolean saveSession(String filename)
  {
    if (_recordedEvents.isEmpty())
    {
      Log.w(TAG, "No events to save");
      return false;
    }

    try
    {
      File sessionDir = new File(_context.getExternalCacheDir(), "test_sessions");
      if (!sessionDir.exists())
      {
        sessionDir.mkdirs();
      }

      File sessionFile = new File(sessionDir, filename);
      BufferedWriter writer = new BufferedWriter(new FileWriter(sessionFile));

      // Write session metadata
      JSONObject sessionData = new JSONObject();
      sessionData.put("sessionId", _sessionId);
      sessionData.put("startTime", _sessionStartTime);
      sessionData.put("eventCount", _recordedEvents.size());
      sessionData.put("duration", System.currentTimeMillis() - _sessionStartTime);

      // Write events array
      JSONArray eventsArray = new JSONArray();
      for (TouchEvent event : _recordedEvents)
      {
        eventsArray.put(event.toJSON());
      }
      sessionData.put("events", eventsArray);

      writer.write(sessionData.toString(2)); // Pretty print with indent
      writer.close();

      Log.i(TAG, "Session saved to: " + sessionFile.getAbsolutePath());
      return true;
    }
    catch (JSONException | IOException e)
    {
      Log.e(TAG, "Error saving session", e);
      return false;
    }
  }

  /**
   * Load a recorded session from a file
   */
  public boolean loadSession(String filename)
  {
    try
    {
      File sessionDir = new File(_context.getExternalCacheDir(), "test_sessions");
      File sessionFile = new File(sessionDir, filename);

      if (!sessionFile.exists())
      {
        Log.e(TAG, "Session file not found: " + filename);
        return false;
      }

      BufferedReader reader = new BufferedReader(new FileReader(sessionFile));
      StringBuilder jsonStr = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null)
      {
        jsonStr.append(line);
      }
      reader.close();

      JSONObject sessionData = new JSONObject(jsonStr.toString());
      _sessionId = sessionData.getString("sessionId");
      _sessionStartTime = sessionData.getLong("startTime");

      _recordedEvents.clear();
      JSONArray eventsArray = sessionData.getJSONArray("events");
      for (int i = 0; i < eventsArray.length(); i++)
      {
        JSONObject eventJson = eventsArray.getJSONObject(i);
        TouchEvent event = TouchEvent.fromJSON(eventJson);
        _recordedEvents.add(event);
      }

      Log.i(TAG, "Session loaded from: " + sessionFile.getAbsolutePath() +
            ", events: " + _recordedEvents.size());
      return true;
    }
    catch (JSONException | IOException e)
    {
      Log.e(TAG, "Error loading session", e);
      return false;
    }
  }

  /**
   * Parse a TouchInstrumentation log file and load it as a session
   */
  public boolean loadFromInstrumentationLog(File logFile)
  {
    try
    {
      BufferedReader reader = new BufferedReader(new FileReader(logFile));
      _recordedEvents.clear();

      String line;
      while ((line = reader.readLine()) != null)
      {
        try
        {
          JSONObject eventJson = new JSONObject(line);
          TouchEvent event = TouchEvent.fromInstrumentationJSON(eventJson);
          _recordedEvents.add(event);

          // Set session info from first event
          if (_recordedEvents.size() == 1)
          {
            _sessionId = eventJson.optString("sessionId", "imported");
            _sessionStartTime = eventJson.optLong("timestamp", System.currentTimeMillis());
          }
        }
        catch (JSONException e)
        {
          // Skip invalid lines
          Log.w(TAG, "Skipping invalid JSON line: " + line);
        }
      }
      reader.close();

      Log.i(TAG, "Loaded " + _recordedEvents.size() + " events from instrumentation log");
      return true;
    }
    catch (IOException e)
    {
      Log.e(TAG, "Error loading instrumentation log", e);
      return false;
    }
  }

  /**
   * Get statistics about the recorded session
   */
  public SessionStats getStats()
  {
    return new SessionStats(_sessionId, _recordedEvents);
  }

  /**
   * Clear all recorded events
   */
  public void clear()
  {
    _recordedEvents.clear();
    _sessionId = null;
    _sessionStartTime = 0;
    _isRecording = false;
  }

  /**
   * Check if currently recording
   */
  public boolean isRecording()
  {
    return _isRecording;
  }

  /**
   * Statistics about a recorded session
   */
  public static class SessionStats
  {
    public final String sessionId;
    public final int totalEvents;
    public final int touchDownCount;
    public final int touchUpCount;
    public final int swipeCount;
    public final int gestureCount;
    public final int longPressCount;
    public final long durationMs;

    public SessionStats(String sessionId, List<TouchEvent> events)
    {
      this.sessionId = sessionId;
      this.totalEvents = events.size();

      int downCount = 0, upCount = 0, swipeCount = 0, gestureCount = 0, longPressCount = 0;
      long minTime = Long.MAX_VALUE, maxTime = 0;

      for (TouchEvent event : events)
      {
        if (event.timestamp < minTime) minTime = event.timestamp;
        if (event.timestamp > maxTime) maxTime = event.timestamp;

        switch (event.eventType)
        {
          case TOUCH_DOWN: downCount++; break;
          case TOUCH_UP: upCount++; break;
          case SWIPE_DETECTED: swipeCount++; break;
          case GESTURE_DETECTED: gestureCount++; break;
          case LONG_PRESS: longPressCount++; break;
        }
      }

      this.touchDownCount = downCount;
      this.touchUpCount = upCount;
      this.swipeCount = swipeCount;
      this.gestureCount = gestureCount;
      this.longPressCount = longPressCount;
      this.durationMs = (maxTime > minTime) ? (maxTime - minTime) : 0;
    }

    @Override
    public String toString()
    {
      return String.format(Locale.US,
          "Session: %s, Events: %d (down=%d, up=%d, swipe=%d, gesture=%d, longPress=%d), Duration: %.1fs",
          sessionId, totalEvents, touchDownCount, touchUpCount, swipeCount, gestureCount,
          longPressCount, durationMs / 1000.0);
    }
  }
}
