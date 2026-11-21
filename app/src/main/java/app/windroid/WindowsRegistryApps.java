package app.windroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the Wine uninstall and App Paths registrations that identify application executables. */
final class WindowsRegistryApps {
    private static final long REGISTRY_LIMIT = 16L * 1024 * 1024;
    private static final int LINE_LIMIT = 4096;
    private static final int APP_LIMIT = 512;
    private static final String WINDOWS_VERSION = "microsoft\\windows\\currentversion\\";
    private static final Pattern APP_KEY = Pattern.compile(
            "software\\\\(?:wow6432node\\\\)?" + Pattern.quote(WINDOWS_VERSION)
                    + "(uninstall|app paths)\\\\([^\\\\]+)");
    private static final Pattern ICON_INDEX = Pattern.compile(",\\s*[+-]?[0-9]+$");
    private static final Pattern DRIVE_LOCATION = Pattern.compile("(?i)^(?:c:|%[a-z0-9()]+%)[\\\\/].*");
    private static final String MSI_COMPONENTS = "software\\" + WINDOWS_VERSION + "installer\\userdata\\";
    private static final Pattern MSI_COMPONENT_KEY = Pattern.compile(Pattern.quote(MSI_COMPONENTS)
            + "s-1-[0-9-]+\\\\components\\\\[a-f0-9]{32}");
    private static final Pattern PACKED_PRODUCT = Pattern.compile("[a-f0-9]{32}", Pattern.CASE_INSENSITIVE);
    static final String PRODUCT_GUID = "\\{[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\}";

