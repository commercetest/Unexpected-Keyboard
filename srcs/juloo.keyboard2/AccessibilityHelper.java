package juloo.keyboard2;

import android.content.Context;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * AccessibilityHelper provides centralized accessibility support for the keyboard.
 *
 * Features:
 * - Announces key presses, swipes, and gestures to TalkBack
 * - Provides descriptive announcements for all touch interactions
 * - Manages accessibility state and preferences
 * - Generates appropriate AccessibilityEvents
 *
 * This enables blind and low-vision users to effectively use the keyboard
 * with screen readers like TalkBack.
 */
public class AccessibilityHelper
{
  private static final String TAG = "AccessibilityHelper";

  private Context _context;
  private AccessibilityManager _accessibilityManager;
  private boolean _enabled = true;
  private boolean _verboseMode = false;

  public AccessibilityHelper(Context context)
  {
    _context = context;
    _accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
  }

  /**
   * Check if accessibility services (like TalkBack) are currently enabled
   */
  public boolean isAccessibilityEnabled()
  {
    return _accessibilityManager != null &&
           _accessibilityManager.isEnabled() &&
           _accessibilityManager.isTouchExplorationEnabled();
  }

  /**
   * Set whether accessibility announcements are enabled
   */
  public void setEnabled(boolean enabled)
  {
    _enabled = enabled;
  }

  /**
   * Set verbose mode for more detailed announcements
   */
  public void setVerboseMode(boolean verbose)
  {
    _verboseMode = verbose;
  }

  /**
   * Announce a key press to accessibility services
   */
  public void announceKeyPress(View view, KeyValue key, Pointers.Modifiers modifiers)
  {
    if (!_enabled || !isAccessibilityEnabled() || key == null)
    {
      return;
    }

    String announcement = buildKeyAnnouncement(key, modifiers);
    announce(view, announcement);
  }

  /**
   * Announce a key activation (double-tap selection) to accessibility services
   */
  public void announceKeyActivation(View view, KeyValue key, Pointers.Modifiers modifiers)
  {
    if (!_enabled || !isAccessibilityEnabled() || key == null)
    {
      return;
    }

    String announcement = buildKeyActivationAnnouncement(key, modifiers);
    announce(view, announcement);
  }

  /**
   * Announce a swipe gesture to accessibility services
   */
  public void announceSwipe(View view, KeyboardData.Key baseKey, KeyValue targetKey, int direction)
  {
    if (!_enabled || !isAccessibilityEnabled())
    {
      return;
    }

    String directionName = getDirectionName(direction);
    String baseKeyName = getKeyDescription(baseKey.keys[0]);
    String targetKeyName = getKeyDescription(targetKey);

    String announcement;
    if (_verboseMode)
    {
      announcement = String.format("Swiped %s on %s, selected %s",
                                   directionName, baseKeyName, targetKeyName);
    }
    else
    {
      announcement = targetKeyName;
    }

    announce(view, announcement);
  }

  /**
   * Announce a gesture (circle, roundtrip, etc.) to accessibility services
   */
  public void announceGesture(View view, Gesture.Name gestureName, KeyValue resultKey)
  {
    if (!_enabled || !isAccessibilityEnabled())
    {
      return;
    }

    String announcement;
    if (_verboseMode)
    {
      announcement = String.format("%s gesture, %s",
                                   getGestureName(gestureName),
                                   getKeyDescription(resultKey));
    }
    else
    {
      announcement = getKeyDescription(resultKey);
    }

    announce(view, announcement);
  }

  /**
   * Announce a long press action to accessibility services
   */
  public void announceLongPress(View view, KeyValue key, String action)
  {
    if (!_enabled || !isAccessibilityEnabled())
    {
      return;
    }

    String keyName = getKeyDescription(key);
    String announcement;

    switch (action)
    {
      case "lock":
        announcement = keyName + " locked";
        break;
      case "alternate":
        announcement = "Long press " + keyName;
        break;
      case "repeat":
        announcement = keyName + " repeating";
        break;
      default:
        announcement = keyName;
    }

    announce(view, announcement);
  }

  /**
   * Announce a modifier state change to accessibility services
   */
  public void announceModifierChange(View view, KeyValue modifier, boolean latched, boolean locked)
  {
    if (!_enabled || !isAccessibilityEnabled())
    {
      return;
    }

    String modName = getKeyDescription(modifier);
    String state = getModifierStateDescription(latched, locked);
    String announcement = modName + " " + state;
    announce(view, announcement);
  }

  /**
   * Build a state description string for modifier keys.
   */
  public String getModifierStateDescription(boolean latched, boolean locked)
  {
    if (locked)
    {
      return _context.getString(R.string.a11y_state_locked);
    }
    if (latched)
    {
      return _context.getString(R.string.a11y_state_on);
    }
    return _context.getString(R.string.a11y_state_off);
  }

