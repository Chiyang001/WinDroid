package com.winlator.win32;

import android.graphics.Bitmap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Stack;

public class PEParser {
    private static final byte RT_ICON = 3;
    private static final byte RT_GROUP_ICON = 14;
    private static final byte RT_VERSION = 16;
    private final File peFile;
    private int resourcesRVA = 0;
    private int resourcesOffset = 0;

    public static class FileVersionInfo {
        public String Comments = "";
        public String CompanyName = "";
        public String FileDescription = "";
        public String FileVersion = "";
        public String InternalName = "";
        public String LegalCopyright = "";
        public String LegalTrademarks = "";
        public String OriginalFilename = "";
        public String PrivateBuild = "";
        public String ProductName = "";
        public String ProductVersion = "";
        public String SpecialBuildprivate = "";
    }

    private interface ImageResourceEntry {}

    private static class ImageResourceDirectoryEntry implements ImageResourceEntry {
        private final int name;
        private final boolean nameIsString;
        private final int offsetToData;
        private final boolean dataIsDirectory;
        private ImageResourceDirectory directory;

        private ImageResourceDirectoryEntry(ByteBuffer data) {
            int field1 = data.getInt();
            int field2 = data.getInt();

            this.name = field1 & 0x7fffffff;
            this.nameIsString = ((field1 >> 31) & 0x1) != 0;
            this.offsetToData = field2 & 0x7fffffff;
            this.dataIsDirectory = ((field2 >> 31) & 0x1) != 0;
        }
    }

    private static class ImageResourceDataEntry implements ImageResourceEntry {
        private final int offsetToData;
        private final int size;
        private final int codePage;
        private final int reserved;

        private ImageResourceDataEntry(ByteBuffer data) {
            this.offsetToData = data.getInt();
            this.size = data.getInt();
            this.codePage = data.getInt();
            this.reserved = data.getInt();
        }
    }

    private static class ImageResourceDirectory {
        private final int characteristics;
        private final int timeDateStamp;
        private final short majorVersion;
        private final short minorVersion;
        private final short numberOfNamedEntries;
        private final short numberOfIdEntries;
        private final ArrayList<ImageResourceEntry> entries = new ArrayList<>();

        private ImageResourceDirectory(byte type, ByteBuffer data, int level, int[] entriesLeft) {
            characteristics = data.getInt();
            timeDateStamp = data.getInt();
            majorVersion = data.getShort();
            minorVersion = data.getShort();
            numberOfNamedEntries = data.getShort();
            numberOfIdEntries = data.getShort();

            int numberOfEntries = Short.toUnsignedInt(numberOfNamedEntries) + Short.toUnsignedInt(numberOfIdEntries);
            if (numberOfEntries > entriesLeft[0] || numberOfEntries > data.remaining() / 8) {
                throw new IllegalArgumentException("Invalid PE resource entry count");
            }
            entriesLeft[0] -= numberOfEntries;
            for (int i = 0; i < numberOfEntries; i++) {
                ImageResourceDirectoryEntry directoryEntry = new ImageResourceDirectoryEntry(data);

                if (directoryEntry.dataIsDirectory && level < 2 && (level > 0 || directoryEntry.name == type)) {
                    int oldPosition = data.position();
                    data.position(directoryEntry.offsetToData);
                    directoryEntry.directory = new ImageResourceDirectory(type, data, level + 1, entriesLeft);
                    data.position(oldPosition);

                    entries.add(0, directoryEntry);
                }
                else if (!directoryEntry.dataIsDirectory && level > 0) {
                    int oldPosition = data.position();
                    data.position(directoryEntry.offsetToData);
                    ImageResourceDataEntry dataEntry = new ImageResourceDataEntry(data);
                    data.position(oldPosition);

                    entries.add(0, dataEntry);
                }
            }
        }
    }

