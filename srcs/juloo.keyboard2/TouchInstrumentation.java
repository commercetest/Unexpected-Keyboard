package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * TouchInstrumentation provides comprehensive logging and instrumentation for touch events,
 * gestures, and keyboard interactions. This enables automated testing and accessibility support.
 *
 * Features:
 * - Multiple output modes: LOGCAT, FILE, BROADCAST
 * - Structured JSON logging
 * - Session tracking
 * - Thread-safe operation
 * - Runtime enable/disable
 */
public class TouchInstrumentation
{
  private static final String TAG = "TouchInstrumentation";
  private static final String BROADCAST_ACTION = "juloo.keyboard2.TOUCH_EVENT";

  // Maximum log file size (5MB)
  private static final long MAX_LOG_FILE_SIZE = 5 * 1024 * 1024;

  // Singleton instance
  private static TouchInstrumentation _instance;

  // Configuration
  private boolean _enabled = false;
  private OutputMode _outputMode = OutputMode.LOGCAT;
  private VerbosityLevel _verbosity = VerbosityLevel.STANDARD;

  // Session tracking
  private String _sessionId;
  private long _sessionStartTime;
  private int _eventCount = 0;

  // Context for broadcasts and file access
  private Context _context;

  // File output
  private File _logFile;
  private BufferedWriter _logWriter;

  // Layout tracking
  private String _currentLayout = "unknown";

  /**
   * Event types that can be logged
   */
  public enum EventType
  {
    TOUCH_DOWN,
    TOUCH_MOVE,
    TOUCH_UP,
    TOUCH_CANCEL,
    SWIPE_DETECTED,
    GESTURE_DETECTED,
    LONG_PRESS,
    KEY_SELECTED,
    KEY_OUTPUT,
    POINTER_STATE_CHANGE,
    MODIFIER_CHANGE,
    LAYOUT_CHANGE,
    SESSION_START,
    SESSION_END
  }

  /**
   * Output modes for instrumentation data
   */
  public enum OutputMode
  {
    LOGCAT,    // Output to Android logcat
    FILE,      // Output to file in app's cache directory
    BROADCAST, // Broadcast events for test receivers
    ALL        // All of the above
  }

  /**
   * Verbosity levels
   */
  public enum VerbosityLevel
  {
    MINIMAL,   // Only key events (down/up/output)
    STANDARD,  // Key events + gestures + state changes
    VERBOSE    // All events including moves
  }

  /**
   * Private constructor for singleton
   */
  private TouchInstrumentation()
  {
    _sessionId = generateSessionId();
    _sessionStartTime = System.currentTimeMillis();
  }

  /**
   * Initialize the instrumentation framework
   */
  public static synchronized void initialize(Context context)
  {
    if (_instance == null)
    {
      _instance = new TouchInstrumentation();
      _instance._context = context.getApplicationContext();
    }
  }

  /**
   * Get the singleton instance
   */
  private static TouchInstrumentation getInstance()
  {
    if (_instance == null)
    {
      throw new IllegalStateException("TouchInstrumentation not initialized. Call initialize() first.");
    }
    return _instance;
  }

  /**
   * Check if instrumentation is enabled
   */
  public static boolean isEnabled()
  {
    if (_instance == null) return false;
    return _instance._enabled;
  }

  /**
   * Enable or disable instrumentation
   */
  public static synchronized void setEnabled(boolean enabled)
  {
    TouchInstrumentation inst = getInstance();

    if (enabled && !inst._enabled)
    {
      // Starting new session
      inst._sessionId = inst.generateSessionId();
      inst._sessionStartTime = System.currentTimeMillis();
      inst._eventCount = 0;
      inst.openLogFile();
      logEvent(EventType.SESSION_START, new Bundle());
      Log.i(TAG, "Instrumentation enabled. Session ID: " + inst._sessionId);
    }
    else if (!enabled && inst._enabled)
    {
      // Ending session
      logEvent(EventType.SESSION_END, new Bundle());
      inst.closeLogFile();
      Log.i(TAG, "Instrumentation disabled. Events logged: " + inst._eventCount);
    }

    inst._enabled = enabled;
  }

  /**
   * Set the output mode
   */
  public static void setOutputMode(OutputMode mode)
  {
    TouchInstrumentation inst = getInstance();
    inst._outputMode = mode;

    if (mode == OutputMode.FILE || mode == OutputMode.ALL)
    {
      inst.openLogFile();
    }
  }

  /**
   * Get the current output mode
   */
  public static OutputMode getOutputMode()
  {
    return getInstance()._outputMode;
  }

  /**
   * Set the verbosity level
   */
  public static void setVerbosity(VerbosityLevel level)
  {
    getInstance()._verbosity = level;
  }

  /**
   * Get the current verbosity level
   */
  public static VerbosityLevel getVerbosity()
  {
    return getInstance()._verbosity;
  }

  /**
   * Set the current layout name for context
   */
  public static void setCurrentLayout(String layoutName)
  {
    TouchInstrumentation inst = getInstance();
    if (!layoutName.equals(inst._currentLayout))
    {
      inst._currentLayout = layoutName;
      if (isEnabled())
      {
        Bundle data = new Bundle();
        data.putString("layoutName", layoutName);
        logEvent(EventType.LAYOUT_CHANGE, data);
      }
    }
  }

  /**
   * Main logging method
   */
  public static void logEvent(EventType eventType, Bundle data)
  {
    TouchInstrumentation inst = getInstance();

    if (!inst._enabled) return;

    // Filter based on verbosity
    if (!inst.shouldLog(eventType)) return;

    try
    {
      JSONObject event = inst.createEventJson(eventType, data);
      inst.outputEvent(event);
      inst._eventCount++;
    }
    catch (JSONException e)
    {
      Log.e(TAG, "Error creating event JSON", e);
    }
  }

