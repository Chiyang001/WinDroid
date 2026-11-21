package com.winlator.win32;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.winlator.core.FileUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;

public class MSIcon {
    private static final int MAX_DIMENSION = 512;
    static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    public static Bitmap decodeByteArray(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 8 || length > bytes.length - offset || length > MAX_IMAGE_BYTES) return null;
        ByteBuffer data = ByteBuffer.wrap(bytes, offset, length).slice().order(ByteOrder.LITTLE_ENDIAN);
        if (data.getInt(0) == 0x474e5089 && data.getInt(4) == 0x0a1a0a0d) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, offset, length, bounds);
            return validDimensions(bounds.outWidth, bounds.outHeight)
                    ? BitmapFactory.decodeByteArray(bytes, offset, length) : null;
        }
        return decodeDib(data);
    }

    private static Bitmap decodeDib(ByteBuffer data) {
        if (data.limit() < 40) return null;
        int headerSize = data.getInt(0);
        int width = data.getInt(4);
        int storedHeight = data.getInt(8);
        int height = Math.abs(storedHeight) / 2;
        int bits = Short.toUnsignedInt(data.getShort(14));
        if (headerSize < 40 || headerSize > data.limit() || !validDimensions(width, height)
                || data.getInt(16) != 0 || (bits != 1 && bits != 4 && bits != 8 && bits != 24 && bits != 32)) return null;

        int palette = 0;
        if (bits <= 8) {
            int used = data.getInt(32);
            if (used < 0 || used > 1 << bits) return null;
            palette = used == 0 ? 1 << bits : used;
        }
        int pixelsStart = headerSize + palette * 4;
        int stride = (width * bits + 31) / 32 * 4;
        int maskStart = pixelsStart + stride * height;
        int maskStride = (width + 31) / 32 * 4;
        if (maskStart > data.limit()) return null;
        boolean hasMask = maskStart + maskStride * height <= data.limit();
        int[] pixels = new int[width * height];
        boolean hasAlpha = false;
        for (int y = 0; y < height; y++) {
            int row = storedHeight < 0 ? y : height - y - 1;
            for (int x = 0; x < width; x++) {
                int position = pixelsStart + row * stride + x * bits / 8;
                int color;
                switch (bits) {
                    case 32:
                        color = data.getInt(position);
                        hasAlpha |= (color >>> 24) != 0;
                        break;
                    case 24:
                        color = 0xff000000 | Byte.toUnsignedInt(data.get(position + 2)) << 16
                                | Byte.toUnsignedInt(data.get(position + 1)) << 8 | Byte.toUnsignedInt(data.get(position));
                        break;
                    default:
                        int packed = Byte.toUnsignedInt(data.get(position));
                        int shift = 8 - bits - x * bits % 8;
                        int paletteIndex = (packed >>> shift) & ((1 << bits) - 1);
                        if (paletteIndex >= palette) return null;
                        color = 0xff000000 | (data.getInt(headerSize + paletteIndex * 4) & 0x00ffffff);
                }
                pixels[y * width + x] = color;
            }
        }
        if (bits != 32 || !hasAlpha) {
            for (int y = 0; y < height; y++) {
                int row = storedHeight < 0 ? y : height - y - 1;
                for (int x = 0; x < width; x++) {
                    int pixel = y * width + x;
                    boolean transparent = hasMask && (data.get(maskStart + row * maskStride + x / 8) & (0x80 >>> (x % 8))) != 0;
                    pixels[pixel] = (pixels[pixel] & 0x00ffffff) | (transparent ? 0 : 0xff000000);
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static boolean validDimensions(int width, int height) {
        return width > 0 && width <= MAX_DIMENSION && height > 0 && height <= MAX_DIMENSION;
    }

    public static Bitmap decodeFile(File file) {
        if (!file.isFile() || file.length() < 6 || file.length() > MAX_IMAGE_BYTES) return null;
        byte[] bytes = FileUtils.read(file);
        if (bytes == null || bytes.length < 6) return null;
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (data.getShort() != 0 || data.getShort() != 1) return null;
        int count = Short.toUnsignedInt(data.getShort());
        if (count == 0 || count > 128 || data.remaining() < count * 16) return null;

        ArrayList<int[]> images = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int width = Byte.toUnsignedInt(data.get());
            int height = Byte.toUnsignedInt(data.get());
            data.position(data.position() + 4);
            int bits = Short.toUnsignedInt(data.getShort());
            int size = data.getInt();
            int offset = data.getInt();
            images.add(new int[]{offset, size, (width == 0 ? 256 : width) * (height == 0 ? 256 : height), bits});
        }
        images.sort(Comparator.<int[]>comparingInt(image -> image[2]).thenComparingInt(image -> image[3]).reversed());
        for (int[] image : images) {
            Bitmap bitmap = decodeByteArray(bytes, image[0], image[1]);
            if (bitmap != null) return bitmap;
        }
        return null;
    }
}
