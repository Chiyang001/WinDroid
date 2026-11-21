package app.windroid;

import com.winlator.R;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiConsumer;

final class StoreDownload {
    private final BiConsumer<String, Integer> progress;
    private volatile boolean cancelled;
    private int lastProgress = -2;
    private String host;

    StoreDownload(BiConsumer<String, Integer> progress) {
        this.progress = progress;
    }

    void cancel() {
        cancelled = true;
    }

    boolean isCancelled() {
        return cancelled;
    }

    void checkCancelled() throws InterruptedIOException {
        if (cancelled) throw new InterruptedIOException("Store download cancelled");
    }

    void update(long done, long total) {
        int percent = total > 0 ? (int)Math.min(100, 100.0 * done / total) : -1;
        if (percent == lastProgress) return;
        lastProgress = percent;
        progress.accept(host, percent);
    }

    interface InstallerWriter {
        String save(InputStream input, long size) throws IOException;
    }

    interface SourceAttempt {
        String download(String address, boolean mirror) throws IOException;
    }

    String download(String origin, boolean preferMirrors, InstallerWriter writer) throws IOException {
        return trySources(origin, preferMirrors, (address, mirror) -> {
            HttpURLConnection connection;
            try {
                connection = connect(address, mirror);
            }
            catch (WinDroidException error) {
                throw error;
            }
            catch (IOException error) {
                throw new WinDroidException(R.string.wd_store_download_error, error);
            }
            try (InputStream input = responseBody(connection)) {
                return writer.save(input, connection.getContentLengthLong());
            }
            finally {
                connection.disconnect();
            }
        });
    }

    String trySources(String origin, boolean preferMirrors, SourceAttempt attempt) throws IOException {
        WinDroidException lastFailure = null;
        WinDroidException checksumFailure = null;
        for (String address : StoreDownloadSources.forInstaller(origin, preferMirrors)) {
            checkCancelled();
            host = new URL(address).getHost();
            lastProgress = -2;
            update(0, -1);
            try {
                return attempt.download(address, !origin.equals(address));
            }
            catch (WinDroidException error) {
                checkCancelled();
                if (error.messageId == R.string.wd_store_checksum_error) checksumFailure = error;
                else if (error.messageId != R.string.wd_store_server_error
                        && error.messageId != R.string.wd_store_download_error) throw error;
                lastFailure = error;
            }
        }
        throw checksumFailure != null ? checksumFailure : lastFailure;
    }

    private static InputStream responseBody(HttpURLConnection connection) throws IOException {
        InputStream input;
        try {
            input = connection.getInputStream();
        }
        catch (IOException error) {
            throw new WinDroidException(R.string.wd_store_download_error, error);
        }
        return new FilterInputStream(input) {
            @Override public int read() throws IOException {
                try {
                    return in.read();
                }
                catch (IOException error) {
                    throw new WinDroidException(R.string.wd_store_download_error, error);
                }
            }

            @Override public int read(byte[] data, int offset, int length) throws IOException {
                try {
                    return in.read(data, offset, length);
                }
                catch (IOException error) {
                    throw new WinDroidException(R.string.wd_store_download_error, error);
                }
            }

            @Override public void close() {
                try {
                    in.close();
                }
                catch (IOException ignored) {
                    // The response may already have been committed to a verified local file.
                }
            }
        };
    }

    HttpURLConnection connect(String address, boolean mirror) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            checkCancelled();
            if (!"https".equals(url.getProtocol()) || url.getHost().isEmpty() || url.getUserInfo() != null) {
                throw new WinDroidException(R.string.wd_store_download_error);
            }
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            boolean keep = false;
            try {
                connection.setConnectTimeout(mirror ? 8000 : 15000);
                connection.setReadTimeout(mirror ? 12000 : 15000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept-Encoding", "identity");
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_OK) {
                    keep = true;
                    return connection;
                }
                String location = connection.getHeaderField("Location");
                if (location == null || (status != 301 && status != 302 && status != 303
                        && status != 307 && status != 308)) {
                    throw new WinDroidException(R.string.wd_store_server_error, "Installer HTTP status " + status);
                }
                url = new URL(url, location);
            }
            finally {
                if (!keep) connection.disconnect();
            }
        }
        throw new WinDroidException(R.string.wd_store_server_error);
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    static boolean matchesSha256(byte[] digest, String expected) {
        if (expected.length() != digest.length * 2) return false;
        for (int i = 0; i < digest.length; i++) {
            if (Character.digit(expected.charAt(i * 2), 16) != ((digest[i] & 255) >>> 4)
                    || Character.digit(expected.charAt(i * 2 + 1), 16) != (digest[i] & 15)) return false;
        }
        return true;
    }
}
