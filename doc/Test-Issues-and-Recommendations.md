# Test Issues and Recommendations
**Unexpected Keyboard - Unit Test Analysis**

**Date:** November 2024
**Command Run:** `./gradlew test`
**Status:** 5 tests failing, 1 test disabled

---

## Executive Summary

The test suite has **compilation and runtime issues** that need to be addressed before automated testing can be reliable. The main problems are:

1. **AccessibilityChecksTest** - Requires AndroidX Test libraries not available for unit tests
2. **AccessibilityHelperTest** - Mock setup issues with Android framework classes
3. **KeyboardAccessibilityDelegateTest** - RuntimeException due to Android API calls in unit tests

**Overall Assessment:** The new accessibility tests were created with the right intention but wrong approach. They're trying to test Android framework integration code as unit tests, which requires either:
- Moving to instrumented tests (androidTest)
- Using Robolectric for Android simulation
- Redesigning the code to be more testable with dependency injection

---

## Test Execution Results

### Test Summary
```
Total Tests: 21
Passed: 16
Failed: 5
Disabled: 1
Success Rate: 76%
```

### Failing Tests

#### 1. AccessibilityChecksTest (DISABLED)
**File:** `test/juloo.keyboard2/AccessibilityChecksTest.java`
**Status:** Compilation error - disabled by renaming to `.java.disabled`

**Error:**
```
error: package androidx.test.espresso.accessibility does not exist
import androidx.test.espresso.accessibility.AccessibilityChecks;
```

**Root Cause:**
The test uses `androidx.test.espresso.accessibility.AccessibilityChecks` which is only available for instrumented tests (`androidTest`), not unit tests (`test`).

**Why This Happens:**
- Dependencies marked as `androidTestImplementation` are NOT available to unit tests
- `AccessibilityChecks` requires Android framework and can only run on device/emulator
- This test was placed in wrong source set

---

#### 2. AccessibilityHelperTest.testAnnounceKeyPress
**File:** `test/juloo.keyboard2/AccessibilityHelperTest.java:40`

**Error:**
```
Wanted but not invoked:
mockView.announceForAccessibility("a");
Actually, there were zero interactions with this mock.
```

**Root Cause:**
The test expects `view.announceForAccessibility()` to be called, but `AccessibilityHelper.announceKeyPress()` checks `isAccessibilityEnabled()` first, which returns `false` because:
1. `mockContext` doesn't have a proper `AccessibilityManager`
2. `AccessibilityManager.isEnabled()` returns `false` by default
3. The announcement is skipped before calling the view method

**Code Flow:**
```java
public void announceKeyPress(View view, KeyValue key, Pointers.Modifiers modifiers) {
  if (!_enabled || !isAccessibilityEnabled() || key == null) {
    return; // ← TEST EXITS HERE
  }
  // ... announcement code never reached
}
```

---

#### 3-6. KeyboardAccessibilityDelegateTest (4 tests)
**File:** `test/juloo.keyboard2/KeyboardAccessibilityDelegateTest.java`

**Tests Failing:**
- `testCreateAccessibilityNodeInfo_forHost_returnsNodeWithChildren` (line 58)
- `testGetVirtualViewIdAt_withValidCoordinates_returnsCorrectId` (line 72)
- `testCreateAccessibilityNodeInfo_forVirtualView_returnsCorrectNode` (line 86)
- `testPerformAction_click_callsPerformAccessibilityKeyPress` (line 101)

**Error:**
```
java.lang.RuntimeException
```

**Root Cause:**
Calling Android framework methods like `AccessibilityNodeInfo.obtain()` in unit tests fails because:
1. Android framework methods are stubs in unit tests (return null or throw)
2. `AccessibilityNodeInfo.obtain()` requires actual Android runtime
3. These are integration tests, not unit tests

**Example Code That Fails:**
```java
// In KeyboardAccessibilityDelegate.java
AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(_view);
// ↑ Returns null in unit tests, causes NullPointerException later
```

---

## Detailed Analysis

### Issue 1: Wrong Test Type

**Problem:**
The new accessibility tests are **instrumented tests** placed in the **unit test** source set.

**Comparison:**

