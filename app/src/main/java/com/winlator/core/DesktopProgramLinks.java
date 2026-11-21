package com.winlator.core;

import com.winlator.win32.MSLink;
import com.winlator.win32.PEParser;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class DesktopProgramLinks {
    private static final String USER_DESKTOP = "users/" + RootFS.USER + "/Desktop";
    private static final String PUBLIC_DESKTOP = "users/Public/Desktop";
    private static final String USER_START_MENU =
            "users/" + RootFS.USER + "/AppData/Roaming/Microsoft/Windows/Start Menu/Programs";
    private static final String PUBLIC_START_MENU = "ProgramData/Microsoft/Windows/Start Menu/Programs";
    private static final String LEDGER = ".windroid-desktop-links";
    private static final String GENERIC_PROGRAM_ICON = "C:\\windows\\system32\\shell32.dll";
    private static final int GENERIC_PROGRAM_ICON_ID = -3; // IDI_SHELL_WINDOW from Wine's shell32.
    private static final long MAX_LINK_SIZE = 1024 * 1024;

    private DesktopProgramLinks() {}

    public static void refresh(File drive, List<String> library, Set<File> builtInLinks) throws IOException {
        if (!drive.isDirectory() || !drive.equals(drive.getCanonicalFile())) return;
        File desktop = new File(drive, PUBLIC_DESKTOP);
        if (!desktop.equals(desktop.getCanonicalFile())) return;
        Files.createDirectories(desktop.toPath());

        Set<File> systemLinks = new HashSet<>();
        for (File link : builtInLinks) systemLinks.add(link.getCanonicalFile());
        Set<File> generated = managedShortcuts(drive);
        Set<String> usedNames = new HashSet<>();
        Set<String> knownTargets = new HashSet<>();
        Map<File, byte[]> desired = new LinkedHashMap<>();
        Map<File, MSLink.LinkInfo> nativeFallbacks = new LinkedHashMap<>();
        for (File folder : new File[]{desktop, new File(drive, USER_DESKTOP)}) {
            for (File file : children(folder)) {
                if (generated.contains(file)) continue;
                if (isLink(file)) {
                    MSLink.LinkInfo info = MSLink.extractLinkInfo(file);
                    File target = linkTarget(drive, file, info);
                    if (needsFallbackIcon(drive, file, info, target)) {
                        setRelativePaths(drive, desktop, target, info);
                        setGenericIcon(info);
                        nativeFallbacks.put(file, info);
                        if (!folder.equals(desktop)) continue;
                    }
                    else knownTargets.add(signature(drive, file, info));
                }
                usedNames.add(key(file.getName()));
            }
        }
        for (Map.Entry<File, MSLink.LinkInfo> entry : nativeFallbacks.entrySet()) {
            File nativeLink = entry.getKey();
            MSLink.LinkInfo info = entry.getValue();
            // The relative path has been rebased to the public desktop.
            String target = signature(drive, new File(desktop, nativeLink.getName()), info);
            if (knownTargets.add(target)) {
                addLink(desktop, nativeLink.getName(), MSLink.relativeProgramShortcut(info), usedNames, desired);
            }
        }
        for (String location : new String[]{USER_START_MENU, PUBLIC_START_MENU}) {
            scanStartMenu(drive, new File(drive, location), desktop, systemLinks, usedNames, knownTargets, desired);
        }
        if (library != null) {
            for (String path : library) {
                if (path == null || path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0) continue;
                File file = new File(drive, path);
                if (!insideDrive(drive, file) || !file.isFile()) continue;
                addProgram(drive, desktop, file, usedNames, knownTargets, desired);
            }
        }
        for (File tool : systemLinks) {
            MSLink.LinkInfo info = MSLink.extractLinkInfo(tool);
            if (info == null || info.targetPath == null) continue;
            String target = key(info.targetPath.replace('/', '\\'));
            if (target.equals("c:\\windows\\wfm.exe") || target.equals("c:\\windows\\wfm-zh.exe")
                    || target.equals("c:\\windows\\notepad.exe")) {
                addProgram(drive, desktop, tool, usedNames, knownTargets, desired);
            }
        }

        // Remember the exact generated contents before publishing so an interrupted refresh can be retried.
        Properties ledger = new Properties();
        for (Map.Entry<File, byte[]> entry : desired.entrySet()) {
            ledger.setProperty(entry.getKey().getName(), digest(entry.getValue()));
        }
        Set<File> retained = new HashSet<>();
        for (File old : generated) {
            byte[] replacement = desired.get(old);
            if (replacement != null && Arrays.equals(replacement, Files.readAllBytes(old.toPath()))) retained.add(old);
            else Files.deleteIfExists(old.toPath());
        }
        saveLedger(drive, ledger);
        for (Map.Entry<File, byte[]> entry : desired.entrySet()) {
            if (retained.contains(entry.getKey())) continue;
            Path temp = Files.createTempFile(drive.getParentFile().toPath(), LEDGER, ".lnk.tmp");
            try {
                Files.write(temp, entry.getValue());
                Files.move(temp, entry.getKey().toPath());
            }
            catch (FileAlreadyExistsException ignored) {
                // A shortcut added since the scan belongs to the user.
            }
            finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static Set<File> managedShortcuts(File drive) {
        Set<File> matches = new HashSet<>();
        File ledgerFile = new File(drive.getParentFile(), LEDGER);
        File desktop = new File(drive, PUBLIC_DESKTOP);
        if (!ledgerFile.isFile()) return matches;
        Properties ledger = new Properties();
        try {
            if (!ledgerFile.equals(ledgerFile.getCanonicalFile()) || !insideDrive(drive, desktop)) return matches;
            try (InputStream input = Files.newInputStream(ledgerFile.toPath())) {
                ledger.load(input);
            }
            for (File file : children(desktop)) {
                String hash = ledger.getProperty(file.getName());
                if (hash != null && isLink(file) && hash.equals(digest(Files.readAllBytes(file.toPath())))) {
                    matches.add(file);
                }
            }
        }
        catch (IOException | IllegalArgumentException ignored) {
            // Unknown ownership leaves the desktop shortcuts intact.
        }
        return matches;
    }

    private static void saveLedger(File drive, Properties ledger) throws IOException {
        Path temp = Files.createTempFile(drive.getParentFile().toPath(), LEDGER, ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp)) {
                ledger.store(output, null);
            }
            Files.move(temp, new File(drive.getParentFile(), LEDGER).toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void scanStartMenu(File drive, File folder, File desktop, Set<File> builtInLinks,
                                      Set<String> names, Set<String> targets, Map<File, byte[]> desired) throws IOException {
        for (File file : children(folder)) {
            String name = key(file.getName());
            if (file.isDirectory() && !name.equals("startup")) {
                scanStartMenu(drive, file, desktop, builtInLinks, names, targets, desired);
            }
            else if (isLink(file) && !builtInLinks.contains(file) && !isUninstaller(name)) {
                addProgram(drive, desktop, file, names, targets, desired);
            }
        }
    }

    private static void addProgram(File drive, File desktop, File file, Set<String> names, Set<String> targets,
                                   Map<File, byte[]> desired) throws IOException {
        String name = file.getName();
        byte[] contents;
        String signature;
        if (isLink(file)) {
            if (file.getParentFile().equals(desktop) || file.getParentFile().equals(new File(drive, USER_DESKTOP))) return;
            MSLink.LinkInfo info = MSLink.extractLinkInfo(file);
            signature = signature(drive, file, info);
            if (targets.contains(signature)) return;
            File target = linkTarget(drive, file, info);
            boolean genericIcon = needsFallbackIcon(drive, file, info, target);
            if (target != null && (info.targetPath == null || genericIcon)) {
                setRelativePaths(drive, desktop, target, info);
                if (genericIcon) setGenericIcon(info);
                contents = MSLink.relativeProgramShortcut(info);
            }
            else contents = Files.readAllBytes(file.toPath());
        }
        else if (key(name).endsWith(".exe")) {
            signature = key(dosPath(drive, file)) + "\0";
            if (targets.contains(signature)) return;
            MSLink.LinkInfo info = new MSLink.LinkInfo();
            setRelativePaths(drive, desktop, file, info);
            if (!PEParser.hasShellIcon(file, 0)) setGenericIcon(info);
            contents = MSLink.relativeProgramShortcut(info);
        }
        else return;

        addLink(desktop, name, contents, names, desired);
        targets.add(signature);
    }

    private static void addLink(File desktop, String name, byte[] contents, Set<String> names,
                                Map<File, byte[]> desired) {
        String stem = name.substring(0, name.length() - 4);
        // Leave room for a collision suffix and Windows pathname limits.
        int limit = Math.min(stem.codePointCount(0, stem.length()), 60);
        stem = stem.substring(0, stem.offsetByCodePoints(0, limit));
        String destination = stem + ".lnk";
        for (int index = 2; !names.add(key(destination)); index++) destination = stem + " (" + index + ").lnk";
        desired.put(new File(desktop, destination), contents);
    }

    private static void setRelativePaths(File drive, File desktop, File target, MSLink.LinkInfo info) {
        info.relativePath = desktop.toPath().relativize(target.toPath()).toString().replace('/', '\\');
        if (info.workingDirectory == null) info.workingDirectory = dosPath(drive, target.getParentFile());
        if (info.iconLocation == null) info.iconLocation = dosPath(drive, target);
    }

    private static File linkTarget(File drive, File file, MSLink.LinkInfo info) throws IOException {
        if (info == null) return null;
        File target = resolveWindowsFile(drive, file.getParentFile(), info.targetPath);
        return target != null ? target : resolveWindowsFile(drive, file.getParentFile(), info.relativePath);
    }

    private static File resolveWindowsFile(File drive, File parent, String value) throws IOException {
        if (value == null || value.indexOf('\0') >= 0) return null;
        String path = value.replace('\\', '/');
        if (path.regionMatches(true, 0, "C:/", 0, 3)) {
            parent = drive;
            path = path.substring(3);
        }
        if (path.startsWith("/") || path.indexOf(':') >= 0 || path.indexOf('%') >= 0) return null;
        Path expected = parent.toPath().resolve(path).normalize();
        Path root = drive.toPath();
        if (!expected.startsWith(root)) return null;
        File current = drive;
        for (Path component : root.relativize(expected)) {
            String name = component.toString();
            File file = new File(current, name);
            if (!file.exists()) {
                File[] alternatives = current.listFiles((directory, entry) -> entry.equalsIgnoreCase(name));
                if (alternatives == null || alternatives.length != 1) return null;
                file = alternatives[0];
            }
            if (Files.isSymbolicLink(file.toPath())) return null;
            current = file;
        }
        return current.isFile() ? current : null;
    }

    private static boolean needsFallbackIcon(File drive, File shortcut, MSLink.LinkInfo info, File target)
            throws IOException {
        if (target == null || info.isDirectory || !key(target.getName()).endsWith(".exe")) return false;
        int index = info.shellIconIndex;
        if (PEParser.hasShellIcon(target, index)) return false;
        if (info.iconLocation == null) return true;
        File icon = resolveWindowsFile(drive, shortcut.getParentFile(), info.iconLocation);
        if (icon == null) return false;
        if (!key(icon.getName()).endsWith(".ico")) return !PEParser.hasShellIcon(icon, index);
        try (DataInputStream input = new DataInputStream(Files.newInputStream(icon.toPath()))) {
            return icon.length() < 22 || input.readInt() != 0x00000100 || input.readUnsignedShort() == 0;
        }
    }

    private static void setGenericIcon(MSLink.LinkInfo info) {
        info.iconLocation = GENERIC_PROGRAM_ICON;
        info.shellIconIndex = GENERIC_PROGRAM_ICON_ID;
    }

    private static String signature(File drive, File file, MSLink.LinkInfo info) throws IOException {
        String target = info == null ? null : info.targetPath;
        if (target == null) {
            File relative = linkTarget(drive, file, info);
            target = relative == null ? null : dosPath(drive, relative);
        }
        if (target == null) return key(file.getPath());
        String arguments = info.arguments == null ? "" : info.arguments;
        return key(target.replace('/', '\\')) + "\0" + arguments;
    }

    private static String dosPath(File drive, File file) {
        return "C:\\" + drive.toPath().relativize(file.toPath()).toString().replace('/', '\\');
    }

    private static boolean insideDrive(File drive, File file) throws IOException {
        return file.toPath().startsWith(drive.toPath()) && file.equals(file.getCanonicalFile());
    }

    private static File[] children(File directory) throws IOException {
        if (!directory.equals(directory.getCanonicalFile())) return new File[0];
        File[] files = directory.listFiles(file -> !Files.isSymbolicLink(file.toPath()));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(File::getName));
        return files;
    }

    private static boolean isLink(File file) {
        return key(file.getName()).endsWith(".lnk") && file.isFile()
                && file.length() >= 76 && file.length() <= MAX_LINK_SIZE;
    }

    private static boolean isUninstaller(String name) {
        return name.startsWith("uninstall") || name.startsWith("unins") || name.contains("卸载");
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String digest(byte[] data) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data));
        }
        catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
