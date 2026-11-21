package app.windroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.DesktopProgramLinks;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.win32.MSLink;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

final class WindowsEnvironment {
    static final int CONTAINER_ID = 1;
    private static final String RESET_PENDING = "vm_reset_pending";
    private static final String IMPORTS = "WinDroid/Imports";
    private static final long STORAGE_RESERVE = 32L * 1024 * 1024;
    private static final long SHORTCUT_LIMIT = 1024L * 1024;
    private static final String[] SHORTCUT_LOCATIONS = {
        "users/" + RootFS.USER + "/Desktop",
        "users/Public/Desktop",
        "users/" + RootFS.USER + "/AppData/Roaming/Microsoft/Windows/Start Menu/Programs",
        "ProgramData/Microsoft/Windows/Start Menu/Programs"
    };

    private final Context context;
    private final File drive;

    private WindowsEnvironment(Context context, File drive) {
        this.context = context;
        this.drive = drive;
    }

    static boolean hasPendingReset(Context context) {
        return context.getSharedPreferences(WinDroidLibrary.PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(RESET_PENDING, false);
    }

    static void reset(Context context) throws IOException {
        SharedPreferences library = context.getSharedPreferences(WinDroidLibrary.PREFERENCES, Context.MODE_PRIVATE);
        // Record confirmation before removing files so an interrupted reset can be retried.
        if (!library.edit().putBoolean(RESET_PENDING, true).commit()) {
            throw new IOException("Cannot record the Windows reset");
        }
        deleteVirtualMachineFiles(context.getFilesDir());
        WinDroidWallpaper.restoreDefault(context);
        if (!context.getSharedPreferences(WinDroidInputPreferences.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
                || !context.getSharedPreferences(WineRuntimePackage.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
                || !PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
                || !library.edit().clear().putBoolean(RESET_PENDING, true)
                        .putString(DesktopShortcuts.GENERATION, UUID.randomUUID().toString()).commit()) {
            throw new IOException("Cannot clear Windows preferences");
        }
    }

    static void finishReset(Context context) throws IOException {
        SharedPreferences library = context.getSharedPreferences(WinDroidLibrary.PREFERENCES, Context.MODE_PRIVATE);
        if (!library.edit().remove(RESET_PENDING).commit()) {
            library.edit().putBoolean(RESET_PENDING, true).apply();
            throw new IOException("Cannot finish the Windows reset");
        }
    }

    static void deleteVirtualMachineFiles(File filesDir) throws IOException {
        // Wine drive mappings and user folders can contain links outside the virtual disk.
        for (String name : new String[]{"rootfs", "imagefs", "installed_components", "pulseaudio", "profiles", "tmp"}) {
            File entry = new File(filesDir, name);
            if (!Files.notExists(entry.toPath(), LinkOption.NOFOLLOW_LINKS) && !FileUtils.delete(entry)) {
                throw new IOException("Cannot delete Windows data in " + name);
            }
        }
    }

    static WindowsEnvironment prepare(Context context, IntConsumer progress) throws IOException, JSONException {
        if (!Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a")) {
            throw new WinDroidException(R.string.wd_error_arm64_device);
        }
        try {
            RootFSInstaller.installFiles(context, progress);
        }
        catch (IOException error) {
            throw new WinDroidException(R.string.wd_error_prepare_storage, error);
        }
        progress.accept(-1);

        ContainerManager manager = new ContainerManager(context);
        Container container = manager.getContainerById(CONTAINER_ID);
        if (container == null) {
            JSONObject config = new JSONObject()
                    .put("name", "WinDroid")
                    .put("drives", "")
                    .put("dxwrapper", DXWrappers.WINED3D)
                    .put("startupSelection", Container.STARTUP_SELECTION_NORMAL);
            container = manager.createContainer(config);
        }
        if (container == null) throw new WinDroidException(R.string.wd_error_create_disk);

        File drive = new File(container.getRootDir().getCanonicalFile(), ".wine/drive_c");
        if (!drive.isDirectory() || !drive.equals(drive.getCanonicalFile())) {
            throw new WinDroidException(R.string.wd_error_read_disk);
        }
        updateDefaultCompatibility(container);
        WindowsFonts.install(context, drive);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("open_android_browser_from_wine", false)
                .putBoolean("move_cursor_to_touchpoint", true)
                .apply();

        WindowsEnvironment environment = new WindowsEnvironment(context, drive);
        environment.cleanIncompleteImports();
        return environment;
    }

    private static void updateDefaultCompatibility(Container container) {
        if (!DXWrappers.WINED3D.equals(container.getDXWrapper())
                || !Box64Preset.PERFORMANCE.equals(container.getBox64Preset())
                || !container.getGraphicsDriverConfig().isEmpty()) return;

        String[] drivers = GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver());
        if (drivers.length != 2 || !GraphicsDrivers.GLADIO.equals(drivers[1])) return;
        container.setGraphicsDriver(drivers[0] + "," + GraphicsDrivers.VIRGL);
        container.setBox64Preset(Box64Preset.STABILITY);
        // Use Wine's standard waits for office apps that mix server and eventfd objects.
        container.setEnvVars(new EnvVars(container.getEnvVars()).put("WINEESYNC", 0).toString());
        container.saveData();
    }

    private void cleanIncompleteImports() throws IOException {
        File root = resolve(IMPORTS);
        File[] partial = root.listFiles((dir, name) -> name.startsWith(".pending-"));
        if (partial != null) {
            for (File file : partial) FileUtils.delete(file);
        }
    }

    Container readContainer() {
        return new ContainerManager(context).getContainerById(CONTAINER_ID);
    }

    File drive() {
        return drive;
    }

    String importInstaller(Uri uri) throws IOException {
        String name = null;
        long size = -1;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameColumn >= 0) name = cursor.getString(nameColumn);
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn);
            }
        }
        boolean msi = name != null && WindowsInstaller.hasMsiExtension(name);
        if (name != null && !msi && !WindowsExecutable.hasExeExtension(name)) {
            throw new WinDroidException(R.string.wd_error_installer_extension);
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new WinDroidException(R.string.wd_error_read_file);
            return saveInstaller(input, name, msi, size, null, null);
        }
    }

    String downloadInstaller(WinDroidStoreCatalog.App app, WinDroidStoreCatalog.Installer installer,
                             StoreDownload download, boolean preferMirrors) throws IOException {
        boolean msi = "msi".equals(installer.extension);
        return download.download(installer.url, preferMirrors, (input, size) ->
                saveInstaller(input, app.name + (msi ? ".msi" : ".exe"), msi, size, download, installer.sha256));
    }

    private String saveInstaller(InputStream input, String name, boolean msi, long size,
                                 StoreDownload download, String expectedHash) throws IOException {
        String filename = safeImportName(name, msi ? ".msi" : ".exe");
        String id = UUID.randomUUID().toString();
        File root = resolve(IMPORTS);
        if (!root.isDirectory() && !root.mkdirs()) throw new WinDroidException(R.string.wd_error_create_import_folder);
        long space = root.getUsableSpace() - STORAGE_RESERVE;
        if (space <= 0 || size > space) throw new WinDroidException(R.string.wd_error_import_space);

        File staging = new File(root, ".pending-" + id);
        if (!staging.mkdir()) throw new WinDroidException(R.string.wd_error_create_import_folder);
        try {
            File executable = new File(staging, filename);
            try (FileOutputStream output = new FileOutputStream(executable)) {
                MessageDigest digest = download == null ? null : StoreDownload.sha256();
                byte[] buffer = new byte[128 * 1024];
                long copied = 0;
                if (download != null) {
                    download.checkCancelled();
                    download.update(0, size);
                }
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > space) throw new WinDroidException(R.string.wd_error_import_space);
                    if (download != null) {
                        download.checkCancelled();
                        digest.update(buffer, 0, count);
                        download.update(copied, size);
                    }
                    output.write(buffer, 0, count);
                }
                if (download != null) {
                    download.checkCancelled();
                    if (!StoreDownload.matchesSha256(digest.digest(), expectedHash)) {
                        throw new WinDroidException(R.string.wd_store_checksum_error);
                    }
                }
                output.getFD().sync();
            }
            if (msi) WindowsInstaller.validateMsi(executable);
            else WindowsExecutable.validate(executable);
            if (!staging.renameTo(new File(root, id))) throw new WinDroidException(R.string.wd_error_save_import);
            return IMPORTS + "/" + id + "/" + filename;
        }
        finally {
            FileUtils.delete(staging);
        }
    }

    private static String safeImportName(String name, String extension) {
        if (name == null) return "program" + extension;
        String stem = name.substring(0, name.length() - 4)
                .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
        int length = Math.min(stem.codePointCount(0, stem.length()), 55);
        stem = stem.substring(0, stem.offsetByCodePoints(0, length));
        if (stem.isEmpty()) return "program" + extension;
        if (stem.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) stem = "_" + stem;
        return stem + extension;
    }

    File resolve(String relativePath) throws IOException {
        if (relativePath.isEmpty()) return drive;
        File candidate = new File(drive, relativePath);
        if (!candidate.getPath().startsWith(drive.getPath() + File.separator)
                || !candidate.equals(candidate.getCanonicalFile())) {
            throw new WinDroidException(R.string.wd_error_disk_location);
        }
        return candidate;
    }

    File launchTarget(String path) throws IOException {
        File file = resolve(path);
        if (!file.isFile()) throw new WinDroidException(R.string.wd_error_program_missing);
        if (WindowsExecutable.hasExeExtension(file.getName())) WindowsExecutable.validate(file);
        else if (!isWindowsShortcut(file)) throw new WinDroidException(R.string.wd_error_open_windows_shortcut);
        return file;
    }

    String uninstallTarget(String path) {
        String target = programIdentity(path, new LinkedHashMap<>());
        if (target == null) return null;
        String key = target.toLowerCase(Locale.ROOT);
        if (key.startsWith("windows/") || key.startsWith("programdata/microsoft/windows/")
                || key.startsWith("windroid/") && !key.startsWith("windroid/imports/")
                || key.equals("program files/internet explorer/iexplore.exe")
                || key.equals("program files (x86)/internet explorer/iexplore.exe")) return null;
        return target;
    }

    WindowsAppUninstall findUninstaller(String target) {
        return WindowsAppUninstall.find(drive, target);
    }

    String removableProgramFolder(String target, List<String> library) throws IOException {
        File folder = resolve(target).getParentFile();
        if (folder.equals(drive)) return null;
        String relative = relativePath(folder);
        if (!isRemovableProgramFolder(relative)) return null;
        Set<String> entries = new HashSet<>(programSnapshot().keySet());
        entries.addAll(library);
        Map<String, String> programs = programIdentities(entries, new LinkedHashMap<>());
        for (String other : programs.values()) {
            if (!other.equals(target) && other.startsWith(relative + '/') && !isUninstaller(new File(other).getName())) return null;
        }
        return relative;
    }

    static boolean isRemovableProgramFolder(String relative) {
        String[] parts = relative.toLowerCase(Locale.ROOT).split("/");
        String first = parts[0];
        if (first.isEmpty() || first.equals("windows")) return false;
        if (first.equals("windroid")) return parts.length >= 3 && parts[1].equals("imports");
        if (first.equals("users")) {
            if (parts.length < 4) return false;
            return !parts[2].equals("appdata") || parts.length >= 5 && !parts[4].equals("microsoft")
                    && !parts[4].equals("temp") && !parts[4].equals("packages");
        }
        if (first.equals("programdata") && parts.length > 1 && parts[1].equals("microsoft")) return false;
        if ((first.equals("program files") || first.equals("program files (x86)"))
                && parts.length > 1 && parts[1].equals("common files")) return false;
        return parts.length > 1 || !Arrays.asList("program files", "program files (x86)", "programdata",
                "downloads", "download", "documents", "desktop", "temp", "programs").contains(first);
    }

    void removeProgramFiles(String target, String folder) throws IOException {
        if (folder == null) {
            Files.delete(resolve(target).toPath());
            return;
        }
        File executable = resolve(target);
        File parent = resolve(folder);
        if (!isRemovableProgramFolder(folder) || !parent.equals(executable.getParentFile()) || !parent.isDirectory()) {
            throw new WinDroidException(R.string.wd_uninstall_changed);
        }
        Files.walkFileTree(parent.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    boolean removalConfirmed(WindowsAppUninstall request) throws IOException {
        return !resolve(request.target).exists() && WindowsRegistryApps.uninstallEntries(drive.getParentFile())
                .stream().noneMatch(entry -> entry.id.equals(request.id));
    }

    Map<String, String> programSnapshot() {
        Map<String, String> programs = new LinkedHashMap<>();
        Set<File> managed = DesktopProgramLinks.managedShortcuts(drive);
        for (String path : SHORTCUT_LOCATIONS) {
            try {
                scanShortcuts(resolve(path), programs, managed);
            }
            catch (IOException ignored) {
                // A Windows program can remove or relocate a standard shortcut directory.
            }
        }
        for (String path : WindowsRegistryApps.executablePaths(drive.getParentFile())) {
            File program = WindowsAppInfo.resolveWindowsFile(drive, drive, path);
            if (program != null) programs.putIfAbsent(relativePath(program), fileSnapshot(program));
        }
        return programs;
    }

    Map<String, String> installedProgramLinks(Iterable<String> paths) {
        Map<String, String> apps = new LinkedHashMap<>();
        Set<String> shortcutTargets = new HashSet<>();
        Map<File, Boolean> executableCache = new LinkedHashMap<>();
        for (String path : paths) {
            String target = programIdentity(path, executableCache);
            if (target != null && isApplicationPath(target)
                    && !isUninstaller(new File(path).getName()) && !isUninstaller(new File(target).getName())) {
                apps.put(path, target);
                if (!path.equals(target)) shortcutTargets.add(target);
            }
        }
        apps.entrySet().removeIf(entry -> entry.getKey().equals(entry.getValue())
                && shortcutTargets.contains(entry.getValue()));
        return apps;
    }

    Map<String, String> programIdentities(Iterable<String> paths, Map<String, String> known) {
        Map<String, String> identities = new LinkedHashMap<>();
        Map<File, Boolean> executableCache = new LinkedHashMap<>();
        for (String path : paths) {
            String target = known.get(path);
            if (target == null) target = programIdentity(path, executableCache);
            if (target != null) identities.put(path, target);
        }
        return identities;
    }

    private String programIdentity(String path, Map<File, Boolean> executableCache) {
        try {
            File target = resolve(path);
            if (isWindowsShortcut(target) && target.length() <= SHORTCUT_LIMIT) {
                MSLink.LinkInfo link = MSLink.extractLinkInfo(target);
                if (link == null) return null;
                File folder = target.getParentFile();
                target = WindowsAppInfo.resolveWindowsFile(drive, folder, link.targetPath);
                if (target == null) target = WindowsAppInfo.resolveWindowsFile(drive, folder, link.relativePath);
            }
            if (target == null || !target.isFile() || !WindowsExecutable.hasExeExtension(target.getName())) return null;
            Boolean executable = executableCache.get(target);
            if (executable == null) {
                executable = isSupportedExecutable(target);
                executableCache.put(target, executable);
            }
            return executable ? relativePath(target) : null;
        }
        catch (IOException | RuntimeException ignored) {
            // Missing, malformed, document and external shortcuts stay out of detection.
            return null;
        }
    }

    private static boolean isSupportedExecutable(File target) {
        try {
            WindowsExecutable.validate(target);
            return true;
        }
        catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isUninstaller(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        return name.startsWith("unins") || name.endsWith("uninst.exe") || name.contains("卸载");
    }

    private static boolean isApplicationPath(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        return !name.startsWith("windows/")
                && !name.startsWith("windroid/imports/")
                && !name.equals("program files/internet explorer/iexplore.exe")
                && !name.equals("program files (x86)/internet explorer/iexplore.exe");
    }

    private void scanShortcuts(File folder, Map<String, String> output, Set<File> managed) {
        File[] entries = folder.listFiles();
        if (entries == null) return;
        Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : entries) {
            if (managed.contains(file) || FileUtils.isSymlink(file)) continue;
            if (file.isDirectory()) scanShortcuts(file, output, managed);
            else if (isWindowsShortcut(file)) output.put(relativePath(file), fileSnapshot(file));
        }
    }

    private static String fileSnapshot(File file) {
        return file.lastModified() + ":" + file.length();
    }

    List<String> browse(String directory) throws IOException {
        File[] entries = resolve(directory).listFiles(file -> !FileUtils.isSymlink(file)
                && !file.getName().startsWith(".")
                && (file.isDirectory() || WindowsExecutable.hasExeExtension(file.getName())));
        if (entries == null) throw new WinDroidException(R.string.wd_error_browse);
        Arrays.sort(entries, Comparator.comparing(File::isFile)
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        List<String> paths = new ArrayList<>(entries.length);
        for (File entry : entries) paths.add(relativePath(entry));
        return paths;
    }

    private String relativePath(File file) {
        return file.getPath().substring(drive.getPath().length() + 1);
    }

    private static boolean isWindowsShortcut(File file) {
        return file.isFile() && file.length() >= 76 && file.getName().toLowerCase(Locale.ROOT).endsWith(".lnk");
    }
}
