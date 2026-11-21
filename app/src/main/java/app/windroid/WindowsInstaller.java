package app.windroid;

import com.winlator.R;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

final class WindowsInstaller {
    static boolean hasMsiExtension(String name) {
        return name.length() > 4 && name.regionMatches(true, name.length() - 4, ".msi", 0, 4);
    }

    static void validateMsi(File file) throws IOException {
        // MSI uses Compound File Binary storage. Wine validates the installer database itself.
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            if (length >= 512 && input.readLong() == 0xd0cf11e0a1b11ae1L) {
                input.seek(26);
                int version = Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
                int byteOrder = input.readUnsignedShort();
                int sectorShift = Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
                int miniSectorShift = Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
                if (byteOrder == 0xfeff && miniSectorShift == 6
                        && ((version == 3 && sectorShift == 9) || (version == 4 && sectorShift == 12))) {
                    int sectorSize = 1 << sectorShift;
                    if (length >= 2L * sectorSize && length % sectorSize == 0) return;
                }
            }
        }
        throw new WinDroidException(R.string.wd_error_msi_invalid);
    }
}
