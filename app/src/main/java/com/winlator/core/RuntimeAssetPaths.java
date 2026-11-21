package com.winlator.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

final class RuntimeAssetPaths {
    private static final String UPSTREAM_PACKAGE = "com.winlator";
    private static final String[] DATA_DIRECTORIES = {"/data/data/", "/data/user/0/"};
    private final byte[][] originalPaths = new byte[DATA_DIRECTORIES.length][];
    private final byte[][] relocatedPaths = new byte[DATA_DIRECTORIES.length][];
    private final String applicationId;
    private final byte[] buffer = new byte[128 * 1024];
    private int overlap;

    private RuntimeAssetPaths(String applicationId) throws IOException {
        this.applicationId = applicationId;
        for (int i = 0; i < DATA_DIRECTORIES.length; i++) {
            originalPaths[i] = packagePath(DATA_DIRECTORIES[i], UPSTREAM_PACKAGE);
            relocatedPaths[i] = packagePath(DATA_DIRECTORIES[i], applicationId);
            if (originalPaths[i].length != relocatedPaths[i].length) {
                throw new IOException("The runtime application ID must preserve the ELF path length");
            }
            overlap = Math.max(overlap, originalPaths[i].length - 1);
        }
    }

    static RuntimeAssetPaths forAsset(String assetName, String applicationId) throws IOException {
        if (UPSTREAM_PACKAGE.equals(applicationId)) return null;
        if (assetName.equals("rootfs.tzst") || assetName.equals("rootfs_patches.tzst")
                || assetName.equals("container_pattern.tzst") || assetName.equals("pulseaudio.tzst")
                || assetName.startsWith("box64/") || assetName.startsWith("graphics_driver/")) {
            return new RuntimeAssetPaths(applicationId);
        }
        return null;
    }

    String relocateLink(String link) {
        for (String directory : DATA_DIRECTORIES) {
            String original = directory + UPSTREAM_PACKAGE + "/";
            if (link.startsWith(original)) {
                return directory + applicationId + "/" + link.substring(original.length());
            }
        }
        return link;
    }

    // The bundled ELF interpreter and RUNPATH used the upstream app's private directory.
    // Replace only that absolute prefix and keep the string and ELF segment lengths intact.
    void relocateFile(File file) throws IOException {
        if (file.length() <= overlap) return;
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            long fileSize = output.length();
            for (long offset = 0; offset < fileSize; ) {
                int count = (int)Math.min(buffer.length, fileSize - offset);
                output.seek(offset);
                output.readFully(buffer, 0, count);
                int starts = offset + count == fileSize ? count : count - overlap;
                for (int i = 0; i < starts; i++) {
                    if (buffer[i] != '/') continue;
                    for (int path = 0; path < originalPaths.length; path++) {
                        byte[] original = originalPaths[path];
                        if (matches(i, count, original)) {
                            output.seek(offset + i);
                            output.write(relocatedPaths[path]);
                            i += original.length - 1;
                            break;
                        }
                    }
                }
                offset += starts;
            }
        }
    }

    private boolean matches(int index, int length, byte[] pattern) {
        if (index + pattern.length > length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (buffer[index + i] != pattern[i]) return false;
        }
        return true;
    }

    private static byte[] packagePath(String directory, String applicationId) {
        return (directory + applicationId + "/").getBytes(StandardCharsets.US_ASCII);
    }
}