  /**
   * Announce that touch exploration has started on a key
   */
  public void announceKeyFocus(View view, KeyboardData.Key key)
  {
    if (!_enabled || !isAccessibilityEnabled() || key == null)
    {
      return;
    }

    String keyName = getKeyDescription(key.keys[0]);

    if (_verboseMode)
    {
      // Build announcement with specific swipe options
      StringBuilder announcement = new StringBuilder(keyName);

      String swipeOptions = buildSwipeOptionsAnnouncement(key);
      if (!swipeOptions.isEmpty())
      {
        announcement.append(". ");
        announcement.append(swipeOptions);
      }

      announceWithInterrupt(view, announcement.toString());
    }
    else
    {
      announceWithInterrupt(view, keyName);
    }
  }

  /**
   * Core announcement method that sends to accessibility services
   */
  private void announce(View view, String announcement)
  {
    if (view == null || announcement == null || announcement.isEmpty())
    {
      return;
    }

    Log.d(TAG, "Announcing: " + announcement);

    // Prefer announceForAccessibility on modern devices; fall back to an event for older APIs.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
    {
      view.announceForAccessibility(announcement);
    }
    else
    {
      AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
      event.getText().add(announcement);
      event.setClassName(view.getClass().getName());
      event.setPackageName(view.getContext().getPackageName());
      view.sendAccessibilityEventUnchecked(event);
    }
  }

  /**
   * Announce with interruption of previous announcements
   * Used for hover/focus events where we want immediate feedback
   */
  private void announceWithInterrupt(View view, String announcement)
  {
    if (view == null || announcement == null || announcement.isEmpty())
    {
      return;
    }

    Log.d(TAG, "Announcing (interrupt): " + announcement);

    // Use TYPE_VIEW_HOVER_ENTER which has higher priority and interrupts previous speech
    AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
    event.getText().add(announcement);
    event.setClassName(view.getClass().getName());
    event.setPackageName(view.getContext().getPackageName());
    event.setEnabled(true);
    view.sendAccessibilityEventUnchecked(event);
  }

  /**
   * Build announcement listing available swipe options
   */
  public String buildSwipeOptionsAnnouncement(KeyboardData.Key key)
  {
    // Direction names corresponding to key.keys indices 1-8
    // Based on the layout diagram:
    //  1 7 2
    //  5 0 6
    //  3 8 4
    // Where 1=nw, 2=ne, 3=sw, 4=se, 5=w, 6=e, 7=n, 8=s
    String[] directionNames = {
      "up and left",    // keys[1] = nw
      "up and right",   // keys[2] = ne
      "down and left",  // keys[3] = sw
      "down and right", // keys[4] = se
      "left",           // keys[5] = w
      "right",          // keys[6] = e
      "up",             // keys[7] = n
      "down"            // keys[8] = s
    };

    StringBuilder options = new StringBuilder();
    int optionCount = 0;

    // Check each swipe direction
    for (int i = 1; i <= 8; i++)
    {
      if (key.keys[i] != null && !key.keys[i].equals(key.keys[0]))
      {
        if (optionCount > 0)
        {
          options.append(", ");
        }

        String direction = getCornerDirectionName(i);
        String keyDesc = getKeyDescription(key.keys[i]);

        options.append("swipe ");
        options.append(direction);
        options.append(getSwipeActionDescription(key.keys[i]));
        options.append(keyDesc);

        optionCount++;
      }
    }

    return options.toString();
  }

  public String getSwipeActionDescription(KeyValue key) {
      if (key == null) {
          return "";
      }

      switch (key.getKind()) {
          case Char:
          case String:
              return " to type ";
          case Keyevent:
              switch (key.getKeyevent()) {
                  case android.view.KeyEvent.KEYCODE_DEL:
                  case android.view.KeyEvent.KEYCODE_FORWARD_DEL:
                      return " to delete ";
                  default:
                      return " for ";
              }
          case Editing:
              return " to ";
          default:
              return " for ";
      }
  }

  /**
   * Build a descriptive announcement for a key press
   */
  public String buildKeyAnnouncement(KeyValue key, Pointers.Modifiers modifiers)
  {
    StringBuilder announcement = new StringBuilder();

    // Announce active modifiers
    if (modifiers != null && modifiers.size() > 0)
    {
      for (int i = 0; i < modifiers.size(); i++)
      {
        KeyValue mod = modifiers.get(i);
        if (mod.getKind() == KeyValue.Kind.Modifier)
        {
          announcement.append(getModifierName(mod.getModifier()));
          announcement.append(" ");
        }
      }
    }

    // Announce the key itself
    announcement.append(getKeyDescription(key));
    if (key.getKind() == KeyValue.Kind.Char && key.getString().length() > 1) {
        announcement.append(", long-press for more options");
    }

    return announcement.toString();
  }

