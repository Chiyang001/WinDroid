package app.windroid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.winlator.win32.MSIcon;
import com.winlator.win32.MSLink;
import com.winlator.win32.PEParser;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WindowsAppInfo {
    private static final int ICON_SIZE = 144;
    private static final String USER = "C:\\users\\" + RootFS.USER;
    private static final Pattern ENVIRONMENT_VARIABLE = Pattern.compile("%([^%]+)%");
    final String name;
    final Bitmap icon;

    WindowsAppInfo(String name, Bitmap icon) {
        this.name = name;
        this.icon = icon;
    }

    static WindowsAppInfo read(WindowsEnvironment environment, String entryPath) {
        String name = WinDroidModel.label(entryPath);
        Bitmap source = null;
        try {
            File file = environment.resolve(entryPath);
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".lnk")) {
                MSLink.LinkInfo link = MSLink.extractLinkInfo(file);
                if (link != null) {
                    File drive = environment.resolve("");
                    File iconFile = resolveWindowsFile(drive, file.getParentFile(), link.iconLocation);
                    source = extractIcon(iconFile, link.shellIconIndex);
                    if (source == null) {
                        File target = resolveWindowsFile(drive, file.getParentFile(), link.targetPath);
                        if (target == null) target = resolveWindowsFile(drive, file.getParentFile(), link.relativePath);
                        source = extractIcon(target, 0);
                    }
                }
            }
            else {
                source = extractIcon(file, 0);
                PEParser.FileVersionInfo version = PEParser.getFileVersionInfo(file);
                if (version != null) {
                    String description = cleanName(version.FileDescription);
                    String product = cleanName(version.ProductName);
                    if (!description.isEmpty()) name = description;
                    else if (!product.isEmpty()) name = product;
                }
            }
        }
        catch (IOException | RuntimeException ignored) {
            // A missing or malformed Windows resource should still produce a usable launcher row.
        }
        return new WindowsAppInfo(name, roundedIcon(source, name));
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String name = value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        int limit = Math.min(80, name.codePointCount(0, name.length()));
        return name.substring(0, name.offsetByCodePoints(0, limit));
    }

    private static Bitmap extractIcon(File file, int index) {
        if (file == null || !file.isFile()) return null;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ico")) return MSIcon.decodeFile(file);
        if (name.endsWith(".exe") || name.endsWith(".dll") || name.endsWith(".icl")) {
            return PEParser.extractShellIcon(file, index);
        }
        return null;
    }

    static File resolveWindowsFile(File drive, File directory, String value) {
        File path = resolveWindowsPath(drive, directory, value);
        return path != null && path.isFile() ? path : null;
    }

    static File resolveWindowsPath(File drive, File directory, String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String path = expandVariables(value.trim().replace("\"", "")).replace('\\', '/');
        if (path.regionMatches(true, 0, "C:/", 0, 3)) {
            path = path.substring(3);
            directory = drive;
        }
        else if (path.startsWith("/") || path.indexOf(':') >= 0 || path.indexOf('%') >= 0) return null;

        Path root = drive.toPath();
        Path target = directory.toPath().resolve(path).normalize();
        if (!target.startsWith(root)) return null;
        File current = drive;
        for (Path segment : root.relativize(target)) {
            String component = segment.toString();
            File next = new File(current, component);
            if (!next.exists()) {
                File[] alternatives = current.listFiles((parent, name) -> name.equalsIgnoreCase(component));
                if (alternatives == null || alternatives.length != 1) return null;
                next = alternatives[0];
            }
            if (Files.isSymbolicLink(next.toPath())) return null;
            current = next;
        }
        return current.exists() ? current : null;
    }

    private static String expandVariables(String path) {
        Matcher variables = ENVIRONMENT_VARIABLE.matcher(path);
        StringBuffer expanded = new StringBuffer();
        while (variables.find()) {
            String value;
            switch (variables.group(1).toLowerCase(Locale.ROOT)) {
                case "windir":
                case "systemroot": value = "C:\\windows"; break;
                case "systemdrive": value = "C:"; break;
                case "programfiles":
                case "programw6432": value = "C:\\Program Files"; break;
                case "programfiles(x86)": value = "C:\\Program Files (x86)"; break;
                case "programdata": value = "C:\\ProgramData"; break;
                case "userprofile": value = USER; break;
                case "localappdata": value = USER + "\\AppData\\Local"; break;
                case "appdata": value = USER + "\\AppData\\Roaming"; break;
                default: value = variables.group();
            }
            variables.appendReplacement(expanded, Matcher.quoteReplacement(value));
        }
        return variables.appendTail(expanded).toString();
    }

    private static Bitmap roundedIcon(Bitmap source, String name) {
        Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColor(0xff303030);
        canvas.drawRoundRect(0, 0, ICON_SIZE, ICON_SIZE, 30, 30, paint);
        if (source != null) {
            float scale = 112f / Math.max(source.getWidth(), source.getHeight());
            float width = source.getWidth() * scale;
            float height = source.getHeight() * scale;
            canvas.drawBitmap(source, null, new RectF((ICON_SIZE - width) / 2, (ICON_SIZE - height) / 2,
                    (ICON_SIZE + width) / 2, (ICON_SIZE + height) / 2), paint);
        }
        else {
            String initial = name.isEmpty() ? "W" : new String(Character.toChars(name.codePointAt(0)));
            paint.setColor(0xffeeeeee);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(66);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(initial.toUpperCase(Locale.ROOT), ICON_SIZE / 2f,
                    (ICON_SIZE - paint.ascent() - paint.descent()) / 2f, paint);
        }
        return bitmap;
    }
}
