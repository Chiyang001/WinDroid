package app.windroid;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WindowsAppUninstall {
    private static final Pattern DOS_PATH = Pattern.compile("(?i)^(?:c:|%[a-z0-9()]+%)[\\\\/].+");
    private static final Pattern COMMAND = Pattern.compile("(?i)^(?:\"([^\"]+\\.exe)\"|([^\"]+?\\.exe))(?=\\s|$)(.*)$");
    private static final Pattern MSI_PRODUCT = Pattern.compile(
            "(?i)(?:^|\\s)/[ix]\\s*\"?(" + WindowsRegistryApps.PRODUCT_GUID + ")\"?(?=\\s|$)");

    final String id;
    final String target;
    final String payload;

    private WindowsAppUninstall(String id, String target, String payload) {
        this.id = id;
        this.target = target;
        this.payload = payload;
    }

    static WindowsAppUninstall find(File drive, String appPath) {
        File target = new File(drive, appPath);
        Set<String> owners = WindowsRegistryApps.msiComponentOwners(drive.getParentFile(), dosPath(drive, target));
        WindowsAppUninstall selected = null;
        int best = 0;
        for (WindowsRegistryApps.UninstallEntry entry : WindowsRegistryApps.uninstallEntries(drive.getParentFile())) {
            Matcher command = COMMAND.matcher(entry.command.trim());
            if (entry.command.chars().anyMatch(Character::isISOControl) || !command.matches()) continue;
            String executable = command.group(1) != null ? command.group(1) : command.group(2);
            File runner = resolve(drive, executable);
            String arguments = command.group(3);
            File msiExec = new File(drive, "windows/system32/msiexec.exe");
            boolean ownsTarget = false;
            if (executable.equalsIgnoreCase("msiexec.exe") || msiExec.equals(runner)
                    || new File(drive, "windows/syswow64/msiexec.exe").equals(runner)) {
                Matcher product = MSI_PRODUCT.matcher(arguments);
                if (!product.find()) continue;
                if (runner == null) runner = msiExec;
                ownsTarget = owners.contains(WindowsRegistryApps.packProductCode(product.group(1)));
                arguments = " /x " + product.group(1) + " REBOOT=ReallySuppress";
            } else if (runner == null || !runner.isFile() || relative(drive, runner).toLowerCase(Locale.ROOT).startsWith("windows/")) {
                continue;
            }

            int score = 0;
            if (target.equals(resolve(drive, entry.executable))) score = Integer.MAX_VALUE;
            else if (ownsTarget) score = Integer.MAX_VALUE - 1;
            else {
                File directory = resolve(drive, entry.directory);
                if (directory == null && (entry.directory == null || entry.directory.trim().isEmpty())
                        && target.getParentFile().equals(runner.getParentFile())) directory = runner.getParentFile();
                if (directory != null && directory.isDirectory() && target.toPath().startsWith(directory.toPath())
                        && isAppDirectory(drive.toPath().relativize(directory.toPath()))) {
                    score = directory.toPath().getNameCount() - drive.toPath().getNameCount();
                }
            }
            if (score == 0 || score < best) continue;
            String program = dosPath(drive, runner);
            String workingDirectory = dosPath(drive, runner.getParentFile());
            String payload = program + '\0' + workingDirectory + '\0' + '"' + program + '"' + arguments + '\0';
            if (payload.length() > 8192) continue;
            selected = score == best ? null : new WindowsAppUninstall(entry.id, appPath, payload);
            best = score;
        }
        return selected;
    }

    private static File resolve(File drive, String path) {
        if (path == null) return null;
        path = path.trim();
        if (path.length() > 1 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        if (path.indexOf('"') >= 0 || path.chars().anyMatch(Character::isISOControl)
                || !DOS_PATH.matcher(path).matches()) return null;
        return WindowsAppInfo.resolveWindowsPath(drive, drive, path);
    }

    private static boolean isAppDirectory(Path directory) {
        String path = directory.toString().replace(File.separatorChar, '/').toLowerCase(Locale.ROOT);
        if (path.isEmpty() || path.equals("program files") || path.equals("program files (x86)")
                || path.equals("programdata") || path.equals("windows") || path.startsWith("windows/")
                || path.equals("windroid") || path.startsWith("windroid/imports")) return false;
        if (path.equals("users")) return false;
        if (!path.startsWith("users/")) return true;
        String[] parts = path.split("/");
        return parts.length >= 5 && parts[2].equals("appdata")
                && (parts[3].equals("local") || parts[3].equals("roaming") || parts[3].equals("locallow"))
                && !parts[4].equals("microsoft") && !parts[4].equals("temp");
    }

    private static String relative(File drive, File file) {
        return drive.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private static String dosPath(File drive, File file) {
        return "C:\\" + relative(drive, file).replace('/', '\\');
    }
}
