package juloo.keyboard2;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * AccessibilityNodeProvider that exposes each keyboard key as a virtual view
 * for TalkBack navigation. This allows users to explore individual keys
 * instead of treating the entire keyboard as a single view.
 */
public class KeyboardAccessibilityDelegate extends AccessibilityNodeProvider
{
  private static final String TAG = "KeyboardA11yDelegate";

  // The host view (Keyboard2View)
  private final View _view;

  // Reference to keyboard data for iterating keys
  private KeyboardData _keyboard;

  // Keyboard layout measurements
  private float _keyWidth;
  private float _marginLeft;
  private float _marginTop;
  private float _rowHeight;
  private float _horizontalMargin;
  private float _verticalMargin;

  // Accessibility helper for generating descriptions
  private AccessibilityHelper _accessibilityHelper;

  // Config for accessing accessibility settings
  private Config _config;

  // Cache of virtual view IDs mapped to keys
  private List<KeyInfo> _keyInfoList = new ArrayList<>();

  // Currently focused virtual view ID
  private int _focusedVirtualViewId = INVALID_ID;

  private static final int INVALID_ID = Integer.MIN_VALUE;
  private static final int HOST_VIEW_ID = View.NO_ID;

  /**
   * Information about a key's position and virtual view ID
   */
  private static class KeyInfo
  {
    int virtualViewId;
    int row;
    int column;
    KeyboardData.Key key;
    Rect bounds;

    KeyInfo(int id, int r, int c, KeyboardData.Key k, Rect b)
    {
      virtualViewId = id;
      row = r;
      column = c;
      key = k;
      bounds = b;
    }
  }

  public KeyboardAccessibilityDelegate(View view, AccessibilityHelper helper, Config config)
  {
    _view = view;
    _accessibilityHelper = helper;
    _config = config;
  }

  /**
   * Update keyboard data and measurements when keyboard changes
   */
  public void setKeyboardData(KeyboardData keyboard, float keyWidth, float marginLeft,
                              float marginTop, float rowHeight, float horizontalMargin,
                              float verticalMargin)
  {
    _keyboard = keyboard;
    _keyWidth = keyWidth;
    _marginLeft = marginLeft;
    _marginTop = marginTop;
    _rowHeight = rowHeight;
    _horizontalMargin = horizontalMargin;
    _verticalMargin = verticalMargin;

    // Rebuild virtual view cache
    buildKeyInfoList();
  }

  /**
   * Build list of all keys with their virtual view IDs and bounds
   */
  private void buildKeyInfoList()
  {
    _keyInfoList.clear();

    if (_keyboard == null)
      return;

    int virtualViewId = 0;
    float y = _marginTop;
    int rowCount = _keyboard.rows.size();

    for (int r = 0; r < rowCount; r++) {
        KeyboardData.Row row = _keyboard.rows.get(r);
        y += row.shift * _rowHeight;
        float x = _marginLeft;
        float keyH = row.height * _rowHeight - _verticalMargin;

        for (int c = 0; c < row.keys.size(); c++) {
            KeyboardData.Key key = row.keys.get(c);
            x += key.shift * _keyWidth;
            float keyW = _keyWidth * key.width - _horizontalMargin;

            // Create bounds for this key
            Rect bounds = new Rect(
              (int)x,
              (int)y,
              (int)(x + keyW),
              (int)(y + keyH)
            );

            _keyInfoList.add(new KeyInfo(virtualViewId, r, c, key, bounds));
            virtualViewId++;

            x += key.width * _keyWidth;
        }

        y += row.height * _rowHeight;
    }

    Log.d(TAG, "Built key info list with " + _keyInfoList.size() + " keys");
  }

  /**
   * Find which key is at the given coordinates
   */
  public int getVirtualViewIdAt(float x, float y)
  {
    for (KeyInfo info : _keyInfoList)
    {
      if (info.bounds.contains((int)x, (int)y))
      {
        return info.virtualViewId;
      }
    }
    return INVALID_ID;
  }

  /**
   * Get key for a virtual view ID
   */
  private KeyInfo getKeyInfo(int virtualViewId)
  {
    for (KeyInfo info : _keyInfoList)
    {
      if (info.virtualViewId == virtualViewId)
        return info;
    }
    return null;
  }