| Aspect | Unit Tests (`test/`) | Instrumented Tests (`androidTest/`) |
|--------|---------------------|-------------------------------------|
| Runs on | JVM (your computer) | Android device/emulator |
| Speed | Fast (milliseconds) | Slow (seconds) |
| Android APIs | Stubs (throw errors) | Real implementation |
| Dependencies | `testImplementation` | `androidTestImplementation` |
| Use for | Pure logic, algorithms | UI, Android framework code |

**What We Have:**
- Tests in `test/` directory (unit tests)
- Testing `AccessibilityHelper` and `KeyboardAccessibilityDelegate`
- Both classes heavily use Android framework APIs
- **Mismatch**: Integration tests in unit test location

---

### Issue 2: Android Framework Dependencies

**Classes Being Tested:**
1. `AccessibilityHelper` - uses:
   - `android.content.Context`
   - `android.view.accessibility.AccessibilityManager`
   - `android.view.accessibility.AccessibilityEvent`
   - `android.view.View`

2. `KeyboardAccessibilityDelegate` - uses:
   - `android.view.accessibility.AccessibilityNodeInfo`
   - `android.view.accessibility.AccessibilityNodeProvider`
   - `android.os.Build`
   - `android.graphics.Rect`

**Problem:**
Unit tests run on the JVM where Android framework classes are **stubs** that:
- Return null
- Throw `RuntimeException`
- Have no real implementation

**Example:**
```java
// In unit test
AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(view);
// ↑ Returns null (stub implementation)

node.setClassName("Test");
// ↑ NullPointerException
```

---

### Issue 3: Mock Setup Complexity

**Current Approach:**
```java
@Mock
private Context mockContext;

@Before
public void setUp() {
    accessibilityHelper = new AccessibilityHelper(mockContext);
}
```

**Problem:**
`AccessibilityHelper` constructor calls:
```java
_accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
```

**What Happens:**
1. `mockContext.getSystemService()` returns `null` (not mocked)
2. `_accessibilityManager` is `null`
3. `isAccessibilityEnabled()` crashes or returns `false`
4. Tests fail

**To Fix (Complex):**
```java
@Mock private Context mockContext;
@Mock private AccessibilityManager mockAccessibilityManager;

@Before
public void setUp() {
    when(mockContext.getSystemService(Context.ACCESSIBILITY_SERVICE))
        .thenReturn(mockAccessibilityManager);
    when(mockAccessibilityManager.isEnabled()).thenReturn(true);
    when(mockAccessibilityManager.isTouchExplorationEnabled()).thenReturn(true);
    // ... many more mocks needed
}
```

**Problem:** Very brittle, breaks when implementation changes

---

### Issue 4: Private Method Testing

**Test Code:**
```java
@Test
public void testGetKeyDescription_space() {
    String description = accessibilityHelper.getKeyDescription(keyValue);
    assertEquals("space bar", description);
}
```

**Problem:**
`getKeyDescription()` is **private** in `AccessibilityHelper.java`:
```java
private String getKeyDescription(KeyValue key) { ... }
```

**Why It Works:**
The test probably changed method visibility to package-private:
```java
String getKeyDescription(KeyValue key) { ... }  // no modifier = package-private
```

**Issue:**
- Violates encapsulation
- Tests internal implementation, not public API
- Methods changed just to enable testing

---

## Recommendations

### Option A: Move to Instrumented Tests (Recommended)

**What to do:**
1. Create `androidTest/` source directory
2. Move accessibility tests there
3. Update `build.gradle.kts` source sets
4. Run with `./gradlew connectedAndroidTest`

**Pros:**
- Tests run on real Android
- No mocking framework APIs
- Tests actual behavior
- More confidence in results

**Cons:**
- Slower execution (needs device/emulator)
- Requires device connection
- More setup in CI/CD

**Implementation:**
```kotlin
// In build.gradle.kts
sourceSets {
    named("androidTest") {
        java.srcDirs("androidTest")
    }
}
```

**Directory Structure:**
```
test/
  juloo.keyboard2/
    ModmapTest.java          ← Keep here (pure logic)
    KeyValueTest.java        ← Keep here

androidTest/               ← CREATE THIS
  juloo.keyboard2/
    AccessibilityHelperInstrumentedTest.java
    KeyboardAccessibilityDelegateTest.java
    AccessibilityChecksTest.java
```

---

### Option B: Use Robolectric (Alternative)

**What is Robolectric:**
A framework that provides Android API implementations for unit tests (runs on JVM, simulates Android).

