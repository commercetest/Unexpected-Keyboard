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
}
