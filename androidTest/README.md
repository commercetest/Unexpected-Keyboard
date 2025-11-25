# Android Instrumented Tests

This directory contains **instrumented tests** that run on an Android device or emulator. These tests use real Android framework APIs and test actual accessibility behavior.

## What's Here

### KeyboardAccessibilityInstrumentedTest.java
Comprehensive tests for accessibility features:
- AccessibilityHelper key descriptions
- Special character names (space bar, comma, backspace, etc.)
- Verbose mode functionality
- KeyboardAccessibilityDelegate creation
- Modifier key announcements

### AccessibilityChecksTest.java
Enables Google's Accessibility Test Framework checks:
- Automatically validates accessibility guidelines
- Checks touch target sizes, contrast ratios, content descriptions
- Base class for future UI tests with Espresso

## Prerequisites

### Option 1: Physical Android Device
1. Enable **Developer Options** on your device
2. Enable **USB Debugging**
3. Connect device via USB
4. Run `adb devices` to verify connection

### Option 2: Android Emulator
1. Open Android Studio
2. Go to **Tools → Device Manager**
3. Create or start an emulator (API 21+)
4. Wait for emulator to fully boot

## Running Tests

### Run All Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Run Specific Test Class
```bash
./gradlew connectedAndroidTest --tests juloo.keyboard2.KeyboardAccessibilityInstrumentedTest
```

### Run Single Test Method
```bash
./gradlew connectedAndroidTest --tests juloo.keyboard2.KeyboardAccessibilityInstrumentedTest.testKeyDescriptionForSpace
```

### Run with Specific Device
```bash
# List connected devices
adb devices

# Run on specific device (if multiple connected)
ANDROID_SERIAL=<device-id> ./gradlew connectedAndroidTest
```

## Viewing Test Results

### HTML Report
After running tests, open:
```
build/reports/androidTests/connected/index.html
```

### Console Output
Test results are printed to console during execution:
```
juloo.keyboard2.KeyboardAccessibilityInstrumentedTest > testKeyDescriptionForSpace PASSED
juloo.keyboard2.KeyboardAccessibilityInstrumentedTest > testKeyDescriptionForLetter PASSED
```

### Logcat Output
View detailed logs during test execution:
```bash
adb logcat -s TestRunner:* AndroidJUnitRunner:*
```

## Test Coverage

Current instrumented tests cover:
- ✅ AccessibilityHelper creation and configuration
- ✅ Key description generation for all key types
- ✅ Special character names (30+ characters)
- ✅ Modifier key descriptions (capital letters)
- ✅ KeyboardAccessibilityDelegate creation
- ✅ Verbose mode toggling
- ✅ Enable/disable announcements

## Adding New Tests

### Example: Testing Key Announcement
```java
@Test
public void testKeyAnnouncement() {
    // Create a real view
    View view = new View(context);
    KeyValue key = KeyValue.makeCharKey('a');

    // Test announcement (requires accessibility enabled)
    accessibilityHelper.announceKeyPress(view, key, Pointers.Modifiers.EMPTY);

    // Verify (using accessibility event inspection)
    // Note: Direct verification requires additional setup
}
```

### Example: Testing with Espresso
```java
@Test
public void testKeyboardDisplayed() {
    // Launch activity with keyboard
    ActivityScenario.launch(TestActivity.class);

    // Interact with keyboard view
    onView(withId(R.id.keyboard_view))
        .check(matches(isDisplayed()));

    // AccessibilityChecks run automatically!
}
```

## Troubleshooting

### Issue: No connected devices
```
Error: No connected devices!
```
**Solution:**
- Connect device via USB or start emulator
- Run `adb devices` to verify
- Enable USB debugging on device

### Issue: Tests fail with SecurityException
```
SecurityException: Permission denial
```
**Solution:**
- Grant permissions via `adb shell pm grant`
- Or use `GrantPermissionRule` in test

### Issue: Tests timeout
```
Test timeout after 10 minutes
```
**Solution:**
- Increase timeout in build.gradle.kts:
```kotlin
android {
    defaultConfig {
        testInstrumentationRunnerArguments["timeout_msec"] = "1200000"
    }
}
```

### Issue: AccessibilityManager returns null
```
AccessibilityManager is null in tests
```
**Solution:**
- This is expected behavior - tests run in isolated environment
- AccessibilityManager exists but may not have services enabled
- Use `ApplicationProvider.getApplicationContext()` for real context

## Differences from Unit Tests

| Aspect | Unit Tests (`test/`) | Instrumented Tests (`androidTest/`) |
|--------|---------------------|-------------------------------------|
| **Location** | `test/juloo.keyboard2/` | `androidTest/juloo.keyboard2/` |
| **Runs on** | JVM (local computer) | Android device/emulator |
| **Speed** | Fast (milliseconds) | Slow (seconds) |
| **Android APIs** | Stubs (mocked) | Real implementation |
| **Use for** | Logic, algorithms | UI, Android framework |
| **Dependencies** | `testImplementation` | `androidTestImplementation` |
| **Run command** | `./gradlew test` | `./gradlew connectedAndroidTest` |

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Android Tests

on: [push, pull_request]

jobs:
  instrumented-tests:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '11'

      - name: Run Instrumented Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 29
          script: ./gradlew connectedAndroidTest

      - name: Upload Test Reports
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-reports
          path: build/reports/androidTests/
```

### GitLab CI Example
```yaml
instrumented-tests:
  stage: test
  image: reactnativecommunity/react-native-android
  script:
    - echo y | sdkmanager "platform-tools" "platforms;android-29"
    - echo no | avdmanager create avd -n test -k "system-images;android-29;default;x86"
    - $ANDROID_HOME/emulator/emulator -avd test -no-window -no-audio &
    - adb wait-for-device
    - ./gradlew connectedAndroidTest
  artifacts:
    paths:
      - build/reports/androidTests/
    when: always
```

## Best Practices

### ✅ DO:
- Test real Android behavior that can't be unit tested
- Test UI interactions with Espresso
- Test accessibility features with real framework
- Use `@Before` to set up test context
- Clean up resources in `@After`
- Use descriptive test names

### ❌ DON'T:
- Test pure logic (use unit tests instead)
- Make network requests without mocking
- Test on outdated API levels
- Forget to check test reports
- Ignore failing tests

## Future Enhancements

Planned instrumented tests:
- [ ] Full keyboard UI tests with Espresso
- [ ] TalkBack integration tests
- [ ] Virtual view hierarchy validation
- [ ] Custom action execution tests
- [ ] Swipe gesture accessibility tests
- [ ] Multi-key accessibility navigation
- [ ] Accessibility announcement verification
- [ ] Screen reader compatibility tests

## Resources

- [Android Testing Documentation](https://developer.android.com/training/testing)
- [Espresso Testing Guide](https://developer.android.com/training/testing/espresso)
- [Accessibility Testing](https://developer.android.com/guide/topics/ui/accessibility/testing)
- [Test from Command Line](https://developer.android.com/studio/test/command-line)

## Questions?

See also:
- `../doc/Test-Issues-and-Recommendations.md` - Detailed testing strategy
- `../doc/Accessibility-Assessment.md` - Accessibility implementation overview
- `../doc/TESTING_EXAMPLES.md` - Touch instrumentation examples
