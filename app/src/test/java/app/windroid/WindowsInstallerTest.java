package app.windroid;

import com.winlator.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WindowsInstallerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void recognizesOnlyMsiExtension() {
        assertTrue(WindowsInstaller.hasMsiExtension("Setup x64.MSI"));
        assertFalse(WindowsInstaller.hasMsiExtension("Setup.msi.exe"));
        assertFalse(WindowsInstaller.hasMsiExtension(".msi"));
    }

    @Test
    public void acceptsBothCompoundFileSectorSizes() throws Exception {
        WindowsInstaller.validateMsi(writeStorage(3, 9, 1024));
        WindowsInstaller.validateMsi(writeStorage(4, 12, 8192));
    }

    @Test
    public void rejectsTruncatedOrInconsistentStorage() throws Exception {
        for (File file : new File[] {writeStorage(3, 9, 1023), writeStorage(3, 12, 8192), temporary.newFile()}) {
            WinDroidException error = assertThrows(WinDroidException.class, () -> WindowsInstaller.validateMsi(file));
            assertEquals(R.string.wd_error_msi_invalid, error.messageId);
        }
    }

    private File writeStorage(int version, int sectorShift, int length) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(length);
        header.putLong(0xd0cf11e0a1b11ae1L);
        header.order(ByteOrder.LITTLE_ENDIAN).position(26);
        header.putShort((short)version).putShort((short)0xfffe).putShort((short)sectorShift).putShort((short)6);
        File file = temporary.newFile();
        Files.write(file.toPath(), header.array());
        return file;
    }
}
