package juloo.keyboard2;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyboardAccessibilityDelegateTest {

    @Mock
    private Context mockContext;

    @Mock
    private View mockView;

    @Mock
    private AccessibilityHelper mockAccessibilityHelper;

    @Mock
    private Config mockConfig;

    private KeyboardAccessibilityDelegate delegate;

    private KeyboardData createTestKeyboard(float keyWidth) {
        KeyValue[] keyValues = new KeyValue[9];
        keyValues[0] = KeyValue.makeCharKey('a');
        KeyboardData.Key key = new KeyboardData.Key(keyValues, null, 0, keyWidth, 0.0f, "");

        List<KeyboardData.Key> keys = new ArrayList<>();
        keys.add(key);

        KeyboardData.Row row = new KeyboardData.Row(keys, 1.0f, 0.0f);
        List<KeyboardData.Row> rows = new ArrayList<>();
        rows.add(row);

        return new KeyboardData(rows, keyWidth, null, "en", "en", "test", true, false, true);
    }

    @Before
    public void setUp() {
        when(mockView.getContext()).thenReturn(mockContext);
        delegate = new KeyboardAccessibilityDelegate(mockView, mockAccessibilityHelper, mockConfig);
    }

    @Test
    @org.junit.Ignore("Requires real Android framework. Moved to androidTest/ as instrumented test.")
    public void testCreateAccessibilityNodeInfo_forHost_returnsNodeWithChildren() {
        // Given
        KeyboardData keyboardData = createTestKeyboard(10f);
        delegate.setKeyboardData(keyboardData, 10, 0, 0, 10, 0, 0);

        // When
        AccessibilityNodeInfo nodeInfo = delegate.createAccessibilityNodeInfo(View.NO_ID);

        // Then
        assertNotNull(nodeInfo);
        assertEquals(1, nodeInfo.getChildCount());
    }

    @Test
    @org.junit.Ignore("Requires real Android framework. Moved to androidTest/ as instrumented test.")
    public void testGetVirtualViewIdAt_withValidCoordinates_returnsCorrectId() {
        // Given
        KeyboardData keyboardData = createTestKeyboard(10f);
        delegate.setKeyboardData(keyboardData, 10, 0, 0, 10, 0, 0);

        // When
        int virtualViewId = delegate.getVirtualViewIdAt(5, 5);

        // Then
        assertEquals(0, virtualViewId);
    }

    @Test
    @org.junit.Ignore("Requires real Android framework. Moved to androidTest/ as instrumented test.")
    public void testCreateAccessibilityNodeInfo_forVirtualView_returnsCorrectNode() {
        // Given
        KeyboardData keyboardData = createTestKeyboard(10f);
        KeyboardData.Key testKey = keyboardData.rows.get(0).keys.get(0);
        delegate.setKeyboardData(keyboardData, 10, 0, 0, 10, 0, 0);
        when(mockAccessibilityHelper.getKeyContentDescription(testKey, null)).thenReturn("a");

        // When
        AccessibilityNodeInfo nodeInfo = delegate.createAccessibilityNodeInfo(0);

        // Then
        assertNotNull(nodeInfo);
        assertEquals("a", nodeInfo.getContentDescription());
    }

    @Test
    @org.junit.Ignore("Requires real Android framework. Moved to androidTest/ as instrumented test.")
    public void testPerformAction_click_callsPerformAccessibilityKeyPress() {
        // Given
        KeyboardData keyboardData = createTestKeyboard(10f);
        delegate.setKeyboardData(keyboardData, 10, 0, 0, 10, 0, 0);

        // When
        boolean result = delegate.performAction(0, AccessibilityNodeInfo.ACTION_CLICK, null);

        // Then
        assertEquals(true, result);
    }
}
