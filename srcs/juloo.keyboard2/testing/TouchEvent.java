package juloo.keyboard2.testing;

import android.os.Bundle;
import juloo.keyboard2.TouchInstrumentation;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Represents a single touch event with all its associated data.
 * Used for recording, playback, and validation in automated tests.
 */
public class TouchEvent
{
  public final TouchInstrumentation.EventType eventType;
  public final long timestamp;
  public final Map<String, Object> data;

  public TouchEvent(TouchInstrumentation.EventType eventType, long timestamp, Map<String, Object> data)
  {
    this.eventType = eventType;
    this.timestamp = timestamp;
    this.data = new HashMap<>(data);
  }

  /**
   * Create a TouchEvent from a Bundle (as used in instrumentation)
   */
  public static TouchEvent fromBundle(TouchInstrumentation.EventType eventType, Bundle bundle, long timestamp)
  {
    Map<String, Object> data = new HashMap<>();

    for (String key : bundle.keySet())
    {
      Object value = bundle.get(key);
      if (value != null)
      {
        data.put(key, value);
      }
    }

    return new TouchEvent(eventType, timestamp, data);
  }

  /**
   * Create a TouchEvent from a JSON object (TouchEventRecorder format)
   */
  public static TouchEvent fromJSON(JSONObject json) throws JSONException
  {
    TouchInstrumentation.EventType eventType =
        TouchInstrumentation.EventType.valueOf(json.getString("eventType"));
    long timestamp = json.getLong("timestamp");

    Map<String, Object> data = new HashMap<>();
    JSONObject dataObj = json.getJSONObject("data");

    Iterator<String> keys = dataObj.keys();
    while (keys.hasNext())
    {
      String key = keys.next();
      data.put(key, dataObj.get(key));
    }

    return new TouchEvent(eventType, timestamp, data);
  }

  /**
   * Create a TouchEvent from a TouchInstrumentation log entry
   */
  public static TouchEvent fromInstrumentationJSON(JSONObject json) throws JSONException
  {
    TouchInstrumentation.EventType eventType =
        TouchInstrumentation.EventType.valueOf(json.getString("eventType"));
    long timestamp = json.getLong("timestamp");

    Map<String, Object> data = new HashMap<>();
    if (json.has("data"))
    {
      JSONObject dataObj = json.getJSONObject("data");
      Iterator<String> keys = dataObj.keys();
      while (keys.hasNext())
      {
        String key = keys.next();
        data.put(key, dataObj.get(key));
      }
    }

    // Also include session info as metadata
    if (json.has("sessionId"))
    {
      data.put("sessionId", json.getString("sessionId"));
    }
    if (json.has("eventNumber"))
    {
      data.put("eventNumber", json.getInt("eventNumber"));
    }
    if (json.has("layout"))
    {
      data.put("layout", json.getString("layout"));
    }

    return new TouchEvent(eventType, timestamp, data);
  }

  /**
   * Convert to JSON for storage
   */
  public JSONObject toJSON() throws JSONException
  {
    JSONObject json = new JSONObject();
    json.put("eventType", eventType.name());
    json.put("timestamp", timestamp);

    JSONObject dataObj = new JSONObject();
    for (Map.Entry<String, Object> entry : data.entrySet())
    {
      dataObj.put(entry.getKey(), entry.getValue());
    }
    json.put("data", dataObj);

    return json;
  }

  /**
   * Get a data value with type checking
   */
  public <T> T get(String key, Class<T> type)
  {
    Object value = data.get(key);
    if (value == null)
    {
      return null;
    }

    if (type.isInstance(value))
    {
      return type.cast(value);
    }

    return null;
  }

  /**
   * Get a string value
   */
  public String getString(String key)
  {
    return get(key, String.class);
  }

  /**
   * Get an integer value
   */
  public Integer getInt(String key)
  {
    Object value = data.get(key);
    if (value instanceof Integer)
    {
      return (Integer) value;
    }
    else if (value instanceof Number)
    {
      return ((Number) value).intValue();
    }
    return null;
  }

  /**
   * Get a float value
   */
  public Float getFloat(String key)
  {
    Object value = data.get(key);
    if (value instanceof Float)
    {
      return (Float) value;
    }
    else if (value instanceof Double)
    {
      return ((Double) value).floatValue();
    }
    else if (value instanceof Number)
    {
      return ((Number) value).floatValue();
    }
    return null;
  }

  /**
   * Check if a key exists in the data
   */
  public boolean has(String key)
  {
    return data.containsKey(key);
  }

  /**
   * Check if this event matches specific criteria
   */
  public boolean matches(TouchInstrumentation.EventType type, String keyName)
  {
    if (eventType != type)
    {
      return false;
    }

    String eventKeyName = getString("keyName");
    if (eventKeyName == null && keyName == null)
    {
      return true;
    }

    return eventKeyName != null && eventKeyName.equals(keyName);
  }

  /**
   * Get time difference in milliseconds from another event
   */
  public long getTimeDelta(TouchEvent other)
  {
    return Math.abs(this.timestamp - other.timestamp);
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("TouchEvent{");
    sb.append("type=").append(eventType);
    sb.append(", timestamp=").append(timestamp);
    sb.append(", data={");

    boolean first = true;
    for (Map.Entry<String, Object> entry : data.entrySet())
    {
      if (!first) sb.append(", ");
      sb.append(entry.getKey()).append("=").append(entry.getValue());
      first = false;
    }

    sb.append("}}");
    return sb.toString();
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (!(obj instanceof TouchEvent)) return false;

    TouchEvent other = (TouchEvent) obj;
    return eventType == other.eventType &&
           timestamp == other.timestamp &&
           data.equals(other.data);
  }

  @Override
  public int hashCode()
  {
    int result = eventType.hashCode();
    result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
    result = 31 * result + data.hashCode();
    return result;
  }
}
