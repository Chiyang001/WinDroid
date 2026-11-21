package app.windroid;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;

public class WindowsRegistryAppsTest {
    private static final String VERSION_KEY = "Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\";
    @Rule public final TemporaryFolder prefix = new TemporaryFolder();

    @Test public void recognizesWineIconPathsAndAppPathsAcrossHives() throws IOException {
        write("system.reg", "[" + VERSION_KEY + "Uninstall\\\\QQ] 1780000000",
                "\"DisplayName\"=\"QQ\"",
                "\"DisplayIcon\"=\"C:\\\\Program Files\\\\Tencent\\\\QQNT\\\\QQ.exe,0\"",
                "[Software\\\\Wow6432Node\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Uninstall\\\\Example] 1780000001",
                "\"DisplayIcon\"=str(2):\"\\\"%ProgramFiles(x86)%\\\\\\x6587\\x6863\\\\App.exe\\\", -2\"",
                "\"DisplayName\"=\"示例\"");
        write("user.reg", "[" + VERSION_KEY + "App Paths\\\\Browser.EXE] 1780000010",
                "@=\"C:/Program Files/Example/Browser.exe\"",
                "\"Path\"=\"C:\\\\Program Files\\\\Example\"");

        assertEquals(new LinkedHashSet<>(Arrays.asList(
                "C:\\Program Files\\Tencent\\QQNT\\QQ.exe",
                "%ProgramFiles(x86)%\\文档\\App.exe",
                "C:/Program Files/Example/Browser.exe")),
                WindowsRegistryApps.executablePaths(prefix.getRoot()));
    }

    @Test public void ignoresHiddenComponentsUninstallCommandsAndUnrecognizedRows() throws IOException {
        write("system.reg", "[" + VERSION_KEY + "Uninstall\\\\Hidden] 1",
                "\"DisplayName\"=\"System component\"",
                "\"DisplayIcon\"=\"C:\\\\Tools\\\\Hidden.exe,0\"",
                "\"SystemComponent\"=dword:00000001",
                "[" + VERSION_KEY + "Uninstall\\\\OnlyCommand] 1",
                "\"DisplayName\"=\"Example\"",
                "\"UninstallString\"=\"C:\\\\Tools\\\\remove.exe\"",
                "[" + VERSION_KEY + "Uninstall\\\\OnlyIcon] 1",
                "\"DisplayIcon\"=\"C:\\\\Tools\\\\no-name.exe\"",
                "[" + VERSION_KEY + "Uninstall\\\\Patch] 1",
                "\"DisplayName\"=\"Update\"",
                "\"DisplayIcon\"=\"C:\\\\Tools\\\\update.exe\"",
                "\"ReleaseType\"=\"Update\"",
                "[" + VERSION_KEY + "Uninstall\\\\Remote] 1",
                "\"DisplayName\"=\"Remote\"",
                "\"DisplayIcon\"=\"Z:\\\\Tools\\\\external.exe\"",
                "[" + VERSION_KEY + "App Paths\\\\Alias.exe] 1",
                "@=\"C:\\\\Tools\\\\unrelated.exe\"",
                "[" + VERSION_KEY + "App Paths\\\\Script.exe] 1",
                "@=\"C:\\\\Tools\\\\Script.exe --run\"",
                "[" + VERSION_KEY + "App Paths\\\\Relative.exe] 1",
                "@=\"Tools\\\\Relative.exe\"",
                "[Software\\\\Example\\\\Uninstall\\\\Entry] 1",
                "\"DisplayName\"=\"Unrelated\"",
                "\"DisplayIcon\"=\"C:\\\\Tools\\\\Unrelated.exe\"");

        assertEquals(Collections.emptySet(), WindowsRegistryApps.executablePaths(prefix.getRoot()));
    }

    private void write(String name, String... lines) throws IOException {
        File file = prefix.newFile(name);
        String contents = "WINE REGISTRY Version 2\n#arch=win64\n" + String.join("\n", lines) + "\n";
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    }
}
