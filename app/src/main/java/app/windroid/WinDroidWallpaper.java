package app.windroid;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.Size;

import com.winlator.R;
import com.winlator.core.WineRegistryEditor;
import com.winlator.win32.MSBitmap;
import com.winlator.xserver.ScreenInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class WinDroidWallpaper {
    private static final String FILENAME = "windroid-wallpaper.bmp";
    private static final String CUSTOM_FILENAME = "windroid-wallpaper.jpg";
    private static final int MAX_IMAGE_SIDE = 2048;

    private WinDroidWallpaper() {}

    static boolean hasCustom(Context context) {
        return customFile(context).isFile();
    }

    static void restoreDefault(Context context) throws IOException {
        Files.deleteIfExists(customFile(context).toPath());
    }

    static void savePhoto(Context context, Uri uri) throws IOException {
        Bitmap image = decodePhoto(context.getContentResolver(), uri);
        if (image == null) throw new IOException("Unable to decode wallpaper photo");
        try {
            File pending = File.createTempFile("windroid-photo-", ".jpg", context.getFilesDir());
            try {
                try (FileOutputStream output = new FileOutputStream(pending)) {
                    if (!image.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                        throw new IOException("Unable to encode wallpaper photo");
                    }
                }
                replaceFile(pending, customFile(context));
            } finally {
                Files.deleteIfExists(pending.toPath());
            }
        } finally {
            image.recycle();
        }
    }

    public static Bitmap load(Context context) {
        File file = customFile(context);
        Bitmap image = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
        return image != null ? image : BitmapFactory.decodeResource(context.getResources(), R.drawable.windroid_hero);
    }

    private static File customFile(Context context) {
        return new File(context.getFilesDir(), CUSTOM_FILENAME);
    }

    private static Bitmap decodePhoto(ContentResolver resolver, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), (decoder, info, source) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                Size size = info.getSize();
                float scale = (float) MAX_IMAGE_SIDE / Math.max(size.getWidth(), size.getHeight());
                if (scale < 1) {
                    decoder.setTargetSize(Math.max(1, Math.round(size.getWidth() * scale)),
                            Math.max(1, Math.round(size.getHeight() * scale)));
                }
            });
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        int size = Math.max(options.outWidth, options.outHeight);
        options.inSampleSize = 1;
        while (size / options.inSampleSize > MAX_IMAGE_SIDE) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap image;
        try (InputStream input = resolver.openInputStream(uri)) {
            image = BitmapFactory.decodeStream(input, null, options);
        }
        if (image == null) return null;

        int orientation;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return image;
            orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException unavailableMetadata) {
            return image;
        }

        Matrix transform = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: transform.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: transform.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: transform.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                transform.setRotate(90);
                transform.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90: transform.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                transform.setRotate(-90);
                transform.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270: transform.setRotate(-90); break;
            default: return image;
        }
        Bitmap oriented = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), transform, true);
        if (oriented != image) image.recycle();
        return oriented;
    }

    public static void applyToDesktop(Bitmap image, File winePrefix, ScreenInfo screen, Point display) {
        Bitmap desktop = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888);
        try {
            // Match the crop of the image beneath the desktop window, including on wider displays.
            float displayScale = Math.max((float) display.x / image.getWidth(), (float) display.y / image.getHeight());
            float windowScale = Math.min((float) display.x / screen.width, (float) display.y / screen.height);
            float width = image.getWidth() * displayScale / windowScale;
            float height = image.getHeight() * displayScale / windowScale;
            float left = (screen.width - width) * 0.5f;
            float top = (screen.height - height) * 0.5f;
            new Canvas(desktop).drawBitmap(image, null, new RectF(left, top, left + width, top + height),
                    new Paint(Paint.FILTER_BITMAP_FLAG));

            File windows = new File(winePrefix, "drive_c/windows");
            File wallpaper = new File(windows, FILENAME);
            File pending = File.createTempFile("windroid-wallpaper-", ".bmp", windows);
            try {
                if (!MSBitmap.create(desktop, pending)) throw new IOException("Could not write desktop wallpaper");
                replaceFile(pending, wallpaper);
            } finally {
                Files.deleteIfExists(pending.toPath());
            }
            try (WineRegistryEditor registry = new WineRegistryEditor(new File(winePrefix, "user.reg"))) {
                registry.setStringValue("Control Panel\\Desktop", "Wallpaper", "C:\\windows\\" + FILENAME);
                registry.setStringValue("Control Panel\\Desktop", "TileWallpaper", "0");
            }
        } catch (IOException error) {
            Log.w("WinDroid", "Unable to set the Windows desktop wallpaper", error);
        } finally {
            desktop.recycle();
        }
    }

    private static void replaceFile(File pending, File destination) throws IOException {
        Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