    private static class VSFixedFileInfo {
        private final int dwSignature;
        private final int dwStrucVersion;
        private final int dwFileVersionMS;
        private final int dwFileVersionLS;
        private final int dwProductVersionMS;
        private final int dwProductVersionLS;
        private final int dwFileFlagsMask;
        private final int dwFileFlags;
        private final int dwFileOS;
        private final int dwFileType;
        private final int dwFileSubtype;
        private final int dwFileDateMS;
        private final int dwFileDateLS;

        private VSFixedFileInfo(ByteBuffer data) {
            dwSignature = data.getInt();
            dwStrucVersion = data.getInt();
            dwFileVersionMS = data.getInt();
            dwFileVersionLS = data.getInt();
            dwProductVersionMS = data.getInt();
            dwProductVersionLS = data.getInt();
            dwFileFlagsMask = data.getInt();
            dwFileFlags = data.getInt();
            dwFileOS = data.getInt();
            dwFileType = data.getInt();
            dwFileSubtype = data.getInt();
            dwFileDateMS = data.getInt();
            dwFileDateLS = data.getInt();
        }
    }

    private static String readUnicodeString(ByteBuffer data) {
        ByteBuffer stringBuf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        short value;
        while ((value = data.getShort()) != 0) stringBuf.putShort(value);
        return new String(Arrays.copyOf(stringBuf.array(), stringBuf.position()), StandardCharsets.UTF_16LE);
    }

    private static class StringHdr {
        private final short length;
        private final short valueLength;
        private final short type;
        private final String key;
        private final String value;

        private StringHdr(ByteBuffer data) {
            int position = data.position();
            length = data.getShort();
            valueLength = data.getShort();
            type = data.getShort();

            key = readUnicodeString(data);
            int offset = data.position() - position;
            if ((offset & 3) != 0) data.getShort();

            if (valueLength > 0) {
                byte[] bytes = new byte[valueLength * 2];
                data.get(bytes, 0, bytes.length);
                value = readUnicodeString(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
            }
            else value = null;
            if ((length & 3) != 0) data.getShort();
        }
    }

    private static class StringTable {
        private final short length;
        private final short valueLength;
        private final short type;
        private final String key;
        private final ArrayList<StringHdr> stringHdrs = new ArrayList<>();

        private StringTable(ByteBuffer data) {
            int position = data.position();
            length = data.getShort();
            valueLength = data.getShort();
            type = data.getShort();
            key = readUnicodeString(data);
            int offset = data.position() - position;
            if ((offset & 3) != 0) data.getShort();
            int remaining = length - offset;

            while (remaining > 0) {
                StringHdr stringhdr = new StringHdr(data);
                stringHdrs.add(stringhdr);
                remaining -= stringhdr.length;
            }
            if ((length & 3) != 0) data.getShort();
        }
    }

    private static class StringFileInfo {
        private final short length;
        private final short valueLength;
        private final short type;
        private final String key;
        private final ArrayList<StringTable> stringTables = new ArrayList<>();

        private StringFileInfo(ByteBuffer data) {
            int position = data.position();
            length = data.getShort();
            valueLength = data.getShort();
            type = data.getShort();
            key = readUnicodeString(data);
            if (!key.equals("StringFileInfo")) return;
            int offset = data.position() - position;
            if ((offset & 3) != 0) data.getShort();
            int remaining = length - offset;

            while (remaining > 0) {
                StringTable stringTable = new StringTable(data);
                stringTables.add(stringTable);
                remaining -= stringTable.length;
            }
            if ((length & 3) != 0) data.getShort();
        }
    }

    private static class VSVersionInfo {
        private final short length;
        private final short valueLength;
        private final short type;
        private final String key;
        private final VSFixedFileInfo value;
        private final StringFileInfo stringFileInfo;

        private VSVersionInfo(ByteBuffer data) {
            int position = data.position();
            length = data.getShort();
            valueLength = data.getShort();
            type = data.getShort();
            key = readUnicodeString(data);
            int offset = data.position() - position;
            if ((offset & 3) != 0) data.getShort();
            value = valueLength > 0 ? new VSFixedFileInfo(data) : null;

            if (value == null || value.dwStrucVersion != 0x10000) {
                stringFileInfo = null;
                return;
            }

            stringFileInfo = new StringFileInfo(data);
        }
    }

