package app.windroid;

import android.content.Context;
import android.util.AtomicFile;

import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.core.WineRegistryEditor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;

final class WindowsFonts {
    private static final String FONT_FILE = "NotoSansCJKsc-Regular.otf";
    private static final String FONT_FAMILY = "Noto Sans CJK SC";
    private static final long FONT_BYTES = 16437364;
    private static final String FONT_VERSION = "windroid_font_fallback_v1";
    private static final String FONT_REGISTRY = "Software\\Microsoft\\Windows NT\\CurrentVersion\\";
    private static final String[] LATIN_FAMILIES = {
        "Tahoma", "Arial", "Segoe UI", "Microsoft Sans Serif", "Verdana", "Times New Roman", "Courier New"
    };
    private static final String[] CHINESE_FAMILIES = {
        "SimSun", "NSimSun", "SimHei", "Microsoft YaHei", "Microsoft YaHei UI", "DengXian"
    };

    static void install(Context context, File drive) throws IOException {
        File font = new File(drive, "windows/Fonts/" + FONT_FILE);
        if (font.isFile() && font.length() == FONT_BYTES
                && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(FONT_VERSION, false)) return;

        if (font.length() != FONT_BYTES) installFontFile(context, font);
        File systemRegistry = new File(drive.getParentFile(), "system.reg");
        String fontLink = FONT_FILE + "," + FONT_FAMILY;
        try (WineRegistryEditor registry = new WineRegistryEditor(systemRegistry)) {
            registry.setStringValue(FONT_REGISTRY + "Fonts", FONT_FAMILY + " (OpenType)", FONT_FILE);
            String linkKey = FONT_REGISTRY + "FontLink\\SystemLink";
            for (String family : LATIN_FAMILIES) {
                byte[] existing = registry.getHexValues(linkKey, family);
                LinkedHashSet<String> links = new LinkedHashSet<>();
                if (existing != null) {
                    links.addAll(Arrays.asList(new String(existing, StandardCharsets.UTF_16LE).split("\u0000")));
                    links.remove("");
                }
                if (links.add(fontLink)) registry.setMultiStringValue(linkKey, family, links.toArray(new String[0]));
            }
            String substitutesKey = FONT_REGISTRY + "FontSubstitutes";
            for (String family : CHINESE_FAMILIES) {
                if (registry.getStringValue(substitutesKey, family) == null) {
                    registry.setStringValue(substitutesKey, family, FONT_FAMILY);
                }
            }
            byte[] fallback = registry.getHexValues(linkKey, "Tahoma");
            if (fallback == null || !new String(fallback, StandardCharsets.UTF_16LE).contains(fontLink)) {
                throw new WinDroidException(R.string.wd_error_font_configuration);
            }
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(FONT_VERSION, true).apply();
    }

    private static void installFontFile(Context context, File font) throws IOException {
        AtomicFile file = new AtomicFile(font);
        try (InputStream source = context.getAssets().open("windroid/fonts/" + FONT_FILE)) {
            FileOutputStream destination = file.startWrite();
            try {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = source.read(buffer)) != -1) destination.write(buffer, 0, read);
                if (destination.getChannel().position() != FONT_BYTES) throw new WinDroidException(R.string.wd_error_font_incomplete);
                file.finishWrite(destination);
            }
            catch (IOException error) {
                file.failWrite(destination);
                throw error;
            }
        }
    }
}
