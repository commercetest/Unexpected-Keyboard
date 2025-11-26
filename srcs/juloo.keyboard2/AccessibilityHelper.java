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
      return getStringSafe(R.string.a11y_state_locked, "locked");
    }
    if (latched)
    {
      return getStringSafe(R.string.a11y_state_on, "on");
    }
    return getStringSafe(R.string.a11y_state_off, "off");
  }

  /**
   * Safely resolve a string; falls back to the provided default when the Context is null (e.g. in JVM tests without Android resources).
   */
  private String getStringSafe(int resId, String fallback)
  {
    try
    {
      if (_context != null)
      {
        String value = _context.getString(resId);
        if (value != null && !value.isEmpty())
          return value;
      }
    }
    catch (Exception ignored) { }
    return fallback;
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
          case " ": return getStringSafe(R.string.a11y_key_space, "space bar");
          case "\n": return getStringSafe(R.string.a11y_key_enter, "enter");
          case "\t": return getStringSafe(R.string.a11y_key_tab, "tab");
          case "!": return getStringSafe(R.string.a11y_key_exclamation, "exclamation");
          case "\"": return getStringSafe(R.string.a11y_key_quote, "quote");
          case "#": return getStringSafe(R.string.a11y_key_hash, "hash");
          case "$": return getStringSafe(R.string.a11y_key_dollar, "dollar");
          case "%": return getStringSafe(R.string.a11y_key_percent, "percent");
          case "&": return getStringSafe(R.string.a11y_key_ampersand, "ampersand");
          case "'": return getStringSafe(R.string.a11y_key_apostrophe, "apostrophe");
          case "(": return getStringSafe(R.string.a11y_key_left_paren, "left parenthesis");
          case ")": return getStringSafe(R.string.a11y_key_right_paren, "right parenthesis");
          case "*": return getStringSafe(R.string.a11y_key_asterisk, "asterisk");
          case "+": return getStringSafe(R.string.a11y_key_plus, "plus");
          case ",": return getStringSafe(R.string.a11y_key_comma, "comma");
          case "-": return getStringSafe(R.string.a11y_key_minus, "minus");
          case ".": return getStringSafe(R.string.a11y_key_period, "period");
          case "/": return getStringSafe(R.string.a11y_key_slash, "slash");
          case ":": return getStringSafe(R.string.a11y_key_colon, "colon");
          case ";": return getStringSafe(R.string.a11y_key_semicolon, "semicolon");
          case "<": return getStringSafe(R.string.a11y_key_less_than, "less than");
          case "=": return getStringSafe(R.string.a11y_key_equals, "equals");
          case ">": return getStringSafe(R.string.a11y_key_greater_than, "greater than");
          case "?": return getStringSafe(R.string.a11y_key_question_mark, "question mark");
          case "@": return getStringSafe(R.string.a11y_key_at, "at");
          case "[": return getStringSafe(R.string.a11y_key_left_bracket, "left bracket");
          case "\\": return getStringSafe(R.string.a11y_key_backslash, "backslash");
          case "]": return getStringSafe(R.string.a11y_key_right_bracket, "right bracket");
          case "^": return getStringSafe(R.string.a11y_key_caret, "caret");
          case "_": return getStringSafe(R.string.a11y_key_underscore, "underscore");
          case "`": return getStringSafe(R.string.a11y_key_backtick, "backtick");
          case "{": return getStringSafe(R.string.a11y_key_left_brace, "left brace");
          case "|": return getStringSafe(R.string.a11y_key_pipe, "pipe");
          case "}": return getStringSafe(R.string.a11y_key_right_brace, "right brace");
          case "~": return getStringSafe(R.string.a11y_key_tilde, "tilde");
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
          case CHANGE_METHOD_PICKER: return getStringSafe(R.string.a11y_event_change_method_picker, "switch keyboard");
          case CHANGE_METHOD_AUTO: return getStringSafe(R.string.a11y_event_change_method_auto, "switch keyboard auto");
          case SWITCH_TEXT: return getStringSafe(R.string.a11y_event_switch_text, "switch to text layout");
          case SWITCH_NUMERIC: return getStringSafe(R.string.a11y_event_switch_numeric, "switch to numeric layout");
          case SWITCH_EMOJI: return getStringSafe(R.string.a11y_event_switch_emoji, "emoji");
          case SWITCH_BACK_EMOJI: return getStringSafe(R.string.a11y_event_switch_back_emoji, "back from emoji");
          case SWITCH_CLIPBOARD: return getStringSafe(R.string.a11y_event_switch_clipboard, "clipboard");
          case SWITCH_BACK_CLIPBOARD: return getStringSafe(R.string.a11y_event_switch_back_clipboard, "back from clipboard");
          case SWITCH_FORWARD: return getStringSafe(R.string.a11y_event_switch_forward, "switch forward");
          case SWITCH_BACKWARD: return getStringSafe(R.string.a11y_event_switch_backward, "switch backward");
          case SWITCH_GREEKMATH: return getStringSafe(R.string.a11y_event_switch_greekmath, "greek math symbols");
          case CAPS_LOCK: return getStringSafe(R.string.a11y_event_caps_lock, "caps lock");
          case SWITCH_VOICE_TYPING: return getStringSafe(R.string.a11y_event_switch_voice_typing, "voice typing");
          case SWITCH_VOICE_TYPING_CHOOSER: return getStringSafe(R.string.a11y_event_switch_voice_typing_chooser, "voice typing chooser");
          case ACTION: return getStringSafe(R.string.a11y_event_action, "action");
          case CONFIG: return getStringSafe(R.string.a11y_event_config, "settings");
          default: return event.toString();
        }

      case Keyevent:
        int keycode = key.getKeyevent();
        switch (keycode)
        {
          case android.view.KeyEvent.KEYCODE_DEL: return getStringSafe(R.string.a11y_key_backspace, "backspace");
          case android.view.KeyEvent.KEYCODE_FORWARD_DEL: return getStringSafe(R.string.a11y_key_delete, "delete");
          case android.view.KeyEvent.KEYCODE_ENTER: return getStringSafe(R.string.a11y_key_enter, "enter");
          case android.view.KeyEvent.KEYCODE_DPAD_LEFT: return getStringSafe(R.string.a11y_key_left_arrow, "left arrow");
          case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: return getStringSafe(R.string.a11y_key_right_arrow, "right arrow");
          case android.view.KeyEvent.KEYCODE_DPAD_UP: return getStringSafe(R.string.a11y_key_up_arrow, "up arrow");
          case android.view.KeyEvent.KEYCODE_DPAD_DOWN: return getStringSafe(R.string.a11y_key_down_arrow, "down arrow");
          case android.view.KeyEvent.KEYCODE_MOVE_HOME: return getStringSafe(R.string.a11y_key_home, "home");
          case android.view.KeyEvent.KEYCODE_MOVE_END: return getStringSafe(R.string.a11y_key_end, "end");
          case android.view.KeyEvent.KEYCODE_PAGE_UP: return getStringSafe(R.string.a11y_key_page_up, "page up");
          case android.view.KeyEvent.KEYCODE_PAGE_DOWN: return getStringSafe(R.string.a11y_key_page_down, "page down");
          case android.view.KeyEvent.KEYCODE_ESCAPE: return getStringSafe(R.string.a11y_key_escape, "escape");
          case android.view.KeyEvent.KEYCODE_TAB: return getStringSafe(R.string.a11y_key_tab, "tab");
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
          case Cursor_left: return getStringSafe(R.string.a11y_slider_cursor_left, "move cursor left");
          case Cursor_right: return getStringSafe(R.string.a11y_slider_cursor_right, "move cursor right");
          case Cursor_up: return getStringSafe(R.string.a11y_slider_cursor_up, "move cursor up");
          case Cursor_down: return getStringSafe(R.string.a11y_slider_cursor_down, "move cursor down");
          case Selection_cursor_left: return getStringSafe(R.string.a11y_slider_selection_left, "select left");
          case Selection_cursor_right: return getStringSafe(R.string.a11y_slider_selection_right, "select right");
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
