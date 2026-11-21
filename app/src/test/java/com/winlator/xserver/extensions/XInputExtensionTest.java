package com.winlator.xserver.extensions;

import com.winlator.xserver.Atom;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class XInputExtensionTest {
    @Test
    public void listInputDevicesEncodesCoreDevicesAndCountedNames() {
        ByteBuffer reply = ByteBuffer.wrap(XInputExtension.encodeListInputDevicesReply((short)0x1234))
                .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, Byte.toUnsignedInt(reply.get()));
        assertEquals(2, Byte.toUnsignedInt(reply.get()));
        assertEquals(0x1234, Short.toUnsignedInt(reply.getShort()));
        assertEquals(reply.capacity(), 32 + reply.getInt() * 4);
        assertEquals(2, Byte.toUnsignedInt(reply.get()));
        reply.position(32);

        assertEquals("MOUSE", Atom.getName(reply.getInt()));
        assertEquals(2, Byte.toUnsignedInt(reply.get())); // Device ID.
        assertEquals(2, Byte.toUnsignedInt(reply.get())); // Class count.
        assertEquals(0, Byte.toUnsignedInt(reply.get())); // IsXPointer.
        assertEquals(0, Byte.toUnsignedInt(reply.get())); // Attached device.
        assertEquals("KEYBOARD", Atom.getName(reply.getInt()));
        assertEquals(3, Byte.toUnsignedInt(reply.get()));
        assertEquals(1, Byte.toUnsignedInt(reply.get()));
        assertEquals(1, Byte.toUnsignedInt(reply.get())); // IsXKeyboard.
        assertEquals(0, Byte.toUnsignedInt(reply.get()));

        assertEquals(1, Byte.toUnsignedInt(reply.get())); // Button class.
        assertEquals(4, Byte.toUnsignedInt(reply.get()));
        assertEquals(3, Short.toUnsignedInt(reply.getShort()));
        assertEquals(2, Byte.toUnsignedInt(reply.get())); // Valuator class.
        assertEquals(32, Byte.toUnsignedInt(reply.get()));
        assertEquals(2, Byte.toUnsignedInt(reply.get()));
        assertEquals(0, Byte.toUnsignedInt(reply.get())); // Relative mode.
        assertEquals(0, reply.getInt()); // Motion history size.
        for (int axis = 0; axis < 2; axis++) {
            assertEquals(0, reply.getInt());
            assertEquals(-1, reply.getInt());
            assertEquals(-1, reply.getInt());
        }
        assertEquals(0, Byte.toUnsignedInt(reply.get())); // Key class.
        assertEquals(8, Byte.toUnsignedInt(reply.get()));
        assertEquals(8, Byte.toUnsignedInt(reply.get()));
        assertEquals(255, Byte.toUnsignedInt(reply.get()));
        assertEquals(248, Short.toUnsignedInt(reply.getShort()));
        assertEquals(0, Short.toUnsignedInt(reply.getShort()));

        assertEquals("Virtual Core Pointer", readName(reply));
        assertEquals("Virtual Core Keyboard", readName(reply));
        assertEquals(1, reply.remaining());
        assertEquals(0, Byte.toUnsignedInt(reply.get()));
    }

    private static String readName(ByteBuffer reply) {
        byte[] name = new byte[Byte.toUnsignedInt(reply.get())];
        reply.get(name);
        return new String(name, StandardCharsets.US_ASCII);
    }
}
