package app.windroid;

import com.winlator.core.EnvVars;

import java.io.File;
import java.io.IOException;
import java.util.function.IntConsumer;

final class WebView2Runtime {
    private static final String DIRECTORY = "WinDroid/Runtimes/WebView2";
    private static final String BROWSER_FOLDER = "WEBVIEW2_BROWSER_EXECUTABLE_FOLDER";
    private static final int FILE_COUNT = 257;
    private static final long UNPACKED_BYTES = 711_058_264;

    static boolean isInstalled(File drive) {
        File folder = new File(drive, DIRECTORY);
        return new File(folder, "msedgewebview2.exe").length() == 4_531_520
                && new File(folder, "msedge.dll").length() == 358_210_376;
    }

    static void applyEnvironment(File drive, EnvVars env) {
        if (!env.has(BROWSER_FOLDER) && isInstalled(drive)) {
            env.put(BROWSER_FOLDER, "C:\\" + DIRECTORY.replace('/', '\\'));
        }
    }

    static void change(File drive, File archive, boolean enable, IntConsumer progress) throws IOException {
        File target = new File(drive.getCanonicalFile(), DIRECTORY);
        if (!enable) {
            RuntimeArchive.removeDeployment(target);
        }
        else if (!isInstalled(drive)) {
            RuntimeArchive.deploy(archive, target, UNPACKED_BYTES, FILE_COUNT, progress);
        }
    }
}
