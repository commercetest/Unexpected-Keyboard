package juloo.keyboard2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented tests for Pointers.java with real Android context and Config.
 * These tests complement the unit tests by testing full touch lifecycle,
 * timing behaviors, and multi-touch coordination.
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PointersInstrumentedTest {

    private Context context;
    private Config config;
    private TestPointerHandler handler;
    private Pointers pointers;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        ensureConfigInitialized(context);
        config = Config.globalConfig();
        handler = new TestPointerHandler();

        // Create Pointers on main thread since it creates a Handler
        final Pointers[] pointersHolder = new Pointers[1];
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(
            new Runnable() {
                @Override
                public void run() {
                    pointersHolder[0] = new Pointers(handler, config);
                }
            }
        );
        pointers = pointersHolder[0];
    }

    // ========================================================================
    // TOUCH LIFECYCLE TESTS
    // ========================================================================

    @Test
    public void touchDown_triggersPointerDownCallback() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);

        assertEquals("Should trigger onPointerDown", 1, handler.downs.size());
        assertEquals("Should pass correct KeyValue", kv, handler.downs.get(0));
    }

    @Test
    public void touchUp_triggersPointerUpCallback() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchUp(0);

        assertEquals("Should trigger onPointerUp", 1, handler.ups.size());
        assertEquals("Should pass correct KeyValue", kv, handler.ups.get(0));
    }

    @Test
    public void touchDown_incrementsPointerCount() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        assertEquals("Initially zero pointers", 0, pointers.getActivePointerCount());

        pointers.onTouchDown(100f, 100f, 0, key);

        assertEquals("Should have 1 active pointer", 1, pointers.getActivePointerCount());
    }

    @Test
    public void touchUp_removesPointerFromActiveSet() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        assertEquals(1, pointers.getActivePointerCount());

        pointers.onTouchUp(0);

        assertEquals("Pointer should be removed", 0, pointers.getActivePointerCount());
        assertNull("Pointer should not be accessible", pointers.getPointer(0));
    }

    @Test
    public void touchCancel_clearsAllPointers() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchDown(200f, 200f, 1, key);
        assertEquals(2, pointers.getActivePointerCount());

        pointers.onTouchCancel();

        assertEquals("All pointers should be cleared", 0, pointers.getActivePointerCount());
    }

    @Test
    public void touchCancel_doesNotTriggerUpCallbacks() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        handler.ups.clear();

        pointers.onTouchCancel();

        assertEquals("Cancel should not trigger onPointerUp", 0, handler.ups.size());
    }

    @Test
    public void getPointer_returnsPointerWithCorrectProperties() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 150f, 5, key);

        Pointers.Pointer ptr = pointers.getPointer(5);
        assertNotNull("Pointer should exist", ptr);
        assertEquals("Pointer ID should match", 5, ptr.pointerId);
        assertEquals("Down X should match", 100f, ptr.downX, 0.1f);
        assertEquals("Down Y should match", 150f, ptr.downY, 0.1f);
    }

    // ========================================================================
    // MULTI-TOUCH TESTS
    // ========================================================================

    @Test
    public void multiTouch_twoSimultaneousPointers_trackedIndependently() {
        KeyValue kv1 = KeyValue.makeCharKey('a');
        KeyValue kv2 = KeyValue.makeCharKey('b');
        KeyboardData.Key key1 = createKey(kv1);
        KeyboardData.Key key2 = createKey(kv2);

        pointers.onTouchDown(100f, 100f, 0, key1);
        pointers.onTouchDown(200f, 200f, 1, key2);

        assertEquals("Should have 2 active pointers", 2, pointers.getActivePointerCount());

        Pointers.Pointer ptr0 = pointers.getPointer(0);
        Pointers.Pointer ptr1 = pointers.getPointer(1);

        assertNotNull("Pointer 0 should exist", ptr0);
        assertNotNull("Pointer 1 should exist", ptr1);
        assertEquals("Pointer 0 ID", 0, ptr0.pointerId);
        assertEquals("Pointer 1 ID", 1, ptr1.pointerId);
    }

    @Test
    public void multiTouch_releaseFirstPointer_secondRemains() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchDown(200f, 200f, 1, key);

        pointers.onTouchUp(0);

        assertEquals("Should have 1 active pointer", 1, pointers.getActivePointerCount());
        assertNull("Pointer 0 should be removed", pointers.getPointer(0));
        assertNotNull("Pointer 1 should remain", pointers.getPointer(1));
    }

    @Test
    public void multiTouch_threeSimultaneousPointers_allTracked() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchDown(200f, 200f, 1, key);
        pointers.onTouchDown(300f, 300f, 2, key);

        assertEquals("Should have 3 active pointers", 3, pointers.getActivePointerCount());

        List<Integer> ids = pointers.getActivePointerIds();
        assertTrue("Should contain pointer 0", ids.contains(0));
        assertTrue("Should contain pointer 1", ids.contains(1));
        assertTrue("Should contain pointer 2", ids.contains(2));
    }

    @Test
    public void multiTouch_getActivePointerIds_returnsAllActiveIds() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchDown(200f, 200f, 1, key);

        List<Integer> ids = pointers.getActivePointerIds();

        assertEquals("Should return 2 IDs", 2, ids.size());
        assertTrue("Should contain ID 0", ids.contains(0));
        assertTrue("Should contain ID 1", ids.contains(1));
    }

    // ========================================================================
    // MODIFIER TESTS (Latch and Lock)
    // ========================================================================

    @Test
    public void modifier_tapShift_latchesModifier() {
        KeyValue shift = KeyValue.getKeyByName("shift");
        KeyboardData.Key key = createKey(shift);

        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchUp(0);

        Pointers.Modifiers mods = pointers.getModifiers();
        assertEquals("Shift should be latched", 1, mods.size());
    }

    @Test
    public void modifier_latch_clearedAfterNormalKey() {
        KeyValue shift = KeyValue.getKeyByName("shift");
        KeyValue letterA = KeyValue.makeCharKey('a');
        KeyboardData.Key shiftKey = createKey(shift);
        KeyboardData.Key letterKey = createKey(letterA);

        // Latch shift
        pointers.onTouchDown(100f, 100f, 0, shiftKey);
        pointers.onTouchUp(0);
        assertEquals("Shift should be latched", 1, pointers.getModifiers().size());

        // Press normal key
        pointers.onTouchDown(200f, 200f, 1, letterKey);
        pointers.onTouchUp(1);

        // Shift should be cleared
        assertEquals("Shift should be cleared", 0, pointers.getModifiers().size());
    }

    @Test
    public void modifier_getModifiers_skipLatched_excludesLatchedKeys() {
        KeyValue shift = KeyValue.getKeyByName("shift");
        KeyboardData.Key key = createKey(shift);

        // Latch shift (not locked)
        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchUp(0);

        // Default: latched included
        assertEquals("Should include latched", 1, pointers.getModifiers(false).size());

        // Skip latched: should be empty
        assertEquals("Should exclude latched", 0, pointers.getModifiers(true).size());
    }

    @Test
    public void keyFlags_modifierKey_returnsCorrectFlags() {
        KeyValue shift = KeyValue.getKeyByName("shift");
        KeyboardData.Key key = createKey(shift);

        pointers.onTouchDown(100f, 100f, 0, key);

        int flags = pointers.getKeyFlags(shift);
        assertNotEquals("Should have flags set", -1, flags);
        assertTrue("Should have LATCHABLE flag",
            (flags & Pointers.FLAG_P_LATCHABLE) != 0);
    }

    // ========================================================================
    // TIMING TESTS (Long Press)
    // ========================================================================

    @Test
    public void longPress_afterDelay_triggersHold() throws InterruptedException {
        KeyValue letterA = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(letterA);

        final CountDownLatch latch = new CountDownLatch(1);
        handler.onHoldCallback = () -> latch.countDown();

        pointers.onTouchDown(100f, 100f, 0, key);

        // Wait for long press timeout + buffer
        boolean holdTriggered = latch.await(
            config.longPressTimeout + 500,
            TimeUnit.MILLISECONDS
        );

        assertTrue("Long press should trigger hold callback", holdTriggered);
        assertTrue("Should have at least 1 hold event", handler.holds.size() >= 1);
    }

    @Test
    public void longPress_canceledByTouchUp_doesNotTriggerHold() throws InterruptedException {
        KeyValue letterA = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(letterA);

        pointers.onTouchDown(100f, 100f, 0, key);

        // Release immediately (before long press timeout)
        Thread.sleep(50);
        pointers.onTouchUp(0);

        // Wait to see if hold gets triggered (it shouldn't)
        Thread.sleep(config.longPressTimeout + 200);

        assertEquals("Hold should not be triggered", 0, handler.holds.size());
    }

    @Test
    public void longPress_onModifier_locksModifier() throws InterruptedException {
        KeyValue shift = KeyValue.getKeyByName("shift");
        KeyboardData.Key key = createKey(shift);

        pointers.onTouchDown(100f, 100f, 0, key);

        // Wait for long press
        Thread.sleep(config.longPressTimeout + 200);

        int flags = pointers.getKeyFlags(shift);
        assertTrue("Shift should be locked",
            (flags & Pointers.FLAG_P_LOCKED) != 0);
    }

    @Test
    public void longPress_lockedModifier_staysAfterNormalKey() throws InterruptedException {
        KeyValue shift = KeyValue.getKeyByName("shift");
        KeyValue letterA = KeyValue.makeCharKey('a');
        KeyboardData.Key shiftKey = createKey(shift);
        KeyboardData.Key letterKey = createKey(letterA);

        // Long press to lock shift
        pointers.onTouchDown(100f, 100f, 0, shiftKey);
        Thread.sleep(config.longPressTimeout + 200);
        pointers.onTouchUp(0);

        int modsBeforeKey = pointers.getModifiers().size();
        assertTrue("Shift should be locked", modsBeforeKey > 0);

        // Press normal key
        pointers.onTouchDown(200f, 200f, 1, letterKey);
        pointers.onTouchUp(1);

        // Shift should still be active (locked)
        assertEquals("Locked shift should remain", modsBeforeKey, pointers.getModifiers().size());
    }

    // ========================================================================
    // STATE INSPECTION TESTS
    // ========================================================================

    @Test
    public void clear_removesAllPointers() {
        KeyValue kv = KeyValue.makeCharKey('a');
        KeyboardData.Key key = createKey(kv);

        pointers.onTouchDown(100f, 100f, 0, key);
        pointers.onTouchDown(200f, 200f, 1, key);
        assertEquals(2, pointers.getActivePointerCount());

        pointers.clear();

        assertEquals("All pointers should be cleared", 0, pointers.getActivePointerCount());
    }

    @Test
    public void getActivePointerIds_excludesLatchedPointers() {
        KeyValue shift = KeyValue.getKeyByName("shift");
        KeyValue letterA = KeyValue.makeCharKey('a');
        KeyboardData.Key shiftKey = createKey(shift);
        KeyboardData.Key letterKey = createKey(letterA);

        // Latch shift
        pointers.onTouchDown(100f, 100f, 0, shiftKey);
        pointers.onTouchUp(0);

        // Press another key
        pointers.onTouchDown(200f, 200f, 1, letterKey);

        List<Integer> ids = pointers.getActivePointerIds();

        // Should only include the letter key, not the latched shift
        assertEquals("Should only include non-latched pointer", 1, ids.size());
        assertTrue("Should contain letter key pointer", ids.contains(1));
    }

    // ========================================================================
    // HELPER CLASSES AND METHODS
    // ========================================================================

    private static class TestPointerHandler implements Pointers.IPointerEventHandler {
        final ArrayList<KeyValue> downs = new ArrayList<>();
        final ArrayList<KeyValue> ups = new ArrayList<>();
        final ArrayList<KeyValue> holds = new ArrayList<>();
        Runnable onHoldCallback = null;

        @Override
        public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods) {
            return k;
        }

        @Override
        public void onPointerDown(KeyValue k, boolean isSwipe) {
            downs.add(k);
        }

        @Override
        public void onPointerUp(KeyValue k, Pointers.Modifiers mods) {
            ups.add(k);
        }

        @Override
        public void onPointerFlagsChanged(boolean shouldVibrate) {
        }

        @Override
        public void onPointerHold(KeyValue k, Pointers.Modifiers mods) {
            holds.add(k);
            if (onHoldCallback != null) {
                onHoldCallback.run();
            }
        }
    }

    private KeyboardData.Key createKey(KeyValue kv) {
        KeyValue[] kvs = new KeyValue[9];
        kvs[0] = kv;
        return new KeyboardData.Key(kvs, null, 0, 1f, 0f, "");
    }

    private void ensureConfigInitialized(Context ctx) {
        if (Config.globalConfig() != null) {
            return;
        }
        Config.initGlobalConfig(
            ctx.getSharedPreferences("pointers-test", Context.MODE_PRIVATE),
            ctx.getResources(),
            new Config.IKeyEventHandler() {
                @Override
                public void key_down(KeyValue value, boolean is_swipe) { }
                @Override
                public void key_up(KeyValue value, Pointers.Modifiers mods) { }
                @Override
                public void mods_changed(Pointers.Modifiers mods) { }
            },
            Boolean.FALSE
        );
    }
}