**Setup:**
```kotlin
// In build.gradle.kts dependencies
testImplementation("org.robolectric:robolectric:4.11")
```

**Test Example:**
```java
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AccessibilityHelperTest {

    @Test
    public void testAnnounceKeyPress() {
        Context context = ApplicationProvider.getApplicationContext();
        AccessibilityHelper helper = new AccessibilityHelper(context);
        // Now Android APIs work!
    }
}
```

**Pros:**
- Fast (still JVM, no device needed)
- Android APIs available
- Good for testing Android components

**Cons:**
- Additional dependency (~10MB)
- Not 100% accurate (simulated Android)
- Learning curve
- Some APIs not fully supported

---

### Option C: Refactor for Testability (Best Practice)

**Problem:**
Tight coupling to Android framework makes testing hard.

**Solution:**
Dependency injection and interfaces.

**Current:**
```java
public class AccessibilityHelper {
    private AccessibilityManager _accessibilityManager;

    public AccessibilityHelper(Context context) {
        _accessibilityManager = (AccessibilityManager)
            context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }
}
```

**Refactored:**
```java
public class AccessibilityHelper {
    private final AccessibilityChecker _checker;

    // Constructor injection
    public AccessibilityHelper(AccessibilityChecker checker) {
        _checker = checker;
    }

    // Factory method for real usage
    public static AccessibilityHelper create(Context context) {
        AccessibilityManager manager = (AccessibilityManager)
            context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return new AccessibilityHelper(new AndroidAccessibilityChecker(manager));
    }
}

// Interface for testability
interface AccessibilityChecker {
    boolean isEnabled();
    boolean isTouchExplorationEnabled();
}

// Real implementation
class AndroidAccessibilityChecker implements AccessibilityChecker {
    private final AccessibilityManager _manager;

    AndroidAccessibilityChecker(AccessibilityManager manager) {
        _manager = manager;
    }

    @Override
    public boolean isEnabled() {
        return _manager != null && _manager.isEnabled();
    }

    @Override
    public boolean isTouchExplorationEnabled() {
        return _manager != null && _manager.isTouchExplorationEnabled();
    }
}
```

**Test with Mock:**
```java
@Test
public void testAnnounceKeyPress() {
    AccessibilityChecker mockChecker = mock(AccessibilityChecker.class);
    when(mockChecker.isEnabled()).thenReturn(true);
    when(mockChecker.isTouchExplorationEnabled()).thenReturn(true);

    AccessibilityHelper helper = new AccessibilityHelper(mockChecker);
    // Now testable!
}
```

**Pros:**
- Clean separation of concerns
- Easy to unit test
- No Android dependencies in tests
- Best practice design

**Cons:**
- Requires code refactoring
- More boilerplate
- Needs careful design

---

### Option D: Delete or Disable Failing Tests (Quick Fix)

**For immediate CI/CD:**
1. Disable `AccessibilityChecksTest` (already done)
2. Comment out or delete failing tests
3. Add TODO comments for future work

**Pros:**
- Unblocks CI/CD immediately
- No code changes needed
- Existing tests still pass

**Cons:**
- No test coverage for accessibility
- Technical debt
- Easy to forget

---

## Specific Recommendations by Test

### AccessibilityChecksTest.java

**Current Status:** Disabled (renamed to `.java.disabled`)