  @Override
  public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId)
  {
    Log.d(TAG, "createAccessibilityNodeInfo called for virtualViewId=" + virtualViewId);

    if (Build.VERSION.SDK_INT < 19)
      return null;

    if (virtualViewId == HOST_VIEW_ID)
    {
      Log.d(TAG, "  Creating node for HOST_VIEW, " + _keyInfoList.size() + " children");
      // Info for the host view (the keyboard itself)
      AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(_view);

      // Initialize from the view itself
      _view.onInitializeAccessibilityNodeInfo(node);

      // Override specific properties for virtual view container
      // The host view should not be interactive - only children are
      node.setClickable(false);
      node.setLongClickable(false);
      node.setFocusable(false);

      // Add all keys as children
      for (KeyInfo info : _keyInfoList)
      {
        node.addChild(_view, info.virtualViewId);
      }

      // Set collection info
      int rowCount = _keyboard.rows.size();
      int colCount = 0;
      if (rowCount > 0) {
          colCount = _keyboard.rows.get(0).keys.size();
      }
      if (Build.VERSION.SDK_INT >= 19)
      {
        node.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(rowCount, colCount, false));
      }

      // Provide a pane title to announce keyboard appearance (API 28+)
      if (Build.VERSION.SDK_INT >= 28)
      {
        node.setPaneTitle("Keyboard");
      }

      return node;
    }

    // Info for a specific key
    KeyInfo keyInfo = getKeyInfo(virtualViewId);
    if (keyInfo == null)
    {
      Log.w(TAG, "  KeyInfo is null for virtualViewId=" + virtualViewId);
      return null;
    }

    Log.d(TAG, "  Creating node for key: " + (keyInfo.key.keys[0] != null ? keyInfo.key.keys[0].toString() : "null"));

    AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(_view, virtualViewId);
    node.setPackageName(_view.getContext().getPackageName());
    // Use Button className so TalkBack recognizes this as activatable
    node.setClassName("android.widget.Button");
    node.setSource(_view, virtualViewId);
    node.setParent(_view);

    // Set bounds in parent coordinates
    Rect bounds = new Rect(keyInfo.bounds);
    node.setBoundsInParent(bounds);

    // Set bounds in screen coordinates (required for TalkBack click handling)
    if (Build.VERSION.SDK_INT >= 16)
    {
      int[] viewLocation = new int[2];
      _view.getLocationOnScreen(viewLocation);
      Rect screenBounds = new Rect(
        bounds.left + viewLocation[0],
        bounds.top + viewLocation[1],
        bounds.right + viewLocation[0],
        bounds.bottom + viewLocation[1]
      );
      node.setBoundsInScreen(screenBounds);
    }

    // Set content description
    String description = _accessibilityHelper.getKeyContentDescription(keyInfo.key, null);
    node.setContentDescription(description);

    // Set as focusable and clickable
    node.setFocusable(true);
    node.setClickable(true);
    node.setEnabled(true);
    node.setVisibleToUser(true);
    node.setScreenReaderFocusable(true);

    // Provide role and state descriptions where possible
    AccessibilityNodeInfoCompat.wrap(node).setRoleDescription("Keyboard key");
    if (Build.VERSION.SDK_INT >= 30 && keyInfo.key.keys[0] != null &&
        keyInfo.key.keys[0].getKind() == KeyValue.Kind.Modifier)
    {
      int flags = ((Keyboard2View)_view).getPointers().getKeyFlags(keyInfo.key.keys[0]);
      boolean latched = (flags & Pointers.FLAG_P_LATCHED) != 0;
      boolean locked = (flags & Pointers.FLAG_P_LOCKED) != 0;
      node.setStateDescription(_accessibilityHelper.getModifierStateDescription(latched, locked));
    }

    // Explicitly mark as important for accessibility
    if (Build.VERSION.SDK_INT >= 24)
    {
      node.setImportantForAccessibility(true);
    }

    // Mark as supporting direct touch for IME keyboards
    if (Build.VERSION.SDK_INT >= 29)
    {
      node.setTouchDelegateInfo(null);  // Ensure no touch delegate interferes
    }

    // Set collection item info
    node.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(keyInfo.row, 1, keyInfo.column, 1, false, false));

    // Provide traversal hints for predictable navigation order
    if (Build.VERSION.SDK_INT >= 22)
    {
      int prevId = virtualViewId - 1;
      int nextId = virtualViewId + 1;
      if (prevId >= 0)
      {
        node.setTraversalAfter(_view, prevId);
      }
      if (nextId < _keyInfoList.size())
      {
        node.setTraversalBefore(_view, nextId);
      }
    }

    // Add standard actions
    if (Build.VERSION.SDK_INT >= 21)
    {
      // Use AccessibilityAction objects for API 21+ so TalkBack recognizes them
      node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
      node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);

      // Add ACTION_CLICK - TalkBack will automatically announce "double-tap to activate"
      // or "lift to type" based on its own settings, so we don't add confusing custom labels
      node.addAction(AccessibilityNodeInfo.ACTION_CLICK);

      // Add custom actions for swipe gestures (use IDs 256+ to avoid conflicts)
      int actionId = 256;
      for (int i = 1; i <= 8; i++) {
          if (keyInfo.key.keys[i] != null && !keyInfo.key.keys[i].equals(keyInfo.key.keys[0])) {
              String label = "Swipe " + AccessibilityHelper.getCornerDirectionName(i) + " for " + _accessibilityHelper.getKeyDescription(keyInfo.key.keys[i]);
              node.addAction(new AccessibilityNodeInfo.AccessibilityAction(actionId, label));
              actionId++;
          }
      }
    }
    else if (Build.VERSION.SDK_INT >= 16)
    {
      // Use integer constants for older APIs
      node.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
      node.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
      node.addAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // Set focused state
    if (virtualViewId == _focusedVirtualViewId)
    {
      node.setAccessibilityFocused(true);
      Log.d(TAG, "  Node has accessibility focus");
    }
    else
    {
      node.setAccessibilityFocused(false);
    }

    Log.d(TAG, "  Node setup complete: clickable=" + node.isClickable() +
              ", focusable=" + node.isFocusable() +
              ", enabled=" + node.isEnabled() +
              ", visibleToUser=" + node.isVisibleToUser());

    return node;
  }

  @Override
  public boolean performAction(int virtualViewId, int action, Bundle arguments)
  {
    String actionName = getActionName(action);
    Log.d(TAG, "performAction called: virtualViewId=" + virtualViewId + ", action=" + action + " (" + actionName + ")");

    if (Build.VERSION.SDK_INT < 16)
      return false;

    if (virtualViewId == HOST_VIEW_ID)
    {
      Log.d(TAG, "Action on host view, delegating to view");
      boolean result = _view.performAccessibilityAction(action, arguments);
      Log.d(TAG, "Host view action result: " + result);
      return result;
    }

    KeyInfo keyInfo = getKeyInfo(virtualViewId);
    if (keyInfo == null)
    {
      Log.w(TAG, "KeyInfo is null for virtualViewId=" + virtualViewId);
      return false;
    }

    Log.d(TAG, "KeyInfo found for key: " + (keyInfo.key.keys[0] != null ? keyInfo.key.keys[0].toString() : "null"));

    // Handle custom swipe actions (IDs 256-263 map to swipe directions 1-8)
    if (action >= 256 && action <= 263) {
        int swipeDirection = action - 255;  // 256->1, 257->2, etc.
        if (swipeDirection >= 1 && swipeDirection <= 8 && _view instanceof Keyboard2View) {
            KeyValue targetKv = keyInfo.key.keys[swipeDirection];
            if (targetKv != null) {
                Log.d(TAG, "  Custom swipe action " + action + " (direction " + swipeDirection + ")");
                KeyboardData.Key tempKey = new KeyboardData.Key(new KeyValue[]{targetKv}, null, 0, 1f, 0f, "");
                ((Keyboard2View) _view).performAccessibilityKeyPress(tempKey);
            }
        }
        return true;
    }

    switch (action)
    {
      case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS:
        _focusedVirtualViewId = virtualViewId;

        // Announce the key when focused
        if (_accessibilityHelper != null)
        {
          _accessibilityHelper.announceKeyFocus(_view, keyInfo.key);
        }

        // Trigger redraw to show accessibility focus rectangle
        _view.invalidate();

        // Send accessibility event
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        return true;

      case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
        if (_focusedVirtualViewId == virtualViewId)
        {
          _focusedVirtualViewId = INVALID_ID;
          // Trigger redraw to remove accessibility focus rectangle
          _view.invalidate();
        }
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        return true;

      case AccessibilityNodeInfo.ACTION_CLICK:
        // Perform actual key press when user double-taps in TalkBack
        // TalkBack has already determined the correct virtual view based on accessibility focus
        Log.d(TAG, "ACTION_CLICK on virtual view " + virtualViewId);
        Log.d(TAG, "  Key: " + (keyInfo.key.keys[0] != null ? keyInfo.key.keys[0].toString() : "null"));
        Log.d(TAG, "  View is Keyboard2View: " + (_view instanceof Keyboard2View));

        // Cast view to Keyboard2View to access performAccessibilityKeyPress
        if (_view instanceof Keyboard2View)
        {
          Log.d(TAG, "  Calling performAccessibilityKeyPress...");
          ((Keyboard2View)_view).performAccessibilityKeyPress(keyInfo.key);
          Log.d(TAG, "  performAccessibilityKeyPress completed");
        }
        else
        {
          Log.e(TAG, "  View is not Keyboard2View! Type: " + _view.getClass().getName());
        }
        return true;

      default:
        return false;
    }
  }

  /**
   * Helper method to get human-readable action name for logging
   */
  private String getActionName(int action)
  {
    switch (action)
    {
      case AccessibilityNodeInfo.ACTION_CLICK: return "CLICK";
      case AccessibilityNodeInfo.ACTION_LONG_CLICK: return "LONG_CLICK";
      case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: return "ACCESSIBILITY_FOCUS";
      case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: return "CLEAR_ACCESSIBILITY_FOCUS";
      case AccessibilityNodeInfo.ACTION_FOCUS: return "FOCUS";
      case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS: return "CLEAR_FOCUS";
      default:
        if (action >= 1 && action <= 8) return "CUSTOM_SWIPE_" + action;
        return "UNKNOWN";
    }
  }

  /**
   * Send accessibility event for a virtual view
   */
  private void sendEventForVirtualView(int virtualViewId, int eventType)
  {
    if (Build.VERSION.SDK_INT < 14)
      return;

    AccessibilityEvent event = AccessibilityEvent.obtain(eventType);

    KeyInfo keyInfo = getKeyInfo(virtualViewId);
    if (keyInfo != null)
    {
      String description = _accessibilityHelper.getKeyContentDescription(keyInfo.key, null);
      event.getText().add(description);
    }

    event.setClassName("android.view.View");
    event.setPackageName(_view.getContext().getPackageName());
    event.setSource(_view, virtualViewId);
    event.setEnabled(true);

    _view.getParent().requestSendAccessibilityEvent(_view, event);
  }

  /**
   * Check if a virtual view currently has accessibility focus
   */
  public boolean isVirtualViewFocused(int virtualViewId)
  {
    return _focusedVirtualViewId == virtualViewId;
  }

  /**
   * Notify that accessibility focus changed to a specific key
   */
  public void notifyAccessibilityFocusChanged(int virtualViewId)
  {
    if (_focusedVirtualViewId != virtualViewId)
    {
      // Clear old focus
      if (_focusedVirtualViewId != INVALID_ID)
      {
        sendEventForVirtualView(_focusedVirtualViewId,
          AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
      }

      // Set new focus
      _focusedVirtualViewId = virtualViewId;
      if (virtualViewId != INVALID_ID)
      {
        sendEventForVirtualView(virtualViewId,
          AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);

        // Announce the key
        KeyInfo keyInfo = getKeyInfo(virtualViewId);
        if (keyInfo != null && _accessibilityHelper != null)
        {
          _accessibilityHelper.announceKeyFocus(_view, keyInfo.key);
        }
      }
    }
  }

  /**
   * Invalidate all virtual views (call when keyboard changes)
   */
  public void invalidateRoot()
  {
    if (Build.VERSION.SDK_INT >= 19)
    {
      _view.getParent().notifySubtreeAccessibilityStateChanged(
        _view, _view, AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
    }
  }
}
