package juloo.keyboard2;

import androidx.test.espresso.accessibility.AccessibilityChecks;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Enables Espresso Accessibility Checks for all tests.
 * This test automatically runs accessibility checks using Google's
 * Accessibility Test Framework when Espresso views are interacted with.
 *
 * Run with: ./gradlew connectedAndroidTest
 *
 * Note: This is a base class to enable accessibility checks.
 * Actual UI tests should be added to test keyboard interaction.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AccessibilityChecksTest {

    @BeforeClass
    public static void enableAccessibilityChecks() {
        // Enable accessibility checks for all Espresso tests
        AccessibilityChecks.enable();
    }

    @Test
    public void accessibilityChecksEnabled() {
        // This test verifies that accessibility checks are enabled
        // Actual UI tests with keyboard views should be added here
        // or in separate test classes

        // For now, this is a placeholder to ensure the test class
        // compiles and runs, enabling checks for future tests
    }

    // TODO: Add actual UI tests here that interact with Keyboard2View
    // Example:
    // @Test
    // public void testKeyboardMeetsAccessibilityGuidelines() {
    //     onView(withId(R.id.keyboard_view))
    //         .check(matches(isDisplayed()));
    //     // AccessibilityChecks run automatically
    // }
}
