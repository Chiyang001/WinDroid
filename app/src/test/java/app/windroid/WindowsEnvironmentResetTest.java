package app.windroid;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

public class WindowsEnvironmentResetTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void deletesGuestFilesAndCanBeRetried() throws IOException {
        Path files = temporary.newFolder("app-files").toPath();
        Path drive = Files.createDirectories(files.resolve("rootfs/home/xuser-1/.wine/drive_c"));
        Files.createFile(drive.resolve("document.txt"));
        Path legacy = Files.createDirectories(files.resolve("imagefs/home"));
        Files.createFile(legacy.resolve("old-prefix"));
        Path components = Files.createDirectory(files.resolve("installed_components"));
        Files.createFile(components.resolve("component"));
        Path appFile = Files.createFile(files.resolve("unrelated.txt"));

        WindowsEnvironment.deleteVirtualMachineFiles(files.toFile());
        WindowsEnvironment.deleteVirtualMachineFiles(files.toFile());

        assertFalse(Files.exists(files.resolve("rootfs")));
        assertFalse(Files.exists(files.resolve("imagefs")));
        assertFalse(Files.exists(components));
        assertTrue(Files.isRegularFile(appFile));
    }

    @Test
    public void leavesFilesReachedThroughWineDriveLinksAlone() throws IOException {
        Path files = temporary.newFolder("app-files").toPath();
        Path hostFolder = temporary.newFolder("phone-files").toPath();
        Path document = Files.createFile(hostFolder.resolve("document.txt"));
        Path mappings = Files.createDirectories(files.resolve("rootfs/home/xuser-1/.wine/dosdevices"));
        Path mappedDrive = mappings.resolve("mapped-drive");
        try {
            Files.createSymbolicLink(mappedDrive, hostFolder);
        }
        catch (IOException | UnsupportedOperationException error) {
            assumeNoException(error);
        }

        WindowsEnvironment.deleteVirtualMachineFiles(files.toFile());

        assertFalse(Files.exists(mappedDrive, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(document));
    }
}
