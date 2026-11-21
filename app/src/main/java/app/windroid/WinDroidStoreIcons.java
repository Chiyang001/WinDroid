package app.windroid;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WinDroidStoreIcons {
    private static final String ROOT = "windroid/store-icons";
    private static final ExecutorService DECODER = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(3 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final AssetManager assets;
    private Set<String> files = Collections.emptySet();

    WinDroidStoreIcons(Context app) {
        assets = app.getAssets();
    }

    void loadIndex() {
        try {
            String[] entries = assets.list(ROOT);
            if (entries != null) files = new HashSet<>(Arrays.asList(entries));
        }
        catch (IOException error) {
            android.util.Log.w("WinDroid", "Unable to read the store icon index", error);
        }
    }

    ImageView createView(Context context, String appId) {
        String file = iconFile(appId);
        if (file == null) return null;
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Bitmap cached = CACHE.get(file);
        if (cached != null) view.setImageBitmap(cached);
        else DECODER.execute(() -> {
            try (InputStream input = assets.open(ROOT + "/" + file)) {
                Bitmap image = BitmapFactory.decodeStream(input);
                if (image == null) return;
                CACHE.put(file, image);
                view.post(() -> view.setImageBitmap(image));
            }
            catch (IOException error) {
                android.util.Log.w("WinDroid", "Unable to read store icon for " + appId, error);
            }
        });
        return view;
    }

    boolean contains(String appId) {
        return iconFile(appId) != null;
    }

    private String iconFile(String appId) {
        String exact = appId + ".webp";
        if (files.contains(exact)) return exact;
        String family;
        if (appId.startsWith("Mozilla.Firefox.DeveloperEdition.")) {
            family = "Mozilla.Firefox.DeveloperEdition.webp";
        }
        else if (appId.equals("Mozilla.Firefox.ESR") || appId.startsWith("Mozilla.Firefox.ESR.")
                || appId.matches("Mozilla\\.Firefox\\.[a-z]{2,3}(-[a-zA-Z]{2,8})*")) {
            family = "Mozilla.Firefox.webp";
        }
        else return null;
        return files.contains(family) ? family : null;
    }
}
