package com.winlator.core;

import android.util.Log;

import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public enum WineGuestLocale {
    CHINESE("zh_CN", "0804"),
    ENGLISH("en_US", "0409");

    private static final String TAG = "WinDroid";
    private final String locale;
    private final String languageId;

    WineGuestLocale(String locale, String languageId) {
        this.locale = locale;
        this.languageId = languageId;
    }

    public static WineGuestLocale prepare(RootFS rootFs, boolean chinese) {
        if (!chinese) return ENGLISH;
        File root = rootFs.getRootDir();
        Path locales = new File(root, "usr/lib/locale").toPath();
        try {
            compileChineseLocale(root, locales);
            return CHINESE;
        }
        catch (IOException error) {
            Log.w(TAG, "Unable to prepare the Chinese Wine locale", error);
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while preparing the Chinese Wine locale", error);
        }
        return ENGLISH;
    }

    public void configure(RootFS rootFs, EnvVars environment) {
        File root = rootFs.getRootDir();
        updateSystemLanguage(new File(root, RootFS.WINEPREFIX + "/system.reg"));
        // Wine derives the guest UI language and ANSI code page from glibc's locale.
        environment.put("LANG", locale + ".UTF-8");
        environment.put("LC_ALL", locale + ".UTF-8");
        environment.put("LANGUAGE", locale);
        environment.put("LOCPATH", new File(root, "usr/lib/locale"));
    }

    private void updateSystemLanguage(File systemRegistry) {
        if (!systemRegistry.isFile()) return;
        // Some Windows software reads these NLS values directly instead of calling the locale APIs.
        try (WineRegistryEditor registry = new WineRegistryEditor(systemRegistry)) {
            String currentControlSet = "System\\CurrentControlSet";
            String controlSet = registry.getSymlinkValue(currentControlSet, "SymbolicLinkValue");
            String key = (controlSet != null ? controlSet : currentControlSet) + "\\Control\\Nls\\Language";
            if (!languageId.equals(registry.getStringValue(key, "Default"))
                    || !languageId.equals(registry.getStringValue(key, "InstallLanguage"))) {
                registry.setStringValues(key, new String[]{"Default", languageId}, new String[]{"InstallLanguage", languageId});
            }
        }
    }

    private static void compileChineseLocale(File root, Path locales) throws IOException, InterruptedException {
        Path destination = locales.resolve(CHINESE.locale + ".utf8");
        if (Files.isRegularFile(destination.resolve("LC_MESSAGES/SYS_LC_MESSAGES"))) return;
        Files.createDirectories(locales);
        Path staging = Files.createTempDirectory(locales, ".zh-cn-");
        Process compiler = null;
        try {
            File i18n = new File(root, "usr/share/i18n");
            Path charmap = staging.resolve("UTF-8");
            try (InputStream compressed = new GZIPInputStream(
                    Files.newInputStream(new File(i18n, "charmaps/UTF-8.gz").toPath()))) {
                Files.copy(compressed, charmap);
            }
            Path output = staging.resolve(destination.getFileName());
            Path log = staging.resolve("localedef.log");
            ProcessBuilder command = new ProcessBuilder(
                    new File(root, "usr/lib/ld-linux-aarch64.so.1").getPath(),
                    "--library-path", new File(root, "usr/lib").getPath(),
                    new File(root, "usr/bin/localedef").getPath(), "--no-archive",
                    "-i", new File(i18n, "locales/" + CHINESE.locale).getPath(),
                    "-f", charmap.toString(), output.toString());
            command.directory(root).redirectErrorStream(true).redirectOutput(log.toFile());
            command.environment().put("I18NPATH", i18n.getPath());
            command.environment().put("LC_ALL", "C");
            compiler = command.start();
            if (!compiler.waitFor(20, TimeUnit.SECONDS)) throw new IOException("localedef timed out");
            if (compiler.exitValue() != 0 || !Files.isRegularFile(output.resolve("LC_MESSAGES/SYS_LC_MESSAGES"))) {
                throw new IOException("localedef failed: " + new String(Files.readAllBytes(log), StandardCharsets.UTF_8).trim());
            }
            Files.move(output, destination, StandardCopyOption.ATOMIC_MOVE);
        }
        finally {
            if (compiler != null && compiler.isAlive()) compiler.destroyForcibly();
            FileUtils.delete(staging.toFile());
        }
    }
}
