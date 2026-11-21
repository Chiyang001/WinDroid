package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RuntimeAssetPathsTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void relocatesPathAcrossBufferBoundaryWithoutChangingFileLength() throws IOException {
        File runtime = folder.newFile();
        String padding = new String(new char[131060]).replace('\0', 'x');
        String original = padding + "/data/data/com.winlator/files\0/data/user/0/com.winlator/cache\0com.winlator";
        String expected = padding + "/data/data/app.windroid/files\0/data/user/0/app.windroid/cache\0com.winlator";
        Files.write(runtime.toPath(), original.getBytes(StandardCharsets.US_ASCII));

        RuntimeAssetPaths paths = RuntimeAssetPaths.forAsset("box64/box64-0.4.4.tzst", "app.windroid");
        paths.relocateFile(runtime);
        assertEquals(expected, new String(Files.readAllBytes(runtime.toPath()), StandardCharsets.US_ASCII));
        assertEquals(original.length(), runtime.length());
    }

    @Test public void onlyRelocatesBundledRuntimeAssetsAndAbsoluteLinks() throws IOException {
        assertNull(RuntimeAssetPaths.forAsset("inputcontrols/game.icp", "app.windroid"));
        RuntimeAssetPaths paths = RuntimeAssetPaths.forAsset("rootfs.tzst", "app.windroid");
        assertEquals("/data/data/app.windroid/files/loader", paths.relocateLink("/data/data/com.winlator/files/loader"));
        assertEquals("../com.winlator/loader", paths.relocateLink("../com.winlator/loader"));
    }
}