**Recommendation:** **Move to androidTest/**

**Reason:**
This test is specifically for Espresso Accessibility Checks, which:
- MUST run on Android device
- Requires UI interaction
- Is an integration test by nature

**Action Items:**
1. Create `androidTest/juloo.keyboard2/` directory
2. Move file there (rename back to `.java`)
3. Create a proper test activity
4. Run with `connectedAndroidTest`

**Example Test:**
```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class KeyboardAccessibilityTest {

    @Rule
    public ActivityScenarioRule<TestActivity> activityRule =
        new ActivityScenarioRule<>(TestActivity.class);

    @BeforeClass
    public static void enableAccessibilityChecks() {
        AccessibilityChecks.enable();
    }

    @Test
    public void testKeyboardMeetsAccessibilityGuidelines() {
        onView(withId(R.id.keyboard_view))
            .check(matches(isDisplayed()));
        // Accessibility checks run automatically
    }
}
```

---

### AccessibilityHelperTest.java

**Current Failures:** 1 out of 4 tests

**Passing Tests:**
- ✅ `testGetKeyDescription_enter`
- ✅ `testGetKeyDescription_space`
- ✅ `testBuildSwipeOptionsAnnouncement`

**Failing Test:**
- ❌ `testAnnounceKeyPress`

**Recommendation:** **Fix mock setup OR use Robolectric**

#### Solution 1: Fix Mock Setup

```java
@RunWith(MockitoJUnitRunner.class)
public class AccessibilityHelperTest {

    @Mock private Context mockContext;
    @Mock private AccessibilityManager mockAccessibilityManager;
    @Mock private View mockView;

    private AccessibilityHelper helper;

    @Before
    public void setUp() {
        // Mock the accessibility manager
        when(mockContext.getSystemService(Context.ACCESSIBILITY_SERVICE))
            .thenReturn(mockAccessibilityManager);
        when(mockAccessibilityManager.isEnabled()).thenReturn(true);
        when(mockAccessibilityManager.isTouchExplorationEnabled()).thenReturn(true);

        helper = new AccessibilityHelper(mockContext);
        helper.setEnabled(true); // Ensure helper is enabled
    }

    @Test
    public void testAnnounceKeyPress() {
        KeyValue keyValue = KeyValue.makeCharKey('a');
        Pointers.Modifiers modifiers = Pointers.Modifiers.EMPTY;

        helper.announceKeyPress(mockView, keyValue, modifiers);

        verify(mockView).announceForAccessibility("a");
    }
}
```

**Pros:**
- Keeps as unit test
- Fast execution
- No additional dependencies

**Cons:**
- Complex mock setup
- Brittle (breaks on implementation changes)
- Doesn't test Android integration

#### Solution 2: Use Robolectric

```java
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AccessibilityHelperTest {

    private Context context;
    private AccessibilityHelper helper;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        // Enable accessibility in Robolectric
        AccessibilityManager manager = (AccessibilityManager)
            context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        shadowOf(manager).setEnabled(true);
        shadowOf(manager).setTouchExplorationEnabled(true);

        helper = new AccessibilityHelper(context);
        helper.setEnabled(true);
    }

    @Test
    public void testAnnounceKeyPress() {
        View view = new View(context);
        KeyValue keyValue = KeyValue.makeCharKey('a');

        helper.announceKeyPress(view, keyValue, Pointers.Modifiers.EMPTY);

        // Verify announcement was made (Robolectric tracks this)
        ShadowView shadowView = shadowOf(view);
        assertEquals("a", shadowView.getLastAccessibilityAnnouncement());
    }
}
```

**Pros:**
- Real Android APIs
- Still runs on JVM (fast)
- Good balance

**Cons:**
- Adds Robolectric dependency
- Requires learning Shadows API

---

### KeyboardAccessibilityDelegateTest.java

**Current Failures:** All 4 tests

**Recommendation:** **Move to androidTest/ as instrumented tests**

**Reason:**
This class:
- Extends `AccessibilityNodeProvider` (Android framework)
- Creates `AccessibilityNodeInfo` objects
- Manages virtual view hierarchy
- Cannot be unit tested without full Android runtime

**Alternative (if must stay unit test):** Use Robolectric

```java
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class KeyboardAccessibilityDelegateTest {

    private Context context;
    private Keyboard2View view;
    private AccessibilityHelper helper;
    private KeyboardAccessibilityDelegate delegate;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        view = new Keyboard2View(context, null);
        helper = new AccessibilityHelper(context);
        delegate = new KeyboardAccessibilityDelegate(view, helper);
    }

    @Test
    public void testCreateAccessibilityNodeInfo() {
        KeyboardData keyboard = createTestKeyboard();
        delegate.setKeyboardData(keyboard, 10, 0, 0, 10, 0, 0);

        AccessibilityNodeInfo nodeInfo =
            delegate.createAccessibilityNodeInfo(View.NO_ID);

        assertNotNull(nodeInfo);
        // Node info methods now work!
    }
}
```

---

## Implementation Plan

### Phase 1: Quick Fix (Immediate)
**Goal:** Get tests passing in CI/CD

**Actions:**
1. ✅ Disable `AccessibilityChecksTest` (already done)
2. Add proper mocking to `AccessibilityHelperTest`
3. Disable `KeyboardAccessibilityDelegateTest` for now
4. Document as technical debt

**Result:** 16/21 tests passing

---

### Phase 2: Add Robolectric (Week 1)
**Goal:** Enable unit testing of Android code

**Actions:**
1. Add Robolectric dependency to `build.gradle.kts`
2. Convert `AccessibilityHelperTest` to use Robolectric
3. Convert `KeyboardAccessibilityDelegateTest` to use Robolectric
4. Re-enable all tests

**Dependencies:**
```kotlin
testImplementation("org.robolectric:robolectric:4.11")
testImplementation("androidx.test:core:1.5.0")
```

**Result:** All unit tests passing with Android simulation

---

### Phase 3: Add Instrumented Tests (Week 2-3)
**Goal:** Real device testing

**Actions:**
1. Create `androidTest/` directory structure
2. Move `AccessibilityChecksTest` to `androidTest/`
3. Create end-to-end accessibility tests
4. Set up CI/CD with connected tests

**New Tests:**
- `KeyboardAccessibilityIntegrationTest` - Full keyboard with TalkBack
- `AccessibilityAnnouncementTest` - Verify announcements
- `VirtualViewNavigationTest` - Test key navigation

**Result:** Comprehensive test coverage at all levels

---

### Phase 4: Refactor for Testability (Month 2)
**Goal:** Clean architecture

**Actions:**
1. Extract interfaces for Android dependencies
2. Implement dependency injection
3. Rewrite tests to use interfaces
4. Remove Robolectric if no longer needed

**Result:** Clean, maintainable, easily testable code

---

## Testing Strategy Matrix

| Test Type | Location | Framework | Use Case | Example |
|-----------|----------|-----------|----------|---------|
| **Unit Tests** | `test/` | JUnit + Mockito | Pure logic, algorithms | `KeyValueTest`, `ModmapTest` |
| **Robolectric Tests** | `test/` | JUnit + Robolectric | Android components (simulated) | `AccessibilityHelperTest` |
| **Instrumented Tests** | `androidTest/` | AndroidX Test + Espresso | UI, real Android behavior | `KeyboardAccessibilityTest` |
| **Integration Tests** | `androidTest/` | AndroidX Test | Full app testing | `AccessibilityE2ETest` |

---

## Build Configuration Updates

### Current Dependencies
```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.mockito:mockito-core:4.5.1")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.5.1")
```

### Recommended Additions
```kotlin
// For Robolectric (if Option B chosen)
testImplementation("org.robolectric:robolectric:4.11")
testImplementation("androidx.test:core:1.5.0")
testImplementation("androidx.test:runner:1.5.2")

// For better assertions
testImplementation("org.hamcrest:hamcrest:2.2")
testImplementation("com.google.truth:truth:1.1.5")

// For instrumented tests (already have Espresso)
androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
androidTestImplementation("com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:4.0.0")
```

---

## Gradle Test Commands

### Run All Unit Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests juloo.keyboard2.KeyValueTest
```

### Run All Instrumented Tests (requires device)
```bash
./gradlew connectedAndroidTest
```

### Run Tests with Coverage
```bash
./gradlew testDebugUnitTestCoverage
```

### Run in Debug Mode
```bash
./gradlew test --debug-jvm
```

---

## Conclusion

### Immediate Actions (This Week)
1. ✅ Disable `AccessibilityChecksTest` - DONE
2. Add proper mocking to `AccessibilityHelperTest.testAnnounceKeyPress`
3. Add `@Ignore` annotation to `KeyboardAccessibilityDelegateTest` tests
4. Document decisions in test files

### Short Term (Next 2 Weeks)
1. Decide: Robolectric OR Instrumented Tests (or both)
2. Implement chosen approach
3. Achieve 80%+ passing rate
4. Set up CI/CD with tests

### Long Term (Next Month)
1. Full instrumented test suite
2. Accessibility Scanner integration
3. Automated TalkBack testing
4. Performance benchmarks

### Success Criteria
- ✅ `./gradlew test` passes with 95%+ success rate
- ✅ CI/CD runs tests automatically
- ✅ New PRs require passing tests
- ✅ Accessibility regressions caught early

---

**Document Version:** 1.0
**Last Updated:** 2024-11-22
**Author:** Analysis of test failures from `./gradlew test`
**Related:** `doc/Accessibility-Assessment.md`, `doc/TESTING_EXAMPLES.md`
