package app.windroid;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class StoreDownloadSources {
    private static final String TUNA = "https://mirrors.tuna.tsinghua.edu.cn/";
    private static final String USTC = "https://mirrors.ustc.edu.cn/";
    private static final String NPM_MIRROR = "https://npmmirror.com/mirrors/";
    private static final String GH_PROXY = "https://gh-proxy.com/";
    private static final Pattern RELEASE_ASSET = Pattern.compile(
            "/[a-z0-9_.-]+/[a-z0-9_.-]+/releases/download/[^/]+/[^/]+\\.(?:exe|msi)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCEFORGE_FILE = Pattern.compile(
            "/projects/[a-z0-9_.-]+/files/(?:[^/]+/)*[^/]+\\.(?:exe|msi)/download",
            Pattern.CASE_INSENSITIVE);

    static List<String> forInstaller(String origin, boolean preferMirrors) {
        if (!preferMirrors) return Collections.singletonList(origin);
        URL official;
        try {
            official = new URL(origin);
        }
        catch (MalformedURLException error) {
            return Collections.singletonList(origin);
        }
        if (!"https".equals(official.getProtocol()) || official.getUserInfo() != null
                || official.getQuery() != null || official.getRef() != null
                || (official.getPort() != -1 && official.getPort() != 443)) {
            return Collections.singletonList(origin);
        }
        String path = official.getPath();
        String lower = path.toLowerCase(Locale.ROOT);
        if (path.indexOf('\\') >= 0 || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%2e") || path.contains("/../") || path.contains("/./")) {
            return Collections.singletonList(origin);
        }

        List<String> sources = new ArrayList<>(3);
        switch (official.getHost().toLowerCase(Locale.ROOT)) {
            case "github.com":
                if (RELEASE_ASSET.matcher(path).matches()) {
                    sources.add(GH_PROXY + "https://github.com" + path);
                    sources.add("https://ghproxy.net/https://github.com" + path);
                }
                break;
            case "sourceforge.net":
                if (SOURCEFORGE_FILE.matcher(path).matches()) {
                    sources.add(GH_PROXY + "sourceforge/https://sourceforge.net" + path);
                }
                break;
            case "download.videolan.org":
                addMirror(sources, path, "/pub/videolan/vlc/", USTC + "videolan-ftp/vlc/");
                addMirror(sources, path, "/pub/videolan/vlc/", TUNA + "videolan-ftp/vlc/");
                break;
            case "download.blender.org":
                addMirror(sources, path, "/release/", TUNA + "blender/release/");
                break;
            case "download.documentfoundation.org":
                addMirror(sources, path, "/libreoffice/stable/", TUNA + "libreoffice/libreoffice/stable/");
                break;
            case "nodejs.org":
                addMirror(sources, path, "/dist/", TUNA + "nodejs-release/");
                addMirror(sources, path, "/dist/", NPM_MIRROR + "node/");
                break;
            case "www.python.org":
                addMirror(sources, path, "/ftp/python/", NPM_MIRROR + "python/");
                break;
        }
        sources.add(origin);
        return sources;
    }

    private static void addMirror(List<String> sources, String path, String prefix, String mirror) {
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            sources.add(mirror + path.substring(prefix.length()));
        }
    }
}