    private PEParser(File peFile) {
        this.peFile = peFile;
    }

    private ByteBuffer readResourceData(int dataOffset, int dataSize) {
        if (dataOffset < 0 || dataSize <= 0 || dataSize > MSIcon.MAX_IMAGE_BYTES) return null;
        try (RandomAccessFile input = new RandomAccessFile(peFile, "r")) {
            if ((long)dataOffset + dataSize > input.length()) return null;
            byte[] bytes = new byte[dataSize];
            input.seek(dataOffset);
            input.readFully(bytes);
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }
        catch (IOException e) {
            return null;
        }
    }

    private ImageResourceDirectory readImageResourceDirectory(byte type) {
        try (RandomAccessFile input = new RandomAccessFile(peFile, "r")) {
            if (input.readUnsignedShort() != 0x4d5a) return null;
            input.seek(60);
            long peOffset = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));
            if (peOffset < 64 || peOffset + 24 > input.length()) return null;
            input.seek(peOffset);
            if (input.readInt() != 0x50450000) return null;
            input.skipBytes(2);
            int sections = Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
            input.skipBytes(12);
            int optionalSize = Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
            long optional = peOffset + 24;
            long sectionStart = optional + optionalSize;
            if (optionalSize < 2 || sectionStart + sections * 40L > input.length()) return null;
            input.seek(optional);
            int magic = Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
            int directory = magic == 0x10b ? 112 : magic == 0x20b ? 128 : -1;
            if (directory < 0 || directory + 8 > optionalSize) return null;
            input.seek(optional + directory);
            resourcesRVA = Integer.reverseBytes(input.readInt());
            int size = Integer.reverseBytes(input.readInt());
            if (resourcesRVA <= 0 || size < 16 || size > 24 * 1024 * 1024) return null;