  /**
   * Build announcement for key activation (double-tap)
   * Adds "selected" suffix for most keys, special handling for delete keys
   */
  public String buildKeyActivationAnnouncement(KeyValue key, Pointers.Modifiers modifiers)
  {
    StringBuilder announcement = new StringBuilder();

    // Announce active modifiers
    if (modifiers != null && modifiers.size() > 0)
    {
      for (int i = 0; i < modifiers.size(); i++)
      {
        KeyValue mod = modifiers.get(i);
        if (mod.getKind() == KeyValue.Kind.Modifier)
        {
          announcement.append(getModifierName(mod.getModifier()));
          announcement.append(" ");
        }
      }
    }

    // Check if this is a delete/backspace key
    boolean isDelete = false;
    if (key.getKind() == KeyValue.Kind.Keyevent)
    {
      int keycode = key.getKeyevent();
      if (keycode == android.view.KeyEvent.KEYCODE_DEL ||
          keycode == android.view.KeyEvent.KEYCODE_FORWARD_DEL)
      {
        isDelete = true;
      }
    }
    else if (key.getKind() == KeyValue.Kind.Editing)
    {
      isDelete = true; // DELETE_WORD, FORWARD_DELETE_WORD
    }

    // Announce the key with appropriate suffix
    String keyDesc = getKeyDescription(key);
    announcement.append(keyDesc);

    if (isDelete)
    {
      announcement.append(", deleted");
    }
    else
    {
      announcement.append(", selected");
    }

    return announcement.toString();
  }

  /**
   * Get a human-readable description of a key
   */
  public String getKeyDescription(KeyValue key)
  {
    if (key == null)
    {
      return "unknown";
    }

    if ("\uE00D".equals(key.getString())) {
        return "space bar";
    }

    switch (key.getKind())
    {
      case Char:
        String charStr = key.getString();

        // Special character descriptions
        switch (charStr)
        {
          case " ": return "space bar";
          case "\n": return "enter";
          case "\t": return "tab";
          case "!": return "exclamation";
          case "\"": return "quote";
          case "#": return "hash";
          case "$": return "dollar";
          case "%": return "percent";
          case "&": return "ampersand";
          case "'": return "apostrophe";
          case "(": return "left parenthesis";
          case ")": return "right parenthesis";
          case "*": return "asterisk";
          case "+": return "plus";
          case ",": return "comma";
          case "-": return "minus";
          case ".": return "period";
          case "/": return "slash";
          case ":": return "colon";
          case ";": return "semicolon";
          case "<": return "less than";
          case "=": return "equals";
          case ">": return "greater than";
          case "?": return "question mark";
          case "@": return "at";
          case "[": return "left bracket";
          case "\\": return "backslash";
          case "]": return "right bracket";
          case "^": return "caret";
          case "_": return "underscore";
          case "`": return "backtick";
          case "{": return "left brace";
          case "|": return "pipe";
          case "}": return "right brace";
          case "~": return "tilde";
          default:
            // For single characters, just return the character
            if (charStr.length() == 1)
            {
              char c = charStr.charAt(0);
              if (Character.isUpperCase(c))
              {
                return "capital " + charStr.toLowerCase();
              }
              return charStr;
            }
            return charStr;
        }

      case Event:
        KeyValue.Event event = key.getEvent();
        switch (event)
        {
          case CHANGE_METHOD_PICKER: return "switch keyboard";
          case CHANGE_METHOD_AUTO: return "switch keyboard auto";
          case SWITCH_TEXT: return "switch to text layout";
          case SWITCH_NUMERIC: return "switch to numeric layout";
          case SWITCH_EMOJI: return "emoji";
          case SWITCH_BACK_EMOJI: return "back from emoji";
          case SWITCH_CLIPBOARD: return "clipboard";
          case SWITCH_BACK_CLIPBOARD: return "back from clipboard";
          case SWITCH_FORWARD: return "switch forward";
          case SWITCH_BACKWARD: return "switch backward";
          case SWITCH_GREEKMATH: return "greek math symbols";
          case CAPS_LOCK: return "caps lock";
          case SWITCH_VOICE_TYPING: return "voice typing";
          case SWITCH_VOICE_TYPING_CHOOSER: return "voice typing chooser";
          case ACTION: return "action";
          case CONFIG: return "settings";
          default: return event.toString();
        }

      case Keyevent:
        int keycode = key.getKeyevent();
        switch (keycode)
        {
          case android.view.KeyEvent.KEYCODE_DEL: return "backspace";
          case android.view.KeyEvent.KEYCODE_FORWARD_DEL: return "delete";
          case android.view.KeyEvent.KEYCODE_ENTER: return "enter";
          case android.view.KeyEvent.KEYCODE_DPAD_LEFT: return "left arrow";
          case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: return "right arrow";
          case android.view.KeyEvent.KEYCODE_DPAD_UP: return "up arrow";
          case android.view.KeyEvent.KEYCODE_DPAD_DOWN: return "down arrow";
          case android.view.KeyEvent.KEYCODE_MOVE_HOME: return "home";
          case android.view.KeyEvent.KEYCODE_MOVE_END: return "end";
          case android.view.KeyEvent.KEYCODE_PAGE_UP: return "page up";
          case android.view.KeyEvent.KEYCODE_PAGE_DOWN: return "page down";
          case android.view.KeyEvent.KEYCODE_ESCAPE: return "escape";
          case android.view.KeyEvent.KEYCODE_TAB: return "tab";
          default: return "key " + keycode;
        }

      case Modifier:
        return getModifierName(key.getModifier());

      case Editing:
        KeyValue.Editing editing = key.getEditing();
        switch (editing)
        {
          case DELETE_WORD: return "delete word";
          case FORWARD_DELETE_WORD: return "forward delete word";
          default: return editing.toString();
        }

      case Placeholder:
        return "placeholder";

      case Compose_pending:
        return "composing";

      case String:
        return key.getString();

      case Slider:
        KeyValue.Slider slider = key.getSlider();
        switch (slider)
        {
          case Cursor_left: return "move cursor left";
          case Cursor_right: return "move cursor right";
          case Cursor_up: return "move cursor up";
          case Cursor_down: return "move cursor down";
          case Selection_cursor_left: return "select left";
          case Selection_cursor_right: return "select right";
          default: return "slider " + slider.toString();
        }

      case Macro:
        return "macro";

      case Hangul_initial:
      case Hangul_medial:
        return key.toString();

      default:
        return key.toString();
    }
  }