  /**
   * Determine if an event should be logged based on verbosity level
   */
  private boolean shouldLog(EventType eventType)
  {
    switch (_verbosity)
    {
      case MINIMAL:
        return eventType == EventType.TOUCH_DOWN ||
               eventType == EventType.TOUCH_UP ||
               eventType == EventType.KEY_OUTPUT ||
               eventType == EventType.SESSION_START ||
               eventType == EventType.SESSION_END;

      case STANDARD:
        return eventType != EventType.TOUCH_MOVE;

      case VERBOSE:
        return true;

      default:
        return true;
    }
  }

  /**
   * Create JSON object for an event
   */
  private JSONObject createEventJson(EventType eventType, Bundle data) throws JSONException
  {
    JSONObject event = new JSONObject();

    // Core event data
    event.put("timestamp", System.currentTimeMillis());
    event.put("eventType", eventType.name());
    event.put("sessionId", _sessionId);
    event.put("eventNumber", _eventCount);
    event.put("sessionTime", System.currentTimeMillis() - _sessionStartTime);

    // Layout context
    event.put("layout", _currentLayout);

    // Event-specific data
    JSONObject eventData = new JSONObject();
    for (String key : data.keySet())
    {
      Object value = data.get(key);
      if (value != null)
      {
        eventData.put(key, value);
      }
    }
    event.put("data", eventData);

    return event;
  }

  /**
   * Output the event based on current output mode
   */
  private void outputEvent(JSONObject event)
  {
    String eventString = event.toString();

    switch (_outputMode)
    {
      case LOGCAT:
        Log.i(TAG, eventString);
        break;

      case FILE:
        writeToFile(eventString);
        break;

      case BROADCAST:
        broadcastEvent(eventString);
        break;

      case ALL:
        Log.i(TAG, eventString);
        writeToFile(eventString);
        broadcastEvent(eventString);
        break;
    }
  }

  /**
   * Write event to log file
   */
  private synchronized void writeToFile(String event)
  {
    if (_logWriter == null)
    {
      openLogFile();
    }

    try
    {
      if (_logWriter != null)
      {
        _logWriter.write(event);
        _logWriter.newLine();
        _logWriter.flush();

        // Check file size and rotate if needed
        if (_logFile != null && _logFile.length() > MAX_LOG_FILE_SIZE)
        {
          rotateLogFile();
        }
      }
    }
    catch (IOException e)
    {
      Log.e(TAG, "Error writing to log file", e);
    }
  }

  /**
   * Broadcast event for test receivers
   */
  private void broadcastEvent(String event)
  {
    if (_context == null) return;

    try
    {
      Intent intent = new Intent(BROADCAST_ACTION);
      intent.putExtra("event", event);
      _context.sendBroadcast(intent);
    }
    catch (Exception e)
    {
      Log.e(TAG, "Error broadcasting event", e);
    }
  }

  /**
   * Open log file for writing
   */
  private void openLogFile()
  {
    if (_context == null) return;

    try
    {
      File cacheDir = _context.getCacheDir();
      _logFile = new File(cacheDir, "touch_instrumentation.log");
      _logWriter = new BufferedWriter(new FileWriter(_logFile, true));
      Log.i(TAG, "Log file opened: " + _logFile.getAbsolutePath());
    }
    catch (IOException e)
    {
      Log.e(TAG, "Error opening log file", e);
      _logWriter = null;
    }
  }

  /**
   * Close log file
   */
  private void closeLogFile()
  {
    if (_logWriter != null)
    {
      try
      {
        _logWriter.close();
        Log.i(TAG, "Log file closed");
      }
      catch (IOException e)
      {
        Log.e(TAG, "Error closing log file", e);
      }
      finally
      {
        _logWriter = null;
      }
    }
  }

  /**
   * Rotate log file when it gets too large
   */
  private void rotateLogFile()
  {
    closeLogFile();

    if (_logFile != null)
    {
      // Rename current file with timestamp
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
      String timestamp = sdf.format(new Date());
      File rotatedFile = new File(_logFile.getParent(),
                                   "touch_instrumentation_" + timestamp + ".log");

      if (_logFile.renameTo(rotatedFile))
      {
        Log.i(TAG, "Log file rotated to: " + rotatedFile.getName());
      }
    }

    openLogFile();
  }

  /**
   * Get the current log file path
   */
  public static String getLogFilePath()
  {
    TouchInstrumentation inst = getInstance();
    return inst._logFile != null ? inst._logFile.getAbsolutePath() : null;
  }

  /**
   * Get session statistics
   */
  public static String getSessionStats()
  {
    TouchInstrumentation inst = getInstance();
    long duration = System.currentTimeMillis() - inst._sessionStartTime;
    return String.format(Locale.US,
                        "Session: %s, Events: %d, Duration: %.1fs",
                        inst._sessionId,
                        inst._eventCount,
                        duration / 1000.0);
  }

  /**
   * Generate a unique session ID
   */
  private String generateSessionId()
  {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  /**
   * Helper method to create a Bundle with common touch data
   */
  public static Bundle createTouchBundle(float x, float y, int pointerId)
  {
    Bundle bundle = new Bundle();
    bundle.putFloat("x", x);
    bundle.putFloat("y", y);
    bundle.putInt("pointerId", pointerId);
    return bundle;
  }

  /**
   * Helper method to create a Bundle with key information
   */
  public static Bundle createKeyBundle(String keyName)
  {
    Bundle bundle = new Bundle();
    bundle.putString("keyName", keyName);
    return bundle;
  }
}