            for (int i = 0; i < sections; i++) {
                input.seek(sectionStart + i * 40L + 12);
                int sectionRVA = Integer.reverseBytes(input.readInt());
                int rawSize = Integer.reverseBytes(input.readInt());
                int rawOffset = Integer.reverseBytes(input.readInt());
                long delta = (long)resourcesRVA - sectionRVA;
                if (delta < 0 || delta + size > rawSize || rawOffset <= 0) continue;
                long offset = rawOffset + delta;
                if (offset > Integer.MAX_VALUE || offset + size > input.length()) return null;
                resourcesOffset = (int)offset;
                byte[] bytes = new byte[size];
                input.seek(offset);
                input.readFully(bytes);
                return new ImageResourceDirectory(type, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), 0,
                        new int[]{8192});
            }
        }
        catch (IOException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private ArrayList<ImageResourceDataEntry> readImageResourceDataEntries(ImageResourceDirectory rootDirectory) {
        ArrayList<ImageResourceDataEntry> dataEntries = new ArrayList<>();
        Stack<ImageResourceDirectory> stack = new Stack<>();
        stack.push(rootDirectory);
        while (!stack.isEmpty()) {
            ImageResourceDirectory directory = stack.pop();

            for (ImageResourceEntry entry : directory.entries) {
                if (entry instanceof ImageResourceDirectoryEntry) {
                    stack.push(((ImageResourceDirectoryEntry)entry).directory);
                }
                else if (entry instanceof ImageResourceDataEntry) {
                    dataEntries.add((ImageResourceDataEntry)entry);
                }
            }
        }
        return dataEntries;
    }

    private Bitmap decodeIcon(int iconIndex, boolean largeIcon, ArrayList<ImageResourceDataEntry> dataEntries) {
        for (int i = 0; i < dataEntries.size(); i++) {
            if (iconIndex >= 0 && i != iconIndex) continue;
            ImageResourceDataEntry dataEntry = dataEntries.get(i);
            int fileOffset = dataEntry.offsetToData - resourcesRVA + resourcesOffset;
            ByteBuffer iconData = readResourceData(fileOffset, dataEntry.size);
            if (iconData == null) continue;
            Bitmap bitmap = MSIcon.decodeByteArray(iconData.array(), 0, iconData.limit());
            if (bitmap == null) continue;
            if (iconIndex >= 0 || largeIcon == (bitmap.getWidth() >= 32)) return bitmap;
            bitmap.recycle();
        }

        return null;
    }

    private ImageResourceDirectory resourceType(byte type) {
        ImageResourceDirectory root = readImageResourceDirectory(type);
        if (root == null || root.entries.isEmpty()) return null;
        return ((ImageResourceDirectoryEntry)root.entries.get(0)).directory;
    }

    private ArrayList<Integer> shellIconImages(int index) {
        ArrayList<Integer> images = new ArrayList<>();
        ImageResourceDirectory groups = resourceType(RT_GROUP_ICON);
        if (groups == null) return images;
        for (int ordinal = 0; ordinal < groups.entries.size(); ordinal++) {
            ImageResourceEntry entry = groups.entries.get(groups.entries.size() - 1 - ordinal);
            if (!(entry instanceof ImageResourceDirectoryEntry)) continue;
            ImageResourceDirectoryEntry group = (ImageResourceDirectoryEntry)entry;
            if (index >= 0 ? index != ordinal : group.nameIsString || group.name != -(long)index) continue;
            for (ImageResourceDataEntry resource : readImageResourceDataEntries(group.directory)) {
                ByteBuffer data = readResourceData(resource.offsetToData - resourcesRVA + resourcesOffset, resource.size);
                if (data == null || data.remaining() < 6 || data.getShort() != 0 || data.getShort() != 1) continue;
                int count = Short.toUnsignedInt(data.getShort());
                if (count == 0 || count > 128 || data.remaining() < count * 14) continue;
                ArrayList<int[]> ranked = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int position = 6 + i * 14;
                    int width = Byte.toUnsignedInt(data.get(position));
                    int height = Byte.toUnsignedInt(data.get(position + 1));
                    int depth = Short.toUnsignedInt(data.getShort(position + 6));
                    int id = Short.toUnsignedInt(data.getShort(position + 12));
                    ranked.add(new int[]{id, (width == 0 ? 256 : width) * (height == 0 ? 256 : height), depth});
                }
                ranked.sort(Comparator.<int[]>comparingInt(image -> image[1]).thenComparingInt(image -> image[2]).reversed());
                for (int[] image : ranked) images.add(image[0]);
                return images;
            }
        }
        return images;
    }

    private Bitmap extractIcon(int iconIndex) {
        if (!peFile.isFile()) return null;

        ImageResourceDirectory rootDirectory = readImageResourceDirectory(RT_ICON);
        if (rootDirectory == null) return null;
        ArrayList<ImageResourceDataEntry> dataEntries = readImageResourceDataEntries(rootDirectory);

        if (iconIndex >= 0) {
            return decodeIcon(iconIndex, true, dataEntries);
        }
        else {
            Bitmap bitmap = decodeIcon(-1, true, dataEntries);
            if (bitmap != null) return bitmap;

            bitmap = decodeIcon(-1, false, dataEntries);
            if (bitmap != null) return bitmap;
        }

        return null;
    }

    public static FileVersionInfo getFileVersionInfo(File peFile) {
        try {
            return readFileVersionInfo(peFile);
        }
        catch (RuntimeException ignored) {
            return null;
        }
    }

    private static FileVersionInfo readFileVersionInfo(File peFile) {
        if (!peFile.isFile()) return null;

        PEParser peParser = new PEParser(peFile);
        ImageResourceDirectory rootDirectory = peParser.readImageResourceDirectory(RT_VERSION);
        if (rootDirectory == null) return null;
        ArrayList<ImageResourceDataEntry> dataEntries = peParser.readImageResourceDataEntries(rootDirectory);
        if (dataEntries.isEmpty()) return null;

        ImageResourceDataEntry dataEntry = dataEntries.get(0);
        int fileOffset = dataEntry.offsetToData - peParser.resourcesRVA + peParser.resourcesOffset;
        ByteBuffer resourceData = peParser.readResourceData(fileOffset, dataEntry.size);
        if (resourceData == null) return null;

        VSVersionInfo versionInfo = new VSVersionInfo(resourceData);

        if (versionInfo.stringFileInfo != null) {
            String preferred = "zh".equals(Locale.getDefault().getLanguage()) ? "0804" : "0409";
            ArrayList<StringTable> tables = versionInfo.stringFileInfo.stringTables;
            tables.sort(Comparator.comparingInt(table -> table.key.startsWith(preferred) ? 2
                    : table.key.startsWith("0409") ? 1 : 0));
            FileVersionInfo fileVersionInfo = new FileVersionInfo();
            for (StringTable stringTable : tables) {
                    for (StringHdr stringHdr : stringTable.stringHdrs) {
                        if (stringHdr.value == null || stringHdr.value.isEmpty()) continue;
                        switch (stringHdr.key) {
                            case "Comments":
                                fileVersionInfo.Comments = stringHdr.value;
                                break;
                            case "CompanyName":
                                fileVersionInfo.CompanyName = stringHdr.value;
                                break;
                            case "FileDescription":
                                fileVersionInfo.FileDescription = stringHdr.value;
                                break;
                            case "FileVersion":
                                fileVersionInfo.FileVersion = stringHdr.value;
                                break;
                            case "InternalName":
                                fileVersionInfo.InternalName = stringHdr.value;
                                break;
                            case "LegalCopyright":
                                fileVersionInfo.LegalCopyright = stringHdr.value;
                                break;
                            case "LegalTrademarks":
                                fileVersionInfo.LegalTrademarks = stringHdr.value;
                                break;
                            case "OriginalFilename":
                                fileVersionInfo.OriginalFilename = stringHdr.value;
                                break;
                            case "PrivateBuild":
                                fileVersionInfo.PrivateBuild = stringHdr.value;
                                break;
                            case "ProductName":
                                fileVersionInfo.ProductName = stringHdr.value;
                                break;
                            case "ProductVersion":
                                fileVersionInfo.ProductVersion = stringHdr.value;
                                break;
                            case "SpecialBuildprivate":
                                fileVersionInfo.SpecialBuildprivate = stringHdr.value;
                                break;
                        }
                    }
            }
            return fileVersionInfo;
        }
        else return null;
    }

    public static Bitmap extractIcon(File peFile) {
        return extractIcon(peFile, -1);
    }

    public static Bitmap extractIcon(File peFile, int iconIndex) {
        return (new PEParser(peFile)).extractIcon(iconIndex);
    }

    public static boolean hasShellIcon(File file, int index) {
        return file.isFile() && !(new PEParser(file)).shellIconImages(index).isEmpty();
    }

    public static Bitmap extractShellIcon(File file, int index) {
        if (!file.isFile()) return null;
        PEParser parser = new PEParser(file);
        ArrayList<Integer> images = parser.shellIconImages(index);
        ImageResourceDirectory icons = parser.resourceType(RT_ICON);
        if (icons == null) return null;
        for (int id : images) {
            for (ImageResourceEntry item : icons.entries) {
                if (!(item instanceof ImageResourceDirectoryEntry)) continue;
                ImageResourceDirectoryEntry image = (ImageResourceDirectoryEntry)item;
                if (image.name != id || image.nameIsString) continue;
                Bitmap bitmap = parser.decodeIcon(0, true, parser.readImageResourceDataEntries(image.directory));
                if (bitmap != null) return bitmap;
            }
        }
        ArrayList<ImageResourceDataEntry> fallback = parser.readImageResourceDataEntries(icons);
        Bitmap bitmap = parser.decodeIcon(-1, true, fallback);
        return bitmap != null ? bitmap : parser.decodeIcon(-1, false, fallback);
    }
}
