package com.winlator.core;

import com.winlator.win32.MSLink;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DesktopProgramLinksTest {
    @Rule public final TemporaryFolder files = new TemporaryFolder();
    private static final String PUBLIC = "users/Public/Desktop/";
    private static final String USER = "users/xuser/Desktop/";
    private static final String START_MENU = "ProgramData/Microsoft/Windows/Start Menu/Programs/";

    @Test public void mirrorsStartMenuAndKeepsExistingNativeLinkToSameProgram() throws IOException {
        File drive = files.newFolder("drive_c").getCanonicalFile();
        File nativeLink = link(drive, USER + "Existing.lnk", "C:\\Programs\\Existing.exe");
        File duplicate = link(drive, START_MENU + "Vendor/Second label.lnk", "c:\\programs\\existing.exe");
        File startMenuOnly = link(drive, START_MENU + "Vendor/Installed.lnk", "C:\\Programs\\Installed.exe");
        link(drive, START_MENU + "Vendor/Uninstall Installed.lnk", "C:\\Programs\\unins000.exe");

        DesktopProgramLinks.refresh(drive, Collections.emptyList(), Collections.emptySet());
        File displayed = new File(drive, PUBLIC + "Installed.lnk");
        assertArrayEquals(Files.readAllBytes(startMenuOnly.toPath()), Files.readAllBytes(displayed.toPath()));
        assertTrue(nativeLink.isFile());
        assertTrue(duplicate.isFile());
        assertFalse(new File(drive, PUBLIC + "Second label.lnk").exists());
        assertFalse(new File(drive, PUBLIC + "Uninstall Installed.lnk").exists());
        assertEquals(Collections.singleton(displayed), DesktopProgramLinks.managedShortcuts(drive));

        Files.delete(startMenuOnly.toPath());
        DesktopProgramLinks.refresh(drive, Collections.emptyList(), Collections.emptySet());
        assertFalse(displayed.exists());
        assertTrue(nativeLink.isFile());
    }

    @Test public void savesUnicodeProgramLinkAndPreservesAnEditedGeneratedLink() throws IOException {
        File drive = files.newFolder("drive_c").getCanonicalFile();
        String path = "Program Files/示例/工具.exe";
        File program = new File(drive, path);
        Files.createDirectories(program.getParentFile().toPath());
        Files.createFile(program.toPath());
        File original = link(drive, USER + "工具.lnk", "C:\\Programs\\Another.exe");

        DesktopProgramLinks.refresh(drive, Collections.singletonList(path), Collections.emptySet());
        File displayed = new File(drive, PUBLIC + "工具 (2).lnk");
        MSLink.LinkInfo parsed = MSLink.extractLinkInfo(displayed);
        assertNotNull(parsed);
        assertEquals("..\\..\\..\\Program Files\\示例\\工具.exe", parsed.relativePath);
        assertEquals("C:\\Program Files\\示例", parsed.workingDirectory);
        assertEquals("C:\\windows\\system32\\shell32.dll", parsed.iconLocation);
        assertEquals(-3, parsed.shellIconIndex);
        assertEquals(Collections.singleton(displayed), DesktopProgramLinks.managedShortcuts(drive));

        assertTrue(MSLink.createFile("C:\\Programs\\Changed.exe", displayed));
        DesktopProgramLinks.refresh(drive, Collections.emptyList(), Collections.emptySet());
        assertTrue(displayed.isFile());
        assertTrue(original.isFile());
        assertTrue(DesktopProgramLinks.managedShortcuts(drive).isEmpty());
    }

    @Test public void givesAnIconlessNativeShortcutAVisibleCopyWithoutEditingIt() throws IOException {
        File drive = files.newFolder("drive_c").getCanonicalFile();
        File program = new File(drive, "Programs/Example.exe");
        Files.createDirectories(program.getParentFile().toPath());
        Files.createFile(program.toPath());
        MSLink.LinkInfo info = new MSLink.LinkInfo();
        info.targetPath = "C:\\Programs\\Example.exe";
        info.arguments = "--from-shortcut";
        File original = new File(drive, USER + "Example.lnk");
        Files.createDirectories(original.getParentFile().toPath());
        assertTrue(MSLink.createFile(info, original));
        byte[] originalBytes = Files.readAllBytes(original.toPath());

        DesktopProgramLinks.refresh(drive, Collections.emptyList(), Collections.emptySet());

        File displayed = new File(drive, PUBLIC + "Example.lnk");
        MSLink.LinkInfo displayedInfo = MSLink.extractLinkInfo(displayed);
        assertNotNull(displayedInfo);
        assertEquals("..\\..\\..\\Programs\\Example.exe", displayedInfo.relativePath);
        assertEquals("--from-shortcut", displayedInfo.arguments);
        assertEquals(-3, displayedInfo.shellIconIndex);
        assertArrayEquals(originalBytes, Files.readAllBytes(original.toPath()));
    }

    @Test public void projectsTheChineseFileManagerByItsTarget() throws IOException {
        File drive = files.newFolder("drive_c").getCanonicalFile();
        File fileManager = link(drive, "ProgramData/Microsoft/Windows/Start Menu/系统工具/计算机.lnk",
                "C:\\windows\\wfm-zh.exe");

        DesktopProgramLinks.refresh(drive, Collections.emptyList(), Collections.singleton(fileManager));

        File desktopLink = new File(drive, PUBLIC + "计算机.lnk");
        assertArrayEquals(Files.readAllBytes(fileManager.toPath()), Files.readAllBytes(desktopLink.toPath()));
        assertEquals(Collections.singleton(desktopLink), DesktopProgramLinks.managedShortcuts(drive));
    }

    private File link(File drive, String path, String target) throws IOException {
        File file = new File(drive, path);
        Files.createDirectories(file.getParentFile().toPath());
        assertTrue(MSLink.createFile(target, file));
        return file;
    }
}
