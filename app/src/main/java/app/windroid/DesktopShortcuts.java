package app.windroid;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.PersistableBundle;

import com.winlator.R;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class DesktopShortcuts {
    static final String OPEN_ACTION = "app.windroid.action.OPEN_WINDOWS_APP";
    static final String SHORTCUT_ID = "shortcut_id";
    static final String GENERATION = "desktop_shortcut_generation";
    private static final String SAVED_PATH = "windows_path";

    static String pin(Context context, String path, WindowsAppInfo app) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            return context.getString(R.string.wd_error_pin_unsupported);
        }
        try {
            String id = idFor(context, path);
            for (ShortcutInfo existing : manager.getPinnedShortcuts()) {
                if (id.equals(existing.getId())) return context.getString(R.string.wd_error_pin_exists);
            }

            Intent launch = new Intent(context, DesktopShortcutActivity.class)
                    .setAction(OPEN_ACTION).putExtra(SHORTCUT_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            PersistableBundle saved = new PersistableBundle();
            saved.putString(SAVED_PATH, path);
            Icon icon = app.icon == null ? Icon.createWithResource(context, R.mipmap.windroid_launcher)
                    : Icon.createWithBitmap(app.icon);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, id)
                    .setShortLabel(app.name)
                    .setIcon(icon)
                    .setIntent(launch)
                    .setExtras(saved)
                    .build();
            return manager.requestPinShortcut(shortcut, null) ? null : context.getString(R.string.wd_error_pin_rejected);
        }
        catch (RuntimeException error) {
            return context.getString(R.string.wd_error_pin_unavailable);
        }
    }

    static String pinnedPath(Context context, String id) {
        if (id == null || id.length() != 64) return null;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return null;
        for (ShortcutInfo shortcut : manager.getPinnedShortcuts()) {
            if (!id.equals(shortcut.getId()) || !shortcut.isEnabled()) continue;
            PersistableBundle saved = shortcut.getExtras();
            String path = saved == null ? null : saved.getString(SAVED_PATH);
            if (path != null && id.equals(idFor(context, path))) return path;
        }
        return null;
    }

    private static String idFor(Context context, String path) {
        try {
            String generation = context.getSharedPreferences(WinDroidLibrary.PREFERENCES, Context.MODE_PRIVATE)
                    .getString(GENERATION, "");
            String identity = generation.isEmpty() ? path : generation + ":" + path;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                key.append(Character.forDigit((value >>> 4) & 15, 16));
                key.append(Character.forDigit(value & 15, 16));
            }
            return key.toString();
        }
        catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
