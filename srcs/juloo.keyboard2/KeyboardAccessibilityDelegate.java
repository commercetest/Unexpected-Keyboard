package juloo.keyboard2;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
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
    KeyboardData.Key key;
    Rect bounds;

    KeyInfo(int id, KeyboardData.Key k, Rect b)
    {
      virtualViewId = id;
      key = k;
      bounds = b;
    }
  }

  public KeyboardAccessibilityDelegate(View view, AccessibilityHelper helper)
  {
    _view = view;
    _accessibilityHelper = helper;
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

    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += row.shift * _rowHeight;
      float x = _marginLeft;
      float keyH = row.height * _rowHeight - _verticalMargin;

      for (KeyboardData.Key key : row.keys)
      {
        x += key.shift * _keyWidth;
        float keyW = _keyWidth * key.width - _horizontalMargin;

        // Create bounds for this key
        Rect bounds = new Rect(
          (int)x,
          (int)y,
          (int)(x + keyW),
          (int)(y + keyH)
        );

        _keyInfoList.add(new KeyInfo(virtualViewId, key, bounds));
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
    if (Build.VERSION.SDK_INT < 16)
      return null;

    if (virtualViewId == HOST_VIEW_ID)
    {
      // Info for the host view (the keyboard itself)
      AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(_view);
      _view.onInitializeAccessibilityNodeInfo(node);

      // Add all keys as children
      for (KeyInfo info : _keyInfoList)
      {
        node.addChild(_view, info.virtualViewId);
      }

      return node;
    }

    // Info for a specific key
    KeyInfo keyInfo = getKeyInfo(virtualViewId);
    if (keyInfo == null)
      return null;

    AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(_view, virtualViewId);
    node.setPackageName(_view.getContext().getPackageName());
    node.setClassName("android.view.View");
    node.setSource(_view, virtualViewId);

    // Set bounds in parent coordinates
    Rect bounds = new Rect(keyInfo.bounds);
    node.setBoundsInParent(bounds);

    // Set content description
    String description = _accessibilityHelper.getKeyContentDescription(keyInfo.key, null);
    node.setContentDescription(description);

    // Set as focusable and clickable
    node.setFocusable(true);
    node.setClickable(true);
    node.setEnabled(true);
    node.setVisibleToUser(true);

    if (Build.VERSION.SDK_INT >= 16)
    {
      node.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
      node.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
      node.addAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // Set focused state
    if (virtualViewId == _focusedVirtualViewId)
    {
      node.setAccessibilityFocused(true);
    }
    else
    {
      node.setAccessibilityFocused(false);
    }

    return node;
  }

  @Override
  public boolean performAction(int virtualViewId, int action, Bundle arguments)
  {
    if (Build.VERSION.SDK_INT < 16)
      return false;

    if (virtualViewId == HOST_VIEW_ID)
    {
      return _view.performAccessibilityAction(action, arguments);
    }

    KeyInfo keyInfo = getKeyInfo(virtualViewId);
    if (keyInfo == null)
      return false;

    switch (action)
    {
      case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS:
        _focusedVirtualViewId = virtualViewId;

        // Announce the key when focused
        if (_accessibilityHelper != null)
        {
          _accessibilityHelper.announceKeyFocus(_view, keyInfo.key);
        }

        // Send accessibility event
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        return true;

      case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
        if (_focusedVirtualViewId == virtualViewId)
        {
          _focusedVirtualViewId = INVALID_ID;
        }
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        return true;

      case AccessibilityNodeInfo.ACTION_CLICK:
        // Perform actual key press when user double-taps in TalkBack
        Log.d(TAG, "Click action on virtual view " + virtualViewId + ", performing key press");

        // Cast view to Keyboard2View to access performAccessibilityKeyPress
        if (_view instanceof Keyboard2View)
        {
          ((Keyboard2View)_view).performAccessibilityKeyPress(keyInfo.key);
        }
        return true;

      default:
        return false;
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
