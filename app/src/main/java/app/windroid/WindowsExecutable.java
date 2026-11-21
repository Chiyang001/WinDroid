package app.windroid;

import com.winlator.R;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

final class WindowsExecutable {
    static boolean hasExeExtension(String name) {
        return name.length() > 4 && name.regionMatches(true, name.length() - 4, ".exe", 0, 4);
    }

    static void validate(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            if (length < 64 || input.readUnsignedShort() != 0x4d5a) throw invalidFile();

            input.seek(0x3c);
            long peOffset = readUnsignedInt(input);
            if (peOffset < 64 || peOffset > length - 26) throw invalidFile();

            input.seek(peOffset);
            if (input.readInt() != 0x50450000) throw invalidFile();

            int machine = readUnsignedShort(input);
            int sections = readUnsignedShort(input);
            input.skipBytes(12);
            int optionalSize = readUnsignedShort(input);
            int characteristics = readUnsignedShort(input);
            long sectionTable = peOffset + 24 + optionalSize;
            if ((characteristics & 0x0002) == 0 || (characteristics & 0x3000) != 0
                    || sections < 1 || sectionTable + 40L * sections > length) {
                throw invalidFile();
            }

            int magic = readUnsignedShort(input);
            if (machine != 0x014c && machine != 0x8664) {
                throw new WinDroidException(R.string.wd_error_exe_architecture, "这个程序的处理器架构暂不支持。请选择 32 位 x86 或 64 位 x64 EXE。");
            }
            if (machine == 0x014c ? magic != 0x10b || optionalSize < 96 : magic != 0x20b || optionalSize < 112) {
                throw invalidFile();
            }

            for (int section = 0; section < sections; section++) {
                input.seek(sectionTable + 40L * section + 16);
                long dataSize = readUnsignedInt(input);
                long dataOffset = readUnsignedInt(input);
                if (dataSize > 0 && (dataOffset < sectionTable || dataOffset + dataSize > length)) {
                    throw invalidFile();
                }
            }
        }
    }

    private static long readUnsignedInt(RandomAccessFile input) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));
    }

    private static int readUnsignedShort(RandomAccessFile input) throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
    }

    private static IOException invalidFile() {
        return new WinDroidException(R.string.wd_error_exe_invalid, "文件已损坏，或它不是可启动的 Windows EXE。");
    }
}
