package app.windroid;

import com.winlator.R;
import com.winlator.core.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class RuntimeArchive {
    static void deploy(File archive, File target, long unpackedSize, int fileCount, IntConsumer progress) throws IOException {
        File pending = stagingPath(target);
        remove(pending);
        Files.createDirectories(target.getParentFile().toPath());
        if (target.getParentFile().getUsableSpace() < unpackedSize + 32L * 1024 * 1024) {
            throw new WinDroidException(R.string.wd_error_import_space);
        }
        Files.createDirectory(pending.toPath());
        try {
            extract(archive, pending, unpackedSize, fileCount, progress);
            remove(target);
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        }
        finally {
            remove(pending);
        }
    }

    static void removeDeployment(File target) throws IOException {
        remove(stagingPath(target));
        remove(target);
    }

    private static File stagingPath(File target) throws IOException {
        if (!target.equals(target.getCanonicalFile())) throw new IOException("Invalid runtime directory");
        return new File(target.getParentFile(), target.getName() + ".part");
    }

    private static void extract(File archive, File folder, long unpackedSize, int fileCount,
                                IntConsumer progress) throws IOException {
        byte[] buffer = new byte[128 * 1024];
        long total = 0;
        int entries = 0;
        int percent = -1;
        try (InputStream source = Files.newInputStream(archive.toPath());
             ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || ++entries > fileCount) throw new IOException("Unexpected runtime package entry");
                File output = new File(folder, entry.getName());
                if (!output.equals(output.getCanonicalFile())
                        || !output.toPath().startsWith(folder.toPath())) {
                    throw new IOException("Invalid runtime package path");
                }
                Files.createDirectories(output.getParentFile().toPath());
                try (OutputStream stream = Files.newOutputStream(output.toPath(), StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        total += count;
                        if (total > unpackedSize) throw new IOException("Runtime package is too large");
                        stream.write(buffer, 0, count);
                        int current = (int)(total * 100 / unpackedSize);
                        if (progress != null && current != percent) progress.accept(current);
                        percent = current;
                    }
                }
            }
        }
        if (entries != fileCount || total != unpackedSize) throw new IOException("Incomplete runtime package");
    }

    private static void remove(File folder) throws IOException {
        if (Files.exists(folder.toPath(), LinkOption.NOFOLLOW_LINKS) && !FileUtils.delete(folder)) {
            throw new IOException("Could not remove runtime directory");
        }
    }
}
