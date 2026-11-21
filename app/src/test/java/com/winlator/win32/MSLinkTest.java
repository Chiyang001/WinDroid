package com.winlator.win32;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MSLinkTest {
    @Rule public final TemporaryFolder resources = new TemporaryFolder();

    @Test public void readsExistingWinlatorShortcut() throws IOException {
        File shortcut = resources.newFile("winlator.lnk");
        MSLink.LinkInfo definition = new MSLink.LinkInfo();
        definition.targetPath = "C:\\Program Files\\Example\\Example.exe";
        definition.iconLocation = "C:\\Program Files\\Example\\Example.dll";
        definition.iconIndex = -42;
        definition.arguments = "--test";
        assertTrue(MSLink.createFile(definition, shortcut));

        MSLink.LinkInfo parsed = MSLink.extractLinkInfo(shortcut);
        assertNotNull(parsed);
        assertEquals(definition.targetPath, parsed.targetPath);
        assertEquals(definition.iconLocation, parsed.iconLocation);
        assertEquals(definition.arguments, parsed.arguments);
        assertEquals(-42, parsed.shellIconIndex);
    }

    @Test public void readsUnicodeTargetAndIconLocation() throws IOException {
        String basePath = "C:\\Program Files\\软件";
        String iconLocation = "C:\\Program Files\\软件\\图标.ico";
        byte[] base = (basePath + "\0").getBytes(StandardCharsets.UTF_16LE);
        byte[] suffix = "程序.exe\0".getBytes(StandardCharsets.UTF_16LE);
        byte[] icon = iconLocation.getBytes(StandardCharsets.UTF_16LE);
        int infoSize = 36 + base.length + suffix.length;
        ByteBuffer bytes = ByteBuffer.allocate(76 + infoSize + 2 + icon.length).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0, 76).putInt(0x14, 0xc2).putInt(0x38, 2);
        bytes.position(76);
        bytes.putInt(infoSize).putInt(36).putInt(1).putInt(0).putInt(0).putInt(0).putInt(0);
        bytes.putInt(36).putInt(36 + base.length).put(base).put(suffix);
        bytes.putShort((short)iconLocation.length()).put(icon);
        File shortcut = resources.newFile("unicode.lnk");
        Files.write(shortcut.toPath(), bytes.array());

        MSLink.LinkInfo parsed = MSLink.extractLinkInfo(shortcut);
        assertNotNull(parsed);
        assertEquals(basePath + "\\程序.exe", parsed.targetPath);
        assertEquals(iconLocation, parsed.iconLocation);
        assertEquals(2, parsed.shellIconIndex);
    }

    @Test public void rejectsTruncatedLinkInfo() throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(88).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0, 76).putInt(0x14, 2).putInt(76, Integer.MAX_VALUE).putInt(80, 36).putInt(84, 1);
        File shortcut = resources.newFile("broken.lnk");
        Files.write(shortcut.toPath(), bytes.array());
        assertNull(MSLink.extractLinkInfo(shortcut));
    }
}
