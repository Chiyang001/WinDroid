package app.windroid;

import android.content.Context;

import com.winlator.R;
import com.winlator.core.EnvVars;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public enum WineRuntimePackage {
    MONO(R.string.wd_runtime_mono, "10.1.0", "wine-mono-10.1.0-x86.msi", 85_491_712,
            "c88c2431890bc32cacec8d7ea70e53a5ae4b95c8c55ca6e75ef8db0e4ccf1070",
            "{54ED3032-F8CB-5444-83B3-F51992D3EDC1}", "windows/mono/mono-2.0/bin/libmono-2.0-x86_64.dll"),
    GECKO_X86(R.string.wd_runtime_gecko_x86, "2.47.4", "wine-gecko-2.47.4-x86.msi", 55_187_968,
            "26cecc47706b091908f7f814bddb074c61beb8063318e9efc5a7f789857793d6",
            "{F220C345-2975-4A47-8686-3F2C41F773B7}", "windows/syswow64/gecko/2.47.4/wine_gecko/xul.dll"),
    GECKO_X64(R.string.wd_runtime_gecko_x64, "2.47.4", "wine-gecko-2.47.4-x86_64.msi", 53_898_752,
            "e590b7d988a32d6aa4cf1d8aa3aa3d33766fdd4cf4c89c2dcc2095ecb28d066f",
            "{AA28AA7D-CA50-44EB-AF34-7F99E4662CF1}", "windows/system32/gecko/2.47.4/wine_gecko/xul.dll"),
    VC12_X86(R.string.wd_runtime_vc12_x86, "12.0.40664", "vcredist_x86.exe", 6_510_136,
            "53b605d1100ab0a88b867447bbf9274b5938125024ba01f5105a9e178a3dcdbd", null, null),
    VC12_X64(R.string.wd_runtime_vc12_x64, "12.0.40664", "vcredist_x64.exe", 7_200_744,
            "a4bba7701e355ae29c403431f871a537897c363e215cafe706615e270984f17c", null, null),
    VC14_X86(R.string.wd_runtime_vc14_x86, "14.52.37029", "vc14-x86.zip", 6_281_011,
            "edffd6b925ac20bc10e6d8da10221b6e1c34fd28263ccbbddb2292f98a47d6e8", null, null),
    VC14_X64(R.string.wd_runtime_vc14_x64, "14.52.37029", "vc14-x64.zip", 6_951_980,
            "3785488fdf1bce3362321a2522408abf00e7f5400e263e95771982b044089c1e", null, null),
    DESKTOP_X86(R.string.wd_runtime_desktop_x86, "10.0.15", "windowsdesktop-runtime-10.0.15-win-x86.exe", 55_617_744,
            "0f55fc79174ea8daa11c339fba629b286ba6cadbb920a77de366b408b15315a5", null,
            "Program Files (x86)/dotnet/shared/Microsoft.WindowsDesktop.App/10.0.15/WindowsBase.dll"),
    DESKTOP_X64(R.string.wd_runtime_desktop_x64, "10.0.15", "windowsdesktop-runtime-10.0.15-win-x64.exe", 60_066_584,
            "e61b030c1673edfa3c0844502eff83198c46af9e7df70dfc6e2efc5b11ea513a", null,
            "Program Files/dotnet/shared/Microsoft.WindowsDesktop.App/10.0.15/WindowsBase.dll"),
    FRAMEWORK(R.string.wd_runtime_framework, "4.8", "framework48.zip", 128_454_318,
            "221f1f72ed5293c66e6ed4a72e21dc45bc56b47201e67fffff8d43b00580aab7"),
    DIRECTX(R.string.wd_runtime_directx, "June 2010", "dx-jun2010.exe", 100_275_120,
            "053f76dcbb28802e23341b6a787e3b0791c0fa5c8d4d011b1044172dbf89c73b"),
    WEBVIEW(R.string.wd_runtime_webview2, "157.0.4341.60", "webview2-fixed-x64.zip", 326_879_848,
            "f0f38bebc41b7365705542dada1f8a1d2f2937f91021ca7964a157c5bf69d379", null, null);

    // Match the versions and hashes compiled into Wine 10.10's appwiz.cpl.
    static final String PREFERENCES = "offline_runtimes";
    private static final String CACHE_DIRECTORY = "opt/windroid-runtime";
    private static final long STORAGE_RESERVE = 32L * 1024 * 1024;
    private static final String FRAMEWORK_INSTALL_COMMAND = "wine --framework48 install";
    private static final String[] VC14_FILES = {
            "concrt140", "msvcp140", "msvcp140_1", "msvcp140_2", "msvcp140_atomic_wait",
            "msvcp140_codecvt_ids", "vcamp140", "vccorlib140", "vcomp140", "vcruntime140",
            "vcruntime140_1", "vcruntime140_threads"
    };
    // Original files in the Microsoft Minimum and Additional cabinets, in VC14_FILES order.
    private static final String[] VC14_X86_HASHES = {
            "ee671a5577a341732b637a045bd44f4838793f3c2b6e0c63c1d0c85798cbcf8c",
            "400aa17deeaa4c051cb94131ca31aa1fcd5a25a39129ebc53b970b96594495fe",
            "2a823c7c10bd03fd972c81cc1d6a2806051afcdf176233d9ab4b04a671fa8438",
            "5ef1ca4779bb0baa5a5ccfb9fb0ed6864062dea3a979713ded7eba6db4fbddc9",
            "0ef3fa036c9e0f4afa42465682c276ca4eb409c52deb5d54ddd5862838de8032",
            "0aef48c328b6ccb3f11f74bf91fd9a36dd075d174eb37e65dd94818c87cdff67",
            "0e7af47ccbb30b4ce6b9d96b29d5b359a65fa0f32031044284dd97b914ab9f21",
            "38e4ceaf41322bbff1807660683d5beac7779984e34859ec916f7a5b78819abc",
            "4942b4068480132ad2102c47688610cafe46c85b25fe5ac4128c3766a316db35",
            "d097ea772560718f27c24d3e5788072b6abf8a8dadc3cee4335d7a7e52d9601d",
            null,
            "031436b3a87b066a8eedf235a6f60b2f33f62834e77b6eb07be3f67b5e445284"
    };
    private static final String[] VC14_X64_HASHES = {
            "8efda0374a4b4df975feeefbea3d86cafdd36802a0144c3d261295bd268071c7",
            "59d325227321d52b9a523ea401ce6787a5d59f78229a0ff3a6f124e5d3445685",
            "1c6522f4d005539109aa040f063659704bfda94a224142f50942573cfbcf7ec9",
            "56318c983acc3914f8523be61f6b1f1c91e6c1ae4b6c531169882aa59baaec6b",
            "8b10c6bd4c3c427ce7c8be96ef0fef0f8187bc236962be62a03caef834b37dcd",
            "695dbb4921cc1536429d238488cbee73581357a2fa05bb5c87b0f6ae48efcaca",
            "5f8760c808e0e56b80d8884072007703ae48494e91f37e4ec986bcfd6632cff6",
            "d247b63cf9b93a5769b5ac205e10fe29029c8ddce1b800159dc6061a36a1c149",
            "294397f642d824a97de354aa625f993a97f34d729eca1bde56d5a897ae574888",
            "eb1ee979bf02324712346e5bdbfc59c785388bc50d18cc2688aae4b0734915a0",
            "a14b4a42a2c6df04d6d53a8340816023d953406f7f3d94996c7cab30c414ff63",
            "44a0a0b925242699f2b31bf40a43b54ab3513584dfe7869521d1983e40ea1f06"
    };
    final int title;
    final String version;
    final String filename;
    final boolean selectable;
    private final String productCode;
    private final String installedFile;
    private final long size;
    private final String sha256;

    WineRuntimePackage(int title, String version, String filename, long size, String sha256) {
        this(title, version, filename, size, sha256, null, null, false);
    }

    WineRuntimePackage(int title, String version, String filename, long size, String sha256,
                       String productCode, String installedFile) {
        this(title, version, filename, size, sha256, productCode, installedFile, true);
    }

    WineRuntimePackage(int title, String version, String filename, long size, String sha256,
                       String productCode, String installedFile, boolean selectable) {
        this.title = title;
        this.version = version;
        this.filename = filename;
        this.size = size;
        this.sha256 = sha256;
        this.productCode = productCode;
        this.installedFile = installedFile;
        this.selectable = selectable;
    }

    boolean isEnabled(Context context, File drive) {
        if (isV14()) return hasV14Runtime(drive);
        if (this == WEBVIEW) return WebView2Runtime.isInstalled(drive);
        if (this == FRAMEWORK) return hasNativeFramework(drive);
        if (installedFile != null) return new File(drive, installedFile).isFile();
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(name(), false);
    }

    boolean record(Context context, File drive, boolean enabled) {
        if (this == FRAMEWORK) return hasNativeFramework(drive);
        if (isV14()) {
            if (!enabled && !restoreWineV14(context, drive)) return false;
            return hasV14Runtime(drive) == enabled;
        }
        if (installedFile != null) {
            if (!enabled && (this == GECKO_X86 || this == GECKO_X64)) {
                // Both upstream Gecko MSIs register the same core component GUID. The other
                // product can keep xul.dll alive after Windows Installer removes this product.
                File engine = new File(drive, installedFile);
                try {
                    if (!engine.equals(engine.getCanonicalFile())) return false;
                    Files.deleteIfExists(engine.toPath());
                }
                catch (IOException error) {
                    return false;
                }
            }
            return isEnabled(context, drive) == enabled;
        }
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(name(), enabled).commit();
    }

    File prepare(Context context, boolean enable, IntConsumer progress) throws IOException {
        File root = RootFS.find(context).getRootDir();
        if (!enable && productCode != null) {
            String addon = this == MONO ? "mono" : "gecko";
            Files.deleteIfExists(new File(root, "opt/wine/share/wine/" + addon + "/" + filename).toPath());
            Files.deleteIfExists(new File(root, RootFS.USER_CACHE_PATH + "/wine/" + filename).toPath());
            return null;
        }
        File folder = new File(root, CACHE_DIRECTORY);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create the Wine add-on cache");
        File installer = new File(folder, filename);
        File pending = new File(folder, filename + ".part");
        Files.deleteIfExists(pending.toPath());
        if (installer.length() == size) {
            try (InputStream input = new FileInputStream(installer)) {
                if (matches(input, null, null)) {
                    unpackInstaller(installer, progress);
                    return installer;
                }
            }
        }
        Files.deleteIfExists(installer.toPath());
        if (folder.getUsableSpace() < size + STORAGE_RESERVE) {
            throw new WinDroidException(R.string.wd_error_import_space);
        }

        try {
            try (InputStream input = context.getAssets().open("windroid/offline-runtimes/" + filename + ".wdpkg");
                 FileOutputStream output = new FileOutputStream(pending)) {
                if (!matches(input, output, progress)) throw new WinDroidException(R.string.wd_runtime_checksum_failed);
                output.getFD().sync();
            }
            Files.move(pending.toPath(), installer.toPath(), StandardCopyOption.ATOMIC_MOVE);
            unpackInstaller(installer, progress);
            return installer;
        }
        finally {
            Files.deleteIfExists(pending.toPath());
        }
    }

    private void unpackInstaller(File archive, IntConsumer progress) throws IOException {
        if (this == FRAMEWORK) {
            File target = new File(archive.getParentFile().getCanonicalFile(), "framework48");
            RuntimeArchive.deploy(archive, target, 277_028_193, 110, progress);
        }
        else if (isV14()) {
            unpackV14(archive);
        }
    }

    private void unpackV14(File archive) throws IOException {
        File target = new File(archive.getParentFile().getCanonicalFile(), filename.substring(0, filename.length() - 4));
        Set<String> remaining = new HashSet<>();
        remaining.add("Minimum/runtime.msi");
        remaining.add("Minimum/cab1.cab");
        remaining.add("Additional/runtime.msi");
        remaining.add("Additional/cab1.cab");
        long copied = 0;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(archive))) {
            byte[] buffer = new byte[128 * 1024];
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!remaining.remove(entry.getName())) throw new IOException("Unexpected VC++ package entry");
                File output = new File(target, entry.getName());
                if (!output.equals(output.getCanonicalFile())) throw new IOException("Invalid VC++ package path");
                Files.createDirectories(output.getParentFile().toPath());
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        copied += count;
                        if (copied > 24 * 1024 * 1024) throw new IOException("VC++ package is too large");
                        stream.write(buffer, 0, count);
                    }
                }
            }
        }
        if (!remaining.isEmpty()) throw new IOException("Incomplete VC++ package");
    }

    private boolean isV14() {
        return this == VC14_X86 || this == VC14_X64;
    }

    private boolean hasV14Runtime(File drive) {
        // The prefix starts with Wine's own vcruntime140.dll. Match the DLL from the bundled Microsoft MSI.
        boolean x86 = this == VC14_X86;
        String directory = x86 ? "syswow64" : "system32";
        File dll = new File(drive, "windows/" + directory + "/vcruntime140.dll");
        String expected = (x86 ? VC14_X86_HASHES : VC14_X64_HASHES)[9];
        try {
            return dll.length() == (x86 ? 124_264 : 176_488)
                    && digestMatches(newDigest().digest(Files.readAllBytes(dll.toPath())), expected);
        }
        catch (IOException error) {
            return false;
        }
    }

    private boolean restoreWineV14(Context context, File drive) {
        boolean x86 = this == VC14_X86;
        String[] hashes = x86 ? VC14_X86_HASHES : VC14_X64_HASHES;
        File system = new File(drive, x86 ? "windows/syswow64" : "windows/system32");
        File wine = new File(RootFS.find(context).getRootDir(),
                "opt/wine/lib/wine/" + (x86 ? "i386-windows" : "x86_64-windows"));
        try {
            if (!system.equals(system.getCanonicalFile())) return false;
            // MSI can leave DLLs behind when another Windows product references the component.
            // Replace only files that match the exact bundled version and only for the selected architecture.
            for (int i = 0; i < VC14_FILES.length; i++) {
                String name = VC14_FILES[i] + ".dll";
                File destination = new File(system, name);
                if (hashes[i] == null || !Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS)
                        || destination.length() > 1024 * 1024) continue;
                if (!digestMatches(newDigest().digest(Files.readAllBytes(destination.toPath())), hashes[i])) continue;
                File builtin = new File(wine, name);
                if (!builtin.isFile()) {
                    Files.delete(destination.toPath());
                    continue;
                }
                File temporary = File.createTempFile("builtin-", ".dll", system);
                try {
                    Files.copy(builtin.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }
                finally {
                    Files.deleteIfExists(temporary.toPath());
                }
            }
            return true;
        }
        catch (IOException error) {
            return false;
        }
    }

    private static boolean hasNativeFramework(File drive) {
        return new File(drive, "windows/system32/mscoree.dll").length() == 444_752
                && new File(drive, "windows/syswow64/mscoree.dll").length() == 297_808
                && new File(drive, "windows/Microsoft.NET/Framework64/v4.0.30319/clr.dll").length() >= 11_270_632
                && new File(drive, "windows/Microsoft.NET/Framework/v4.0.30319/clr.dll").length() >= 8_044_312;
    }

    public static void applyInstalledRuntimeEnvironment(File drive, EnvVars env, String command) {
        WebView2Runtime.applyEnvironment(drive, env);
        if (FRAMEWORK_INSTALL_COMMAND.equals(command)) {
            addDllOverrides(env, "fusion=b;mscoree,mshtml=d");
        }
        else if (hasNativeFramework(drive) && !new File(drive, MONO.installedFile).isFile()) {
            addDllOverrides(env, "mscoree=n");
        }
        if (VC14_X86.hasV14Runtime(drive) || VC14_X64.hasV14Runtime(drive)) {
            addDllOverrides(env, String.join(",", VC14_FILES) + "=n,b");
        }
    }

    private static void addDllOverrides(EnvVars env, String overrides) {
        String existing = env.get("WINEDLLOVERRIDES");
        env.put("WINEDLLOVERRIDES", existing.isEmpty() ? overrides : overrides + ";" + existing);
    }

    String command(boolean enable) {
        String path = "Z:\\opt\\windroid-runtime\\" + filename;
        if (productCode != null) {
            String system = this == GECKO_X86 ? "syswow64" : "system32";
            boolean gecko = this == GECKO_X86 || this == GECKO_X64;
            return "wine C:\\windows\\" + system + "\\msiexec.exe "
                    + (enable ? "/i " + path + (gecko ? " REINSTALLMODE=amus" : "") : "/x " + productCode)
                    + " REBOOT=ReallySuppress /qn";
        }
        if (this == FRAMEWORK) return FRAMEWORK_INSTALL_COMMAND;
        if (this == DIRECTX) return "wine " + path;
        if (isV14()) {
            return "wine --vc14-" + (this == VC14_X86 ? "x86" : "x64") + (enable ? " install" : " remove");
        }
        return "wine " + path + (enable ? " /install" : " /uninstall") + " /quiet /norestart";
    }

    private boolean matches(InputStream input, FileOutputStream output, IntConsumer progress) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[128 * 1024];
        int percent = -1;
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > size) return false;
            digest.update(buffer, 0, count);
            if (output != null) output.write(buffer, 0, count);
            int downloaded = (int)(total * 100 / size);
            if (progress != null && downloaded != percent) progress.accept(downloaded);
            percent = downloaded;
        }
        return total == size && digestMatches(digest.digest(), sha256);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    private static boolean digestMatches(byte[] hash, String sha256) {
        for (int i = 0; i < hash.length; i++) {
            if (Character.digit(sha256.charAt(i * 2), 16) != ((hash[i] & 0xff) >> 4)
                    || Character.digit(sha256.charAt(i * 2 + 1), 16) != (hash[i] & 0xf)) return false;
        }
        return true;
    }
}
