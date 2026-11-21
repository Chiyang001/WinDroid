package app.windroid;

import com.winlator.R;

import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StoreDownloadTest {
    private static final String RELEASE = "https://github.com/ip7z/7zip/releases/download/26.06/7z2606-x64.msi";

    @Test
    public void onlyAcceptsTheExactInstallerDigest() {
        byte[] digest = StoreDownload.sha256().digest("abc".getBytes(StandardCharsets.US_ASCII));
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertTrue(StoreDownload.matchesSha256(digest, expected));
        assertFalse(StoreDownload.matchesSha256(digest, "f" + expected.substring(1)));
        assertFalse(StoreDownload.matchesSha256(digest, expected.substring(0, 63)));
    }

    @Test
    public void rejectsCleartextBeforeOpeningAConnection() {
        StoreDownload download = new StoreDownload((host, progress) -> {});
        assertThrows(WinDroidException.class, () -> download.connect("http://example.invalid/installer.exe", false));
    }

    @Test
    public void aCancelledDownloadCannotOpenAConnection() {
        StoreDownload download = new StoreDownload((host, progress) -> {});
        download.cancel();
        assertThrows(InterruptedIOException.class, () -> download.connect(RELEASE, false));
    }

    @Test
    public void prefersVerifiedMirrorRoutesForGitHubAndOtherKnownPublishers() {
        assertEquals(Arrays.asList("https://gh-proxy.com/" + RELEASE, "https://ghproxy.net/" + RELEASE, RELEASE),
                StoreDownloadSources.forInstaller(RELEASE, true));
        String vlc = "https://download.videolan.org/pub/videolan/vlc/3.0.24/win64/vlc-3.0.24-win64.msi";
        assertEquals("https://mirrors.ustc.edu.cn/videolan-ftp/vlc/3.0.24/win64/vlc-3.0.24-win64.msi",
                StoreDownloadSources.forInstaller(vlc, true).get(0));
        String sourceForge = "https://sourceforge.net/projects/example/files/1.0/Tool%20Setup.exe/download";
        assertEquals("https://gh-proxy.com/sourceforge/" + sourceForge,
                StoreDownloadSources.forInstaller(sourceForge, true).get(0));
    }

    @Test
    public void mirrorsOnlyPublicDownloadPathsAndHonorsOriginalSourcePreference() {
        assertEquals(Collections.singletonList(RELEASE), StoreDownloadSources.forInstaller(RELEASE, false));
        for (String url : Arrays.asList(RELEASE + "?key=test", RELEASE.replace("github.com", "github.com.example.org"),
                RELEASE.replace("github.com", "user@github.com"), RELEASE.replace("26.06", "latest%2Fprivate"),
                "https://example.org/installer.exe")) {
            assertEquals(Collections.singletonList(url), StoreDownloadSources.forInstaller(url, true));
        }
    }

    @Test
    public void retriesMirrorErrorsAndChecksumMismatchesBeforeGoingToTheOriginalSource() throws IOException {
        List<String> hosts = new ArrayList<>();
        StoreDownload download = new StoreDownload((host, progress) -> hosts.add(host));
        String installed = download.trySources(RELEASE, true, (address, mirror) -> {
            if (address.startsWith("https://gh-proxy.com/")) throw new WinDroidException(R.string.wd_store_server_error);
            if (mirror) throw new WinDroidException(R.string.wd_store_checksum_error);
            assertEquals(RELEASE, address);
            return "verified";
        });
        assertEquals("verified", installed);
        assertEquals(Arrays.asList("gh-proxy.com", "ghproxy.net", "github.com"), hosts);
    }

    @Test
    public void cancellationAndLocalWriteFailuresNeverStartAnotherSource() {
        List<String> hosts = new ArrayList<>();
        StoreDownload cancelled = new StoreDownload((host, progress) -> hosts.add(host));
        assertThrows(InterruptedIOException.class, () -> cancelled.trySources(RELEASE, true, (address, mirror) -> {
            cancelled.cancel();
            throw new WinDroidException(R.string.wd_store_download_error);
        }));
        assertEquals(Collections.singletonList("gh-proxy.com"), hosts);

        hosts.clear();
        StoreDownload outOfSpace = new StoreDownload((host, progress) -> hosts.add(host));
        WinDroidException failure = assertThrows(WinDroidException.class,
                () -> outOfSpace.trySources(RELEASE, true, (address, mirror) -> {
                    throw new WinDroidException(R.string.wd_error_import_space);
                }));
        assertEquals(R.string.wd_error_import_space, failure.messageId);
        assertEquals(Collections.singletonList("gh-proxy.com"), hosts);
    }
}