  /**
   * Get human-readable modifier name
   */
  public String getModifierName(KeyValue.Modifier mod)
  {
    switch (mod)
    {
      case SHIFT: return "shift";
      case CTRL: return "control";
      case ALT: return "alt";
      case META: return "meta";
      case GESTURE: return "gesture";
      default: return mod.toString().toLowerCase().replace('_', ' ');
    }
  }

  /**
   * Get human-readable direction name
   * Used for swipe gestures which pass direction as 0-15
   */
  public String getDirectionName(int direction)
  {
    // Direction is 0-15 (16 compass points), convert to 8 main directions
    // The formula maps 16 directions to 8 by grouping pairs
    String[] directions = {
      "up",             // n
      "up and right",   // ne
      "right",          // e
      "down and right", // se
      "down",           // s
      "down and left",  // sw
      "left",           // w
      "up and left"     // nw
    };

    int index = (direction + 1) / 2 % 8;
    return directions[index];
  }

  /**
   * Get human-readable gesture name
   */
  public String getGestureName(Gesture.Name gesture)
  {
    switch (gesture)
    {
      case Circle: return "circle";
      case Anticircle: return "reverse circle";
      case Roundtrip: return "roundtrip";
      case Swipe: return "swipe";
      default: return gesture.toString();
    }
  }

  /**
   * Direction names for key.keys indices 1-8.
   *  1 7 2
   *  5 0 6
   *  3 8 4
   * Where 1=nw, 2=ne, 3=sw, 4=se, 5=w, 6=e, 7=n, 8=s.
   */
  static String getCornerDirectionName(int index)
  {
    switch (index)
    {
      case 1: return "up and left";
      case 2: return "up and right";
      case 3: return "down and left";
      case 4: return "down and right";
      case 5: return "left";
      case 6: return "right";
      case 7: return "up";
      case 8: return "down";
      default: return "";
    }
  }

  /**
   * Generate content description for a key (for touch exploration)
   */
  public String getKeyContentDescription(KeyboardData.Key key, Pointers.Modifiers modifiers)
  {
    if (key == null || key.keys[0] == null)
    {
      return "";
    }

    String baseDescription = getKeyDescription(key.keys[0]);

    // Add modifier context
    if (modifiers != null && modifiers.size() > 0)
    {
      StringBuilder desc = new StringBuilder();
      for (int i = 0; i < modifiers.size(); i++)
      {
        KeyValue mod = modifiers.get(i);
        if (mod.getKind() == KeyValue.Kind.Modifier)
        {
          desc.append(getModifierName(mod.getModifier()));
          desc.append(" ");
        }
      }
      desc.append(baseDescription);
      return desc.toString();
    }

    return baseDescription;
  }
}
