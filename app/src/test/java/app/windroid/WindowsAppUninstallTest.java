package app.windroid;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WindowsAppUninstallTest {
    @Rule public final TemporaryFolder prefix = new TemporaryFolder();

    @Test public void matchesTheAppAndKeepsVendorDataRemovalArguments() throws IOException {
        File drive = prefix.newFolder("drive_c");
        createFiles(drive, "Program Files/Example App/Run.exe", "Program Files/Example App/Uninstall.exe");
        register(entry("Example", "\"DisplayIcon\"=\"C:\\\\Program Files\\\\Example App\\\\Run.exe,0\"",
                "\"UninstallString\"=\"\\\"C:\\\\Program Files\\\\Example App\\\\Uninstall.exe\\\" /RemoveData\""));

        WindowsAppUninstall request = WindowsAppUninstall.find(drive, "Program Files/Example App/Run.exe");
        assertNotNull(request);
        assertEquals("C:\\Program Files\\Example App\\Uninstall.exe\0C:\\Program Files\\Example App\0"
                + "\"C:\\Program Files\\Example App\\Uninstall.exe\" /RemoveData\0", request.payload);
    }

    @Test public void convertsAnMsiMaintenanceCommandToAnUninstall() throws IOException {
        File drive = prefix.newFolder("drive_c");
        createFiles(drive, "Program Files (x86)/Example/Run.exe");
        String product = "{12345678-1234-1234-1234-123456789ABC}";
        register(entry(product, "\"InstallLocation\"=str(2):\"%ProgramFiles(x86)%\\\\Example\"",
                "\"UninstallString\"=\"MsiExec.exe /I" + product + "\""));

        WindowsAppUninstall request = WindowsAppUninstall.find(drive, "Program Files (x86)/Example/Run.exe");
        assertNotNull(request);
        assertEquals("C:\\windows\\system32\\msiexec.exe\0C:\\windows\\system32\0"
                + "\"C:\\windows\\system32\\msiexec.exe\" /x " + product + " REBOOT=ReallySuppress\0", request.payload);
    }

    @Test public void matchesSevenZipFromMsiComponentWithoutAnIconOrInstallLocation() throws IOException {
        File drive = prefix.newFolder("drive_c");
        createFiles(drive, "Program Files/7-Zip/7zFM.exe", "Program Files/7-Zip/unlisted.exe");
        String product = "{23170F69-40C1-2702-2606-000001000000}";
        register("[Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Installer\\\\UserData\\\\"
                        + "S-1-5-18\\\\Components\\\\0123456789ABCDEF0123456789ABCDEF] 1\n"
                        + "\"96F071321C0420726260000010000000\"=\"C:\\\\Program Files\\\\7-Zip\\\\7zFM.exe\"",
                entry(product, "\"InstallLocation\"=\"\"",
                        "\"UninstallString\"=str(2):\"MsiExec.exe /I" + product + "\""));

        WindowsAppUninstall request = WindowsAppUninstall.find(drive, "Program Files/7-Zip/7zFM.exe");
        assertNotNull(request);
        assertEquals("C:\\windows\\system32\\msiexec.exe\0C:\\windows\\system32\0"
                + "\"C:\\windows\\system32\\msiexec.exe\" /x " + product + " REBOOT=ReallySuppress\0", request.payload);
        assertNull(WindowsAppUninstall.find(drive, "Program Files/7-Zip/unlisted.exe"));
    }

    @Test public void usesAQuietRegistrationWhenThereIsNoInteractiveUninstallCommand() throws IOException {
        File drive = prefix.newFolder("drive_c");
        createFiles(drive, "Tools/Test.exe", "Tools/Remove.exe");
        register(entry("Test", "\"DisplayIcon\"=\"C:\\\\Tools\\\\Test.exe\"",
                "\"QuietUninstallString\"=\"C:\\\\Tools\\\\Remove.exe /S\""));

        WindowsAppUninstall request = WindowsAppUninstall.find(drive, "Tools/Test.exe");
        assertNotNull(request);
        assertEquals("C:\\Tools\\Remove.exe\0C:\\Tools\0\"C:\\Tools\\Remove.exe\" /S\0", request.payload);
    }

    @Test public void rejectsAmbiguousMatchesAndSharedInstallDirectories() throws IOException {
        File drive = prefix.newFolder("drive_c");
        createFiles(drive, "Program Files/Example/Run.exe", "Program Files/Example/Uninstall.exe",
                "Program Files/Example/OtherUninstall.exe", "Program Files/Portable.exe");
        String icon = "\"DisplayIcon\"=\"C:\\\\Program Files\\\\Example\\\\Run.exe\"";
        register(entry("First", icon, "\"UninstallString\"=\"C:\\\\Program Files\\\\Example\\\\Uninstall.exe\""),
                entry("Second", icon, "\"UninstallString\"=\"C:\\\\Program Files\\\\Example\\\\OtherUninstall.exe\""),
                entry("Broad", "\"InstallLocation\"=\"C:\\\\Program Files\"",
                        "\"UninstallString\"=\"C:\\\\Program Files\\\\Example\\\\Uninstall.exe\""));

        assertNull(WindowsAppUninstall.find(drive, "Program Files/Example/Run.exe"));
        assertNull(WindowsAppUninstall.find(drive, "Program Files/Portable.exe"));
    }

    @Test public void reservesSharedFoldersAndAllowsDedicatedPortableFolders() {
        assertFalse(WindowsEnvironment.isRemovableProgramFolder("Program Files (x86)/Common Files"));
        assertFalse(WindowsEnvironment.isRemovableProgramFolder("users/xuser/AppData/Local"));
        assertFalse(WindowsEnvironment.isRemovableProgramFolder("users/xuser/Downloads"));
        assertTrue(WindowsEnvironment.isRemovableProgramFolder("users/xuser/Downloads/My Portable App"));
    }

    @Test public void limitsPortableDeletionToTheConfirmedScope() throws Exception {
        File drive = prefix.newFolder("drive_c").getCanonicalFile();
        String folder = "Downloads/Portable App";
        String program = folder + "/App.exe";
        String data = folder + "/data/settings.ini";
        String other = "Downloads/Other.exe";
        createFiles(drive, program, data, other);
        Constructor<WindowsEnvironment> constructor = WindowsEnvironment.class.getDeclaredConstructor(Context.class, File.class);
        constructor.setAccessible(true);
        WindowsEnvironment environment = constructor.newInstance(null, drive);

        environment.removeProgramFiles(program, null);
        assertFalse(new File(drive, program).exists());
        assertTrue(new File(drive, data).exists());

        createFiles(drive, program);
        environment.removeProgramFiles(program, folder);
        assertFalse(new File(drive, folder).exists());
        assertTrue(new File(drive, other).exists());
    }

    private void register(String... entries) throws IOException {
        File hive = prefix.newFile("system.reg");
        Files.write(hive.toPath(), ("WINE REGISTRY Version 2\n" + String.join("\n", entries)).getBytes(StandardCharsets.UTF_8));
    }

    private static String entry(String key, String... fields) {
        return "[Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Uninstall\\\\" + key + "] 1\n"
                + "\"DisplayName\"=\"Example\"\n" + String.join("\n", fields) + "\n";
    }

    private static void createFiles(File drive, String... paths) throws IOException {
        for (String path : paths) {
            File file = new File(drive, path);
            Files.createDirectories(file.getParentFile().toPath());
            Files.createFile(file.toPath());
        }
    }
}
