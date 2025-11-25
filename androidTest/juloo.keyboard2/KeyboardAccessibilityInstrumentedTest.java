package juloo.keyboard2;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented tests for accessibility features.
 * These tests run on Android device/emulator and test real accessibility behavior.
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class KeyboardAccessibilityInstrumentedTest {

    private Context context;
    private AccessibilityHelper accessibilityHelper;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        accessibilityHelper = new AccessibilityHelper(context);
        accessibilityHelper.setEnabled(true);
    }

    @Test
    public void testAccessibilityManagerAvailable() {
        AccessibilityManager manager = (AccessibilityManager)
            context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        assertNotNull("AccessibilityManager should be available", manager);
    }

    @Test
    public void testAccessibilityHelperCreation() {
        assertNotNull("AccessibilityHelper should be created", accessibilityHelper);
    }

    @Test
    public void testKeyDescriptionForSpace() {
        KeyValue spaceKey = KeyValue.makeCharKey(' ');
        String description = accessibilityHelper.getKeyContentDescription(
            new KeyboardData.Key(new KeyValue[]{spaceKey}, null, 0, 1f, 0f, ""),
            null
        );
        assertEquals("space bar", description);
    }

    @Test
    public void testKeyDescriptionForLetter() {
        KeyValue letterKey = KeyValue.makeCharKey('a');
        String description = accessibilityHelper.getKeyContentDescription(
            new KeyboardData.Key(new KeyValue[]{letterKey}, null, 0, 1f, 0f, ""),
            null
        );
        assertEquals("a", description);
    }

    @Test
    public void testKeyDescriptionForSpecialCharacter() {
        KeyValue commaKey = KeyValue.makeCharKey(',');
        String description = accessibilityHelper.getKeyContentDescription(
            new KeyboardData.Key(new KeyValue[]{commaKey}, null, 0, 1f, 0f, ""),
            null
        );
        assertEquals("comma", description);
    }

    @Test
    public void testKeyDescriptionForBackspace() {
        KeyValue backspaceKey = KeyValue.keyeventKey("⌫",
            android.view.KeyEvent.KEYCODE_DEL, 0);
        String description = accessibilityHelper.getKeyContentDescription(
            new KeyboardData.Key(new KeyValue[]{backspaceKey}, null, 0, 1f, 0f, ""),
            null
        );
        assertEquals("backspace", description);
    }

    @Test
    public void testVerboseMode() {
        accessibilityHelper.setVerboseMode(true);
        // Test that verbose mode is set
        // Actual verbose behavior is tested through TalkBack integration
        accessibilityHelper.setVerboseMode(false);
    }

    @Test
    public void testEnableDisableAnnouncements() {
        accessibilityHelper.setEnabled(false);
        // When disabled, announcements should be skipped
        // This is tested through integration with real views

        accessibilityHelper.setEnabled(true);
        // Re-enable for other tests
    }

    @Test
    public void testKeyboardAccessibilityDelegateCreation() {
        View mockView = new View(context);
        Config config = new Config(context);
        KeyboardAccessibilityDelegate delegate =
            new KeyboardAccessibilityDelegate(mockView, accessibilityHelper, config);
        assertNotNull("KeyboardAccessibilityDelegate should be created", delegate);
    }

    @Test
    public void testKeyContentDescriptionWithModifiers() {
        KeyValue letterKey = KeyValue.makeCharKey('A');
        String description = accessibilityHelper.getKeyContentDescription(
            new KeyboardData.Key(new KeyValue[]{letterKey}, null, 0, 1f, 0f, ""),
            null
        );
        assertEquals("capital a", description);
    }
}
