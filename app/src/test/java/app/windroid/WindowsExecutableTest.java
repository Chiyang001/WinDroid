package app.windroid;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WindowsExecutableTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void accepts32And64BitWindowsExecutables() throws IOException {
        WindowsExecutable.validate(write(image(0x014c, 0x10b, 0x0002)));
        WindowsExecutable.validate(write(image(0x8664, 0x20b, 0x0022)));
    }

    @Test public void acceptsUppercaseExtensionAndUnicodeFilename() {
        assertTrue(WindowsExecutable.hasExeExtension("中文程序 v2.EXE"));
        assertFalse(WindowsExecutable.hasExeExtension("program.exe.txt"));
        assertFalse(WindowsExecutable.hasExeExtension(".exe"));
    }

    @Test public void rejectsArm64WithoutReportingCorruption() throws IOException {
        File arm = write(image(0xaa64, 0x20b, 0x0022));
        IOException error = assertThrows(IOException.class, () -> WindowsExecutable.validate(arm));
        assertTrue(error.getMessage().contains("架构暂不支持"));
    }

    @Test public void rejectsDllsDriversAndWrongPeFormats() throws IOException {
        File dll = write(image(0x8664, 0x20b, 0x2002));
        File driver = write(image(0x8664, 0x20b, 0x1002));
        File mismatched = write(image(0x014c, 0x20b, 0x0022));
        assertThrows(IOException.class, () -> WindowsExecutable.validate(dll));
        assertThrows(IOException.class, () -> WindowsExecutable.validate(driver));
        assertThrows(IOException.class, () -> WindowsExecutable.validate(mismatched));
    }

    @Test public void rejectsTruncatedSectionsAndInvalidHeaderOffset() throws IOException {
        byte[] executable = image(0x8664, 0x20b, 0x0022);
        File truncated = write(Arrays.copyOf(executable, 600));
        ByteBuffer.wrap(executable).order(ByteOrder.LITTLE_ENDIAN).putInt(0x3c, -1);
        File invalidOffset = write(executable);
        File notPe = write(new byte[10]);
        assertThrows(IOException.class, () -> WindowsExecutable.validate(truncated));
        assertThrows(IOException.class, () -> WindowsExecutable.validate(invalidOffset));
        assertThrows(IOException.class, () -> WindowsExecutable.validate(notPe));
    }

    private static byte[] image(int machine, int magic, int characteristics) {
        ByteBuffer bytes = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        int pe = 128;
        int optionalSize = magic == 0x20b ? 112 : 96;
        int section = pe + 24 + optionalSize;
        bytes.putShort(0, (short)0x5a4d).putInt(0x3c, pe);
        bytes.putInt(pe, 0x00004550).putShort(pe + 4, (short)machine).putShort(pe + 6, (short)1);
        bytes.putShort(pe + 20, (short)optionalSize).putShort(pe + 22, (short)characteristics);
        bytes.putShort(pe + 24, (short)magic);
        bytes.putInt(section + 16, 512).putInt(section + 20, 512);
        return bytes.array();
    }

    private File write(byte[] contents) throws IOException {
        File file = folder.newFile();
        Files.write(file.toPath(), contents);
        return file;
    }
}
