package app.windroid;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WineRuntimePackageTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void useOfflineInstallerToEnableAndProductCodeToDisableWineAddon() {
        assertTrue(WineRuntimePackage.MONO.command(true).contains("/i Z:\\opt\\windroid-runtime\\wine-mono-10.1.0-x86.msi"));
        assertTrue(WineRuntimePackage.MONO.command(false).contains("/x {54ED3032-F8CB-5444-83B3-F51992D3EDC1}"));
        assertNotEquals(WineRuntimePackage.GECKO_X86.command(false), WineRuntimePackage.GECKO_X64.command(false));
        assertTrue(WineRuntimePackage.GECKO_X86.command(false).contains("\\syswow64\\msiexec.exe"));
        assertTrue(WineRuntimePackage.GECKO_X64.command(true).contains("REINSTALLMODE=amus"));
    }

    @Test
    public void useVendorUninstallOptionForSelectableMicrosoftPackages() {
        assertTrue(WineRuntimePackage.VC14_X64.command(true).endsWith("--vc14-x64 install"));
        assertTrue(WineRuntimePackage.VC14_X64.command(false).endsWith("--vc14-x64 remove"));
        assertTrue(WineRuntimePackage.VC14_X86.command(true).endsWith("--vc14-x86 install"));
        assertTrue(WineRuntimePackage.DESKTOP_X86.command(false).endsWith(" /uninstall /quiet /norestart"));
    }

    @Test
    public void leaveFrameworkAndDirectXInTheOneShotInstallerList() {
        assertFalse(WineRuntimePackage.FRAMEWORK.selectable);
        assertTrue(WineRuntimePackage.FRAMEWORK.command(true).endsWith("--framework48 install"));
        assertFalse(WineRuntimePackage.DIRECTX.selectable);
        assertTrue(WineRuntimePackage.WEBVIEW.selectable);
    }

    @Test
    public void rejectRuntimeZipOutsideItsDirectoryWithoutReplacingCurrentFiles() throws Exception {
        File root = folder.newFolder("runtime").getCanonicalFile();
        File target = new File(root, "installed");
        assertTrue(target.mkdir());
        File installed = new File(target, "browser.exe");
        Files.createFile(installed.toPath());
        File archive = new File(root, "installer.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            zip.putNextEntry(new ZipEntry("../foreign.dll"));
            zip.write(1);
        }
        assertThrows(IOException.class, () -> RuntimeArchive.deploy(archive, target, 1, 1, null));
        assertTrue(installed.isFile());
        assertFalse(new File(root, "foreign.dll").exists());
    }

    @Test
    public void disablingOneGeckoArchitectureLeavesTheOtherEnginePresent() throws Exception {
        File drive = folder.newFolder("drive_c").getCanonicalFile();
        File x86 = new File(drive, "windows/syswow64/gecko/2.47.4/wine_gecko/xul.dll");
        File x64 = new File(drive, "windows/system32/gecko/2.47.4/wine_gecko/xul.dll");
        for (File engine : new File[]{x86, x64}) {
            Files.createDirectories(engine.getParentFile().toPath());
            Files.createFile(engine.toPath());
        }
        assertTrue(WineRuntimePackage.GECKO_X86.record(null, drive, false));
        assertFalse(x86.exists());
        assertTrue(x64.isFile());
    }
}
