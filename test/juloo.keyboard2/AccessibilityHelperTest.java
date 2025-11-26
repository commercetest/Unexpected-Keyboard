package juloo.keyboard2;

import android.content.Context;
import android.view.View;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class AccessibilityHelperTest {

    @Mock
    private Context mockContext;

    @Mock
    private View mockView;

    private AccessibilityHelper accessibilityHelper;

    @Before
    public void setUp() {
        accessibilityHelper = new AccessibilityHelper(mockContext);
    }

    @Test
    @org.junit.Ignore("Requires proper AccessibilityManager mocking. See androidTest/ for instrumented version.")
    public void testAnnounceKeyPress() {
        // Given
        KeyValue keyValue = KeyValue.makeCharKey('a');
        Pointers.Modifiers modifiers = Pointers.Modifiers.EMPTY;

        // When
        accessibilityHelper.announceKeyPress(mockView, keyValue, modifiers);

        // Then
        verify(mockView).announceForAccessibility("a");
    }

    @Test
    public void testGetKeyDescription_enter() {
        // Given
        KeyValue keyValue = KeyValue.keyeventKey("enter", android.view.KeyEvent.KEYCODE_ENTER, 0);

        // When
        String description = accessibilityHelper.getKeyDescription(keyValue);

        // Then
        assertEquals("enter", description);
    }

    @Test
    public void testGetKeyDescription_space() {
        // Given
        KeyValue keyValue = KeyValue.makeCharKey(' ');

        // When
        String description = accessibilityHelper.getKeyDescription(keyValue);

        // Then
        assertEquals("space bar", description);
    }

    @Test
    public void testBuildSwipeOptionsAnnouncement() {
        // Given
        KeyValue[] keyValues = new KeyValue[9];
        keyValues[0] = KeyValue.makeCharKey('a');
        keyValues[7] = KeyValue.makeCharKey('b');
        KeyboardData.Key key = new KeyboardData.Key(keyValues, null, 0, 1f, 0f, "");

        // When
        String announcement = accessibilityHelper.buildSwipeOptionsAnnouncement(key);

        // Then
        assertEquals("swipe up to type b", announcement);
    }

    @Test
    public void testCornerDirectionNames() {
        assertEquals("up and left", AccessibilityHelper.getCornerDirectionName(1));
        assertEquals("up and right", AccessibilityHelper.getCornerDirectionName(2));
        assertEquals("down and left", AccessibilityHelper.getCornerDirectionName(3));
        assertEquals("down and right", AccessibilityHelper.getCornerDirectionName(4));
        assertEquals("left", AccessibilityHelper.getCornerDirectionName(5));
        assertEquals("right", AccessibilityHelper.getCornerDirectionName(6));
        assertEquals("up", AccessibilityHelper.getCornerDirectionName(7));
        assertEquals("down", AccessibilityHelper.getCornerDirectionName(8));
        assertEquals("", AccessibilityHelper.getCornerDirectionName(9));
    }

    @Test
    public void testModifierStateDescriptions() {
        assertEquals("off", accessibilityHelper.getModifierStateDescription(false, false));
        assertEquals("on", accessibilityHelper.getModifierStateDescription(true, false));
        assertEquals("locked", accessibilityHelper.getModifierStateDescription(true, true));
        assertEquals("locked", accessibilityHelper.getModifierStateDescription(false, true));
    }
}
