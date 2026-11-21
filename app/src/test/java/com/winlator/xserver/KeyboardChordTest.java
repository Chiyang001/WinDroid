package com.winlator.xserver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.winlator.xserver.XKeycode.KEY_ALT_L;
import static com.winlator.xserver.XKeycode.KEY_C;
import static com.winlator.xserver.XKeycode.KEY_CTRL_L;
import static com.winlator.xserver.XKeycode.KEY_CTRL_R;
import static com.winlator.xserver.XKeycode.KEY_DEL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeyboardChordTest {
    private final Keyboard keyboard = Keyboard.createKeyboard(null);
    private final List<Integer> events = new ArrayList<>();

    public KeyboardChordTest() {
        keyboard.addOnKeyboardListener(new Keyboard.OnKeyboardListener() {
            @Override
            public void onKeyPress(byte keycode, int keysym) {
                events.add((int) keycode);
            }

            @Override
            public void onKeyRelease(byte keycode) {
                events.add(-keycode);
            }
        });
    }

    @Test
    public void pressesModifiersBeforeDeleteAndReleasesInReverseOrder() {
        keyboard.tapKeyChord(KEY_DEL, KEY_CTRL_L, KEY_ALT_L);

        assertEquals(Arrays.asList((int) KEY_CTRL_L.id, (int) KEY_ALT_L.id, (int) KEY_DEL.id,
                -KEY_DEL.id, -KEY_ALT_L.id, -KEY_CTRL_L.id), events);
        assertTrue(keyboard.getModifiersMask().isEmpty());
    }

    @Test
    public void leavesPhysicallyHeldControlPressed() {
        keyboard.setKeyPress(KEY_CTRL_R.id, 0);
        keyboard.tapKeyChord(KEY_C, KEY_CTRL_L);

        assertEquals(Arrays.asList((int) KEY_CTRL_R.id, (int) KEY_C.id, -KEY_C.id), events);
        assertTrue(keyboard.getModifiersMask().isSet(Keyboard.getModifierFlag(KEY_CTRL_R.id)));
    }
}
