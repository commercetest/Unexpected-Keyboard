package juloo.keyboard2;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Assume;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class KeyboardAccessibilityActionsInstrumentedTest {

    private Context context;
    private AccessibilityHelper accessibilityHelper;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ensureConfigInitialized(context);
        accessibilityHelper = new AccessibilityHelper(context);
    }

    @Test
    public void customActions_includeCorrectCornerLabels() {
        KeyboardData keyboard = buildSingleKeyKeyboardWithSwipe();
        KeyboardAccessibilityDelegate delegate =
            new KeyboardAccessibilityDelegate(new View(context), accessibilityHelper, Config.globalConfig());
        delegate.setKeyboardData(keyboard, 100f, 0f, 0f, 100f, 0f, 0f);

        AccessibilityNodeInfo node = delegate.createAccessibilityNodeInfo(0);
        boolean found = false;
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if ("Swipe up for b".equals(action.getLabel())) {
                found = true;
                break;
            }
        }
        assertTrue("Expected custom action label for swipe up to 'b'", found);
    }

    @Test
    public void modifierKeys_reportStateDescription() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);

        KeyboardData keyboard = buildModifierKeyboard();
        Keyboard2View view = new Keyboard2View(context, null);
        view.setKeyboard(keyboard);
        view.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        // Lock the shift key to set modifier state
        view.set_shift_state(true, true);

        KeyboardAccessibilityDelegate delegate =
            new KeyboardAccessibilityDelegate(view, accessibilityHelper, Config.globalConfig());
        delegate.setKeyboardData(keyboard, 100f, 0f, 0f, 100f, 0f, 0f);

        AccessibilityNodeInfo node = delegate.createAccessibilityNodeInfo(0);
        AccessibilityNodeInfoCompat compat = AccessibilityNodeInfoCompat.wrap(node);
        assertEquals("locked", compat.getStateDescription());
    }

    private void ensureConfigInitialized(Context ctx) {
        if (Config.globalConfig() != null) {
            return;
        }
        Config.initGlobalConfig(
            ctx.getSharedPreferences("a11y-test", Context.MODE_PRIVATE),
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

    private KeyboardData buildSingleKeyKeyboardWithSwipe() {
        KeyValue[] keyValues = new KeyValue[9];
        keyValues[0] = KeyValue.makeCharKey('a');
        keyValues[7] = KeyValue.makeCharKey('b'); // Up direction
        KeyboardData.Key key = new KeyboardData.Key(keyValues, null, 0, 1f, 0f, "");

        List<KeyboardData.Key> keys = new ArrayList<>();
        keys.add(key);
        KeyboardData.Row row = new KeyboardData.Row(keys, 1.0f, 0.0f);
        List<KeyboardData.Row> rows = new ArrayList<>();
        rows.add(row);
        return new KeyboardData(rows, 1f, null, null, null, "test", false, false, false);
    }

    private KeyboardData buildModifierKeyboard() {
        KeyValue[] keyValues = new KeyValue[9];
        keyValues[0] = KeyValue.getKeyByName("shift");
        KeyboardData.Key key = new KeyboardData.Key(keyValues, null, 0, 1f, 0f, "");

        List<KeyboardData.Key> keys = new ArrayList<>();
        keys.add(key);
        KeyboardData.Row row = new KeyboardData.Row(keys, 1.0f, 0.0f);
        List<KeyboardData.Row> rows = new ArrayList<>();
        rows.add(row);
        return new KeyboardData(rows, 1f, null, null, null, "shift-test", false, false, false);
    }
}