    static Set<String> msiComponentOwners(File prefix, String target) {
        Set<String> owners = new LinkedHashSet<>();
        for (String hive : new String[]{"system.reg", "user.reg"}) {
            File registry = new File(prefix, hive);
            if (!readableRegistry(registry)) continue;
            try (BufferedReader reader = Files.newBufferedReader(registry.toPath(), StandardCharsets.UTF_8)) {
                if (!"WINE REGISTRY Version 2".equals(reader.readLine())) continue;
                boolean component = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("[")) {
                        String key = line.length() <= LINE_LIMIT ? readString(line, 1, ']') : null;
                        component = key != null && MSI_COMPONENT_KEY.matcher(key.toLowerCase(Locale.ROOT)).matches();
                    } else if (component && line.length() <= LINE_LIMIT && line.startsWith("\"")) {
                        int equals = line.indexOf("\"=");
                        if (equals != 33) continue;
                        String product = line.substring(1, equals);
                        if (!PACKED_PRODUCT.matcher(product).matches()) continue;
                        String installed = stringValue(line.substring(equals + 2));
                        if (installed != null && target.equalsIgnoreCase(installed.replace('/', '\\'))) {
                            owners.add(product.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (IOException ignored) {
                // Other installation records can still identify the app.
            }
        }
        return owners;
    }

    static String packProductCode(String product) {
        String[] parts = product.toLowerCase(Locale.ROOT).substring(1, product.length() - 1).split("-");
        StringBuilder packed = new StringBuilder(32);
        for (int index = 0; index < 3; index++) packed.append(new StringBuilder(parts[index]).reverse());
        String tail = parts[3] + parts[4];
        for (int index = 0; index < tail.length(); index += 2) packed.append(tail.charAt(index + 1)).append(tail.charAt(index));
        return packed.toString();
    }

    static Set<String> executablePaths(File prefix) {
        Set<String> apps = new LinkedHashSet<>();
        scan(prefix, app -> {
            String path = app.executablePath();
            if (path != null) apps.add(path);
            return apps.size() < APP_LIMIT;
        });
        return apps;
    }

    static List<UninstallEntry> uninstallEntries(File prefix) {
        List<UninstallEntry> entries = new ArrayList<>();
        scan(prefix, app -> {
            if (app.appPathName == null && app.hasDisplayName && !app.hidden) {
                String command = app.uninstall;
                if ((command == null || command.trim().isEmpty()) && app.msi) {
                    String product = app.id.substring(app.id.lastIndexOf('\\') + 1);
                    if (product.matches(PRODUCT_GUID)) command = "MsiExec.exe /x " + product;
                }
                if (command == null || command.trim().isEmpty()) command = app.quietUninstall;
                if (command != null && !command.trim().isEmpty()) {
                    entries.add(new UninstallEntry(app.id, app.executablePath(), app.installLocation, command));
                }
            }
            return entries.size() < APP_LIMIT;
        });
        return entries;
    }

    private static void scan(File prefix, Predicate<AppKey> accept) {
        for (String hive : new String[]{"system.reg", "user.reg"}) {
            File registry = new File(prefix, hive);
            if (!readableRegistry(registry)) continue;
            try (BufferedReader reader = Files.newBufferedReader(registry.toPath(), StandardCharsets.UTF_8)) {
                if (!"WINE REGISTRY Version 2".equals(reader.readLine())) continue;
                if (!readKeys(reader, hive, accept)) return;
            }
            catch (IOException ignored) {
                // A missing or partially rewritten hive should not prevent detection from shortcuts.
            }
        }
    }

    private static boolean readableRegistry(File registry) {
        return registry.isFile() && registry.length() <= REGISTRY_LIMIT && !Files.isSymbolicLink(registry.toPath());
    }

    private static boolean readKeys(BufferedReader reader, String hive, Predicate<AppKey> accept) throws IOException {
        AppKey app = null;
        String line;
        int charsRemaining = (int)REGISTRY_LIMIT;
        while (charsRemaining > 0 && (line = reader.readLine()) != null) {
            charsRemaining -= line.length() + 1;
            if (line.startsWith("[")) {
                if (app != null && !accept.test(app)) return false;
                app = line.length() <= LINE_LIMIT ? readKey(line, hive) : null;
            }
            else if (app != null && line.length() <= LINE_LIMIT) app.readValue(line);
        }
        return app == null || accept.test(app);
    }

    private static AppKey readKey(String line, String hive) {
        String key = readString(line, 1, ']');
        if (key == null) return null;
        key = key.toLowerCase(Locale.ROOT);
        Matcher matcher = APP_KEY.matcher(key);
        return matcher.matches() ? new AppKey(hive + ':' + key,
                matcher.group(1).equals("app paths") ? matcher.group(2) : null) : null;
    }

    private static String readString(String value, int start, char end) {
        StringBuilder text = new StringBuilder();
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == end) return text.toString();
            if (current == '\\') {
                if (++i == value.length()) return null;
                current = value.charAt(i);
                if (current == 'x') {
                    int code = 0;
                    int digits = 0;
                    while (digits < 4 && i + 1 < value.length()) {
                        int digit = Character.digit(value.charAt(i + 1), 16);
                        if (digit < 0) break;
                        code = code * 16 + digit;
                        digits++;
                        i++;
                    }
                    if (digits == 0) return null;
                    current = (char)code;
                }
                else if (current != '\\' && current != end) return null;
            }
            text.append(current);
        }
        return null;
    }

    private static String stringValue(String value) {
        if (value.startsWith("str(2):")) value = value.substring(7);
        return value.startsWith("\"") ? readString(value, 1, '"') : null;
    }

    private static final class AppKey {
        final String id;
        private final String appPathName;
        private String executable;
        private String installLocation;
        private String uninstall;
        private String quietUninstall;
        private boolean msi;
        private boolean hasDisplayName;
        private boolean hidden;

        AppKey(String id, String appPathName) {
            this.id = id;
            this.appPathName = appPathName;
        }

        void readValue(String line) {
            int equals = line.indexOf('=');
            if (equals < 0) return;
            String name = line.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = line.substring(equals + 1);
            if (appPathName != null) {
                if (name.equals("@")) executable = stringValue(value);
                return;
            }
            switch (name) {
                case "\"displayname\"":
                    String displayName = stringValue(value);
                    hasDisplayName = displayName != null && !displayName.trim().isEmpty();
                    break;
                case "\"displayicon\"":
                    executable = stringValue(value);
                    break;
                case "\"installlocation\"":
                    installLocation = stringValue(value);
                    break;
                case "\"uninstallstring\"":
                    uninstall = stringValue(value);
                    break;
                case "\"quietuninstallstring\"":
                    quietUninstall = stringValue(value);
                    break;
                case "\"windowsinstaller\"":
                    msi = value.equalsIgnoreCase("dword:00000001");
                    break;
                case "\"systemcomponent\"":
                    hidden |= value.startsWith("dword:") && !value.equalsIgnoreCase("dword:00000000");
                    break;
                case "\"parentkeyname\"":
                case "\"releasetype\"":
                    String marker = stringValue(value);
                    hidden |= marker != null && !marker.trim().isEmpty();
                    break;
                default:
                    break;
            }
        }

        String executablePath() {
            if (hidden || executable == null || (appPathName == null && !hasDisplayName)) return null;
            String path = executable.trim();
            if (appPathName == null) path = ICON_INDEX.matcher(path).replaceFirst("").trim();
            if (path.length() > 1 && path.startsWith("\"") && path.endsWith("\"")) {
                path = path.substring(1, path.length() - 1);
            }
            if (path.indexOf('"') >= 0 || path.indexOf('\0') >= 0 || !DRIVE_LOCATION.matcher(path).matches()
                    || !WindowsExecutable.hasExeExtension(path)) return null;
            String name = path.substring(Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/')) + 1);
            return appPathName == null || appPathName.equalsIgnoreCase(name) ? path : null;
        }
    }

    static final class UninstallEntry {
        final String id;
        final String executable;
        final String directory;
        final String command;

        UninstallEntry(String id, String executable, String directory, String command) {
            this.id = id;
            this.executable = executable;
            this.directory = directory;
            this.command = command;
        }
    }
}
