package app.windroid;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WinDroidModel extends AndroidViewModel {
    private static final String TAG = "WinDroid";
    private static final String INSTALL_SNAPSHOT = "install_snapshot";
    private static final ExecutorService WINDOWS_WORKER = Executors.newSingleThreadExecutor();
    private static final ExecutorService ICON_WORKER = Executors.newSingleThreadExecutor();
    private static boolean resetInProgress;

    final MutableLiveData<Status> status = new MutableLiveData<>();
    final MutableLiveData<Launch> launch = new MutableLiveData<>();
    final MutableLiveData<List<String>> runtimeResults = new MutableLiveData<>();
    final MutableLiveData<List<String>> installation = new MutableLiveData<>();
    final MutableLiveData<String> appInfoChanged = new MutableLiveData<>();
    final MutableLiveData<Boolean> installedAppsChanged = new MutableLiveData<>();
    final MutableLiveData<Boolean> customWallpaper = new MutableLiveData<>();
    final MutableLiveData<Integer> wallpaperNotice = new MutableLiveData<>();
    final MutableLiveData<Integer> resetNotice = new MutableLiveData<>();
    final MutableLiveData<String> uninstallNotice = new MutableLiveData<>();
    final MutableLiveData<UninstallPlan> uninstallPlan = new MutableLiveData<>();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final LruCache<String, WindowsAppInfo> appInfo = new LruCache<>(64);
    private final Set<String> loadingAppInfo = new HashSet<>();
    private int appInfoRevision;
    private WindowsEnvironment environment;
    private boolean busy;
    private boolean checkingInstalledApps;
    private final ArrayDeque<RuntimeChange> runtimeQueue = new ArrayDeque<>();
    private final List<String> runtimeOutcomes = new ArrayList<>();
    private RuntimeChange runtimeInFlight;
    private WindowsAppUninstall uninstallInFlight;
    private String uninstallEntry;
    private StoreDownload storeDownload;

    public WinDroidModel(Application application) {
        super(application);
        preferences = application.getSharedPreferences(WinDroidLibrary.PREFERENCES, Context.MODE_PRIVATE);
        customWallpaper.setValue(WinDroidWallpaper.hasCustom(application));
    }

    void changeWallpaper(@Nullable Uri photo) {
        Context context = getApplication();
        if (blocksWindowsLaunch(context)) return;
        WINDOWS_WORKER.execute(() -> {
            int message;
            try {
                if (photo == null) WinDroidWallpaper.restoreDefault(context);
                else WinDroidWallpaper.savePhoto(context, photo);
                message = R.string.windroid_wallpaper_updated;
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Unable to update wallpaper", error);
                message = R.string.windroid_wallpaper_save_failed;
            }
            int notice = message;
            main.post(() -> {
                customWallpaper.setValue(WinDroidWallpaper.hasCustom(context));
                wallpaperNotice.setValue(notice);
            });
        });
    }

    void prepare() {
        prepare(true);
    }

    void prepare(boolean inspectInstallation) {
        if (environment != null || busy || resetInProgress) return;
        boolean resetPending = WindowsEnvironment.hasPendingReset(getApplication());
        if (resetPending && !inspectInstallation) {
            report(R.string.wd_vm_reset_busy);
            return;
        }
        prepareEnvironment(inspectInstallation, resetPending);
    }

    static boolean isResetInProgress() {
        return resetInProgress;
    }

    public static boolean blocksWindowsLaunch(Context context) {
        return resetInProgress || WindowsEnvironment.hasPendingReset(context);
    }

    @StringRes int resetBlockedReason() {
        if (XServerDisplayActivity.hasWinDroidSession()) return R.string.wd_vm_reset_close_windows;
        return busy || resetInProgress ? R.string.wd_vm_reset_busy : 0;
    }

    @StringRes int resetWindows() {
        int blockedReason = resetBlockedReason();
        if (blockedReason != 0) return blockedReason;
        prepareEnvironment(false, true);
        return 0;
    }

    private void prepareEnvironment(boolean inspectInstallation, boolean reset) {
        Context context = getApplication();
        busy = true;
        if (reset) {
            resetInProgress = true;
            environment = null;
            launch.setValue(null);
            installation.setValue(null);
            appInfoRevision++;
            appInfo.evictAll();
            loadingAppInfo.clear();
        }
        status.setValue(new Status(reset ? R.string.wd_vm_resetting : R.string.wd_status_reading, Status.Kind.BUSY, -1));
        WINDOWS_WORKER.execute(() -> {
            try {
                if (reset) WindowsEnvironment.reset(context);
                WindowsEnvironment prepared = WindowsEnvironment.prepare(context, progress -> main.post(() -> {
                    int message = progress < 0 ? R.string.wd_status_preparing_disk : R.string.wd_status_unpacking;
                    status.setValue(new Status(message, Status.Kind.BUSY, progress));
                }));
                if (reset) WindowsEnvironment.finishReset(context);
                main.post(() -> {
                    environment = prepared;
                    if (reset) {
                        resetInProgress = false;
                        customWallpaper.setValue(false);
                    }
                    ready();
                    if (reset) resetNotice.setValue(R.string.wd_vm_reset_done);
                    else if (inspectInstallation) {
                        if (preferences.contains(INSTALL_SNAPSHOT)) finishInstallation(null);
                        else refreshInstalledApps();
                    }
                });
            }
            catch (Exception | LinkageError error) {
                Log.e(TAG, reset ? "Windows reset failed" : "Runtime preparation failed", error);
                main.post(() -> {
                    if (reset) {
                        resetInProgress = false;
                        customWallpaper.setValue(WinDroidWallpaper.hasCustom(context));
                        report(R.string.wd_vm_reset_failed);
                    }
                    else report(error, R.string.wd_error_prepare);
                });
            }
        });
    }

    void installFile(Uri uri) {
        if (!canOpen()) return;
        busy = true;
        status.setValue(new Status(R.string.wd_status_importing, Status.Kind.BUSY, -1));
        WINDOWS_WORKER.execute(() -> {
            try {
                launchInstaller(environment.importInstaller(uri), null);
            }
            catch (Exception error) {
                Log.e(TAG, "Executable import failed", error);
                main.post(() -> report(error, R.string.wd_error_read_selection));
            }
        });
    }

    void installFromStore(WinDroidStoreCatalog.App app, WinDroidStoreCatalog.Installer installer,
                          boolean preferMirrors) {
        if (!canOpen() || XServerDisplayActivity.hasWinDroidSession()) return;
        busy = true;
        StoreDownload download = new StoreDownload((host, progress) -> main.post(() -> {
            if (storeDownload != null && !storeDownload.isCancelled()) {
                status.setValue(new Status(host, progress));
            }
        }));
        storeDownload = download;
        status.setValue(new Status(R.string.wd_store_downloading, Status.Kind.BUSY, -1));
        WINDOWS_WORKER.execute(() -> {
            try {
                launchInstaller(environment.downloadInstaller(app, installer, download, preferMirrors), download);
            }
            catch (Exception error) {
                if (!download.isCancelled()) Log.w(TAG, "Store installer download failed", error);
                main.post(() -> {
                    storeDownload = null;
                    if (download.isCancelled()) ready();
                    else report(error, R.string.wd_store_download_error);
                });
            }
        });
    }

    void cancelStoreDownload() {
        if (storeDownload == null) return;
        storeDownload.cancel();
        status.setValue(new Status(R.string.wd_store_cancelling, Status.Kind.BUSY, -1));
    }

    private void launchInstaller(String path, StoreDownload download) throws IOException {
        File file = environment.resolve(path);
        String snapshot = new JSONObject(environment.programSnapshot()).toString();
        if (download != null) download.checkCancelled();
        if (!preferences.edit().putString(INSTALL_SNAPSHOT, snapshot).commit()) {
            throw new WinDroidException(R.string.wd_error_save_installation);
        }
        main.post(() -> {
            storeDownload = null;
            if (download != null && download.isCancelled()) {
                preferences.edit().remove(INSTALL_SNAPSHOT).apply();
                ready();
                return;
            }
            ready();
            launch.setValue(new Launch(file, true, path));
        });
    }

    boolean isRuntimeEnabled(WineRuntimePackage component) {
        return component.isEnabled(getApplication(), environment.drive());
    }

    void applyRuntimeChanges(Map<WineRuntimePackage, Boolean> changes) {
        if (!canOpen() || changes.isEmpty()) return;
        changes.forEach((component, enable) -> runtimeQueue.add(new RuntimeChange(component, enable)));
        busy = true;
        installNextRuntime();
    }

    private void installNextRuntime() {
        RuntimeChange change = runtimeQueue.pollFirst();
        if (change == null) {
            ready();
            runtimeResults.setValue(new ArrayList<>(runtimeOutcomes));
            runtimeOutcomes.clear();
            return;
        }
        status.setValue(new Status(R.string.wd_runtime_preparing, Status.Kind.BUSY, -1));
        WINDOWS_WORKER.execute(() -> {
            try {
                if (change.component == WineRuntimePackage.WEBVIEW) {
                    File archive = change.enable ? change.component.prepare(getApplication(), true, null) : null;
                    WebView2Runtime.change(environment.drive(), archive, change.enable, progress -> main.post(() ->
                            status.setValue(new Status(R.string.wd_runtime_extracting, Status.Kind.BUSY, progress))));
                    main.post(() -> {
                        Context context = WinDroidPreferences.wrapLanguage(getApplication());
                        addRuntimeOutcome(change.component, context.getString(change.enable
                                ? R.string.wd_runtime_installed : R.string.wd_runtime_disabled));
                        installNextRuntime();
                    });
                    return;
                }
                change.component.prepare(getApplication(), change.enable, progress -> main.post(() ->
                        status.setValue(new Status(R.string.wd_runtime_extracting, Status.Kind.BUSY, progress))));
                main.post(() -> {
                    runtimeInFlight = change;
                    status.setValue(new Status(change.enable ? R.string.wd_runtime_installing : R.string.wd_runtime_disabling,
                            Status.Kind.BUSY, -1));
                    launch.setValue(new Launch(change));
                });
            }
            catch (IOException | RuntimeException error) {
                Log.w(TAG, "Unable to prepare " + change.component.filename, error);
                int message = error instanceof WinDroidException ? ((WinDroidException)error).messageId
                        : R.string.wd_runtime_extract_failed;
                main.post(() -> {
                    addRuntimeOutcome(change.component, WinDroidPreferences.wrapLanguage(getApplication()).getString(message));
                    installNextRuntime();
                });
            }
        });
    }

    void finishRuntimeInstallation(Intent data) {
        RuntimeChange completed = runtimeInFlight;
        if (completed == null) return;
        runtimeInFlight = null;
        Context context = WinDroidPreferences.wrapLanguage(getApplication());
        String error = data == null ? null : data.getStringExtra(XServerDisplayActivity.WINDROID_ERROR);
        if (data != null && data.getBooleanExtra(XServerDisplayActivity.WINDROID_RUNTIME_INSTALLED, false)) {
            int message = completed.component == WineRuntimePackage.DIRECTX ? R.string.wd_runtime_installer_finished
                    : completed.component.record(getApplication(), environment.drive(), completed.enable)
                    ? completed.enable ? R.string.wd_runtime_installed : R.string.wd_runtime_disabled
                    : R.string.wd_runtime_state_not_confirmed;
            addRuntimeOutcome(completed.component, context.getString(message));
        }
        else if (error != null) addRuntimeOutcome(completed.component, error);
        else {
            addRuntimeOutcome(completed.component, context.getString(R.string.wd_runtime_install_cancelled));
            while (!runtimeQueue.isEmpty()) {
                addRuntimeOutcome(runtimeQueue.removeFirst().component, context.getString(R.string.wd_runtime_install_skipped));
            }
        }
        installNextRuntime();
    }

    private static final class RuntimeChange {
        final WineRuntimePackage component;
        final boolean enable;

        RuntimeChange(WineRuntimePackage component, boolean enable) {
            this.component = component;
            this.enable = enable;
        }
    }

    private void addRuntimeOutcome(WineRuntimePackage component, String outcome) {
        Context context = WinDroidPreferences.wrapLanguage(getApplication());
        String title = context.getString(R.string.wd_runtime_package_label, context.getString(component.title), component.version);
        runtimeOutcomes.add(context.getString(R.string.wd_runtime_install_outcome, title, outcome));
    }

    void prepareUninstall(String path, String label) {
        if (!canOpen()) return;
        if (!canChangeInstalledProgram()) return;
        busy = true;
        status.setValue(new Status(R.string.wd_uninstall_preparing, Status.Kind.BUSY, -1));
        WINDOWS_WORKER.execute(() -> {
            UninstallPlan plan = null;
            try {
                String target = environment.uninstallTarget(path);
                if (target != null) {
                    WindowsAppUninstall registered = environment.findUninstaller(target);
                    String folder = registered == null ? environment.removableProgramFolder(target, library()) : null;
                    plan = new UninstallPlan(path, label, target, folder, registered);
                }
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Unable to find app uninstaller", error);
            }
            UninstallPlan resolved = plan;
            main.post(() -> {
                ready();
                if (resolved == null) showUninstallNotice(R.string.wd_uninstall_unavailable);
                else uninstallPlan.setValue(resolved);
            });
        });
    }

    void uninstall(UninstallPlan plan, boolean removeFolder) {
        if (!canOpen() || !canChangeInstalledProgram()) return;
        busy = true;
        status.setValue(new Status(R.string.wd_uninstall_preparing, Status.Kind.BUSY, -1));
        WindowsEnvironment prepared = environment;
        WINDOWS_WORKER.execute(() -> {
            int message;
            try {
                if (!plan.target.equals(prepared.uninstallTarget(plan.entry))) {
                    throw new WinDroidException(R.string.wd_uninstall_changed);
                }
                WindowsAppUninstall current = prepared.findUninstaller(plan.target);
                if (plan.registered != null) {
                    if (current == null || !current.id.equals(plan.registered.id) || !current.payload.equals(plan.registered.payload)) {
                        throw new WinDroidException(R.string.wd_uninstall_changed);
                    }
                    main.post(() -> {
                        uninstallInFlight = current;
                        uninstallEntry = plan.entry;
                        launch.setValue(new Launch(current));
                    });
                    return;
                }
                if (current != null || removeFolder && (plan.folder == null
                        || !plan.folder.equals(prepared.removableProgramFolder(plan.target, library())))) {
                    throw new WinDroidException(R.string.wd_uninstall_changed);
                }
                prepared.removeProgramFiles(plan.target, removeFolder ? plan.folder : null);
                WinDroidLibrary.forgetUninstalledTarget(preferences, plan.entry, plan.target);
                message = R.string.wd_uninstall_files_removed;
            } catch (WinDroidException error) {
                message = error.messageId;
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Unable to remove app files", error);
                message = R.string.wd_uninstall_files_failed;
            }
            refreshAfterUninstall(prepared);
            int notice = message;
            main.post(() -> completeUninstall(uninstallContext().getString(notice)));
        });
    }

    private boolean canChangeInstalledProgram() {
        if (!XServerDisplayActivity.hasWinDroidSession()) return true;
        showUninstallNotice(R.string.windroid_runtime_session_active);
        return false;
    }

    private void showUninstallNotice(@StringRes int message) {
        uninstallNotice.setValue(uninstallContext().getString(message));
    }

    private Context uninstallContext() {
        return WinDroidPreferences.wrapLanguage(getApplication());
    }

    static final class UninstallPlan {
        final String entry;
        final String label;
        final String target;
        final String folder;
        final WindowsAppUninstall registered;

        UninstallPlan(String entry, String label, String target, String folder, WindowsAppUninstall registered) {
            this.entry = entry;
            this.label = label;
            this.target = target;
            this.folder = folder;
            this.registered = registered;
        }
    }

    void finishAppUninstall(@Nullable Intent result) {
        WindowsAppUninstall request = uninstallInFlight;
        String entry = uninstallEntry;
        uninstallInFlight = null;
        uninstallEntry = null;
        if (request == null) {
            refreshInstalledApps();
            return;
        }
        String error = result == null ? null : result.getStringExtra(XServerDisplayActivity.WINDROID_ERROR);
        boolean success = result != null && result.getBooleanExtra(XServerDisplayActivity.WINDROID_APP_UNINSTALLED, false);
        WindowsEnvironment prepared = environment;
        WINDOWS_WORKER.execute(() -> {
            int message = R.string.wd_uninstall_cancelled;
            try {
                if (success) {
                    if (prepared.removalConfirmed(request)) {
                        WinDroidLibrary.forgetUninstalledTarget(preferences, entry, request.target);
                        message = R.string.wd_uninstall_done;
                    } else message = R.string.wd_uninstall_unconfirmed;
                }
            } catch (IOException | RuntimeException failure) {
                Log.w(TAG, "Unable to verify app uninstall", failure);
                message = R.string.wd_uninstall_unconfirmed;
            }
            refreshAfterUninstall(prepared);
            String notice = error == null ? uninstallContext().getString(message) : error;
            main.post(() -> completeUninstall(notice));
        });
    }

    private void refreshAfterUninstall(WindowsEnvironment prepared) {
        try {
            reconcileInstalledApps(prepared, prepared.installedProgramLinks(prepared.programSnapshot().keySet()));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to refresh apps after uninstall", error);
        }
    }

    private void completeUninstall(String notice) {
        appInfoRevision++;
        appInfo.evictAll();
        loadingAppInfo.clear();
        ready();
        installedAppsChanged.setValue(true);
        uninstallNotice.setValue(notice);
    }

    void launchPinned(String path) {
        openProgram(path, false);
    }

    void launchHomeScreenShortcut(String path) {
        openProgram(path, true);
    }

    Intent windowsDesktopIntent(Context context) {
        return Launch.sessionIntent(context)
                .putExtra(XServerDisplayActivity.WINDROID_DESKTOP, true)
                .putStringArrayListExtra(XServerDisplayActivity.WINDROID_DESKTOP_LIBRARY, new ArrayList<>(library()));
    }

    private void openProgram(String path, boolean fromDesktop) {
        if (!canOpen()) return;
        busy = true;
        status.setValue(new Status(R.string.wd_status_checking_program, Status.Kind.BUSY, -1));
        WINDOWS_WORKER.execute(() -> {
            try {
                File file = environment.launchTarget(path);
                main.post(() -> {
                    if (!fromDesktop) ready();
                    launch.setValue(new Launch(file, false, path));
                });
            }
            catch (IOException error) {
                main.post(() -> report(error, R.string.wd_error_open_program));
            }
        });
    }

    WindowsAppInfo appInfo(String path) {
        WindowsAppInfo cached = appInfo.get(path);
        return cached == null ? new WindowsAppInfo(label(path), null) : cached;
    }

    boolean hasAppInfo(String path) {
        return appInfo.get(path) != null;
    }

    void loadAppInfo(String path) {
        WindowsEnvironment prepared = environment;
        if (prepared == null || appInfo.get(path) != null || !loadingAppInfo.add(path)) return;
        int revision = appInfoRevision;
        ICON_WORKER.execute(() -> {
            WindowsAppInfo info = WindowsAppInfo.read(prepared, path);
            main.post(() -> {
                if (revision != appInfoRevision) return;
                loadingAppInfo.remove(path);
                appInfo.put(path, info);
                appInfoChanged.setValue(path);
            });
        });
    }

    void finishInstallation(String launchError) {
        if (environment == null || busy || !preferences.contains(INSTALL_SNAPSHOT)) return;
        busy = true;
        status.setValue(new Status(R.string.wd_status_finding_shortcuts, Status.Kind.BUSY, -1));
        WindowsEnvironment prepared = environment;
        WINDOWS_WORKER.execute(() -> {
            JSONObject before = loadMap(INSTALL_SNAPSHOT);
            boolean changed = false;
            boolean foundNewApp = false;
            try {
                Map<String, String> snapshot = prepared.programSnapshot();
                Map<String, String> installed = prepared.installedProgramLinks(snapshot.keySet());
                changed = reconcileInstalledApps(prepared, installed);
                for (Map.Entry<String, String> entry : installed.entrySet()) {
                    if (!snapshot.get(entry.getKey()).equals(before.optString(entry.getKey()))
                            && WinDroidLibrary.hasVisibleTarget(preferences, entry.getValue())) {
                        foundNewApp = true;
                        break;
                    }
                }
            }
            catch (RuntimeException error) {
                Log.w(TAG, "Unable to detect newly installed Windows programs", error);
            }
            boolean detected = foundNewApp;
            boolean libraryChanged = changed;
            main.post(() -> {
                appInfoRevision++;
                appInfo.evictAll();
                loadingAppInfo.clear();
                if (launchError == null) ready();
                else report(launchError);
                if (libraryChanged) installedAppsChanged.setValue(true);
                if (detected) acknowledgeInstallation();
                else installation.setValue(new ArrayList<>());
            });
        });
    }

    void refreshInstalledApps() {
        WindowsEnvironment prepared = environment;
        if (prepared == null || busy || checkingInstalledApps) return;
        checkingInstalledApps = true;
        WINDOWS_WORKER.execute(() -> {
            boolean changed = false;
            try {
                changed = reconcileInstalledApps(prepared, prepared.installedProgramLinks(prepared.programSnapshot().keySet()));
            }
            catch (RuntimeException error) {
                Log.w(TAG, "Unable to detect installed Windows programs", error);
            }
            boolean libraryChanged = changed;
            main.post(() -> {
                checkingInstalledApps = false;
                if (libraryChanged) {
                    appInfoRevision++;
                    appInfo.evictAll();
                    loadingAppInfo.clear();
                    installedAppsChanged.setValue(true);
                }
            });
        });
    }

    private boolean reconcileInstalledApps(WindowsEnvironment prepared, Map<String, String> installed) {
        Map<String, String> manual = prepared.programIdentities(WinDroidLibrary.manualApps(preferences), installed);
        return WinDroidLibrary.updateInstalledApps(preferences, installed, manual);
    }

    List<String> availableProgramLinks() {
        return WinDroidLibrary.availableProgramLinks(preferences);
    }

    void acknowledgeInstallation() {
        preferences.edit().remove(INSTALL_SNAPSHOT).apply();
        installation.setValue(null);
    }

    List<String> library() {
        List<String> paths = WinDroidLibrary.apps(preferences);
        paths.sort(Comparator.comparing(WinDroidModel::label, String.CASE_INSENSITIVE_ORDER).thenComparing(path -> path));
        return paths;
    }

    boolean pin(String path) {
        return WinDroidLibrary.add(preferences, path);
    }

    boolean unpin(String path) {
        return WinDroidLibrary.remove(preferences, path);
    }

    List<String> recentApps() {
        return WinDroidLibrary.recent(preferences);
    }

    List<String> frequentApps() {
        return WinDroidLibrary.frequent(preferences);
    }

    List<String> homeFavorites() {
        return WinDroidLibrary.favorites(preferences);
    }

    boolean homeFavoritesCustomized() {
        return WinDroidLibrary.favoritesCustomized(preferences);
    }

    boolean resetHomeFavorites() {
        return WinDroidLibrary.resetFavorites(preferences);
    }

    boolean isHomeFavorite(String path) {
        return homeFavorites().contains(path);
    }

    boolean setHomeFavorite(String path, boolean favorite) {
        return WinDroidLibrary.setFavorite(preferences, path, favorite);
    }

    boolean setHomeFavorites(List<String> paths) {
        return WinDroidLibrary.setFavorites(preferences, paths);
    }

    boolean canOpen() {
        return environment != null && !busy && !blocksWindowsLaunch(getApplication());
    }

    WindowsEnvironment environment() {
        return environment;
    }

    void report(String message) {
        busy = false;
        status.setValue(new Status(message));
    }

    void report(@StringRes int messageId) {
        busy = false;
        status.setValue(new Status(messageId, Status.Kind.ERROR, -1));
    }

    void report(Throwable error, @StringRes int fallback) {
        report(error instanceof WinDroidException ? ((WinDroidException) error).messageId : fallback);
    }

    private void ready() {
        busy = false;
        status.setValue(new Status(R.string.wd_status_ready, Status.Kind.READY, -1));
    }

    private JSONObject loadMap(String key) {
        try {
            return new JSONObject(preferences.getString(key, "{}"));
        }
        catch (JSONException error) {
            Log.w(TAG, "Unable to read " + key, error);
            return new JSONObject();
        }
    }

    static String label(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.length() > 4 ? name.substring(0, name.length() - 4) : name;
    }

    static final class Status {
        enum Kind { BUSY, READY, ERROR }

        @StringRes private final int messageId;
        private final String message;
        private final String downloadHost;
        final Kind kind;
        final int progress;

        Status(@StringRes int messageId, Kind kind, int progress) {
            this.messageId = messageId;
            message = null;
            downloadHost = null;
            this.kind = kind;
            this.progress = progress;
        }

        Status(String message) {
            this.message = message;
            messageId = 0;
            downloadHost = null;
            kind = Kind.ERROR;
            progress = -1;
        }

        Status(String downloadHost, int progress) {
            messageId = R.string.wd_store_downloading;
            message = null;
            this.downloadHost = downloadHost;
            kind = Kind.BUSY;
            this.progress = progress;
        }

        String message(Context context) {
            if (messageId == 0) return message;
            if (downloadHost != null) {
                return progress >= 0 ? context.getString(R.string.wd_store_download_progress, downloadHost, progress)
                        : context.getString(R.string.wd_store_connecting, downloadHost);
            }
            if (messageId == R.string.wd_status_unpacking || messageId == R.string.wd_runtime_extracting) {
                return context.getString(messageId, progress);
            }
            return context.getString(messageId);
        }

        boolean isPreparingRuntime() {
            return messageId == R.string.wd_runtime_preparing || messageId == R.string.wd_runtime_extracting
                    || messageId == R.string.wd_runtime_installing || messageId == R.string.wd_runtime_disabling;
        }

        boolean isStoreDownload() {
            return messageId == R.string.wd_store_downloading || messageId == R.string.wd_store_cancelling;
        }

        boolean isCancellingStoreDownload() {
            return messageId == R.string.wd_store_cancelling;
        }
    }

    static final class Launch {
        private final File file;
        private final boolean installer;
        private final String entryPath;
        private final RuntimeChange change;
        private final WindowsAppUninstall uninstall;

        Launch(File file, boolean installer, String entryPath) {
            this.file = file;
            this.installer = installer;
            this.entryPath = entryPath;
            change = null;
            uninstall = null;
        }

        private Launch(RuntimeChange change) {
            this.change = change;
            uninstall = null;
            file = null;
            installer = true;
            entryPath = null;
        }

        private Launch(WindowsAppUninstall uninstall) {
            this.uninstall = uninstall;
            change = null;
            file = null;
            installer = false;
            entryPath = null;
        }

        boolean isAppUninstall() {
            return uninstall != null;
        }

        boolean isRuntimeInstaller() {
            return change != null;
        }

        Intent intent(Context context) {
            Intent intent = sessionIntent(context)
                    .putExtra(XServerDisplayActivity.WINDROID_INSTALLER, installer);
            if (uninstall != null) return intent.putExtra(XServerDisplayActivity.WINDROID_APP_UNINSTALL, uninstall.payload);
            if (change != null) {
                return intent.putExtra(XServerDisplayActivity.WINDROID_RUNTIME_COMMAND, change.component.command(change.enable))
                        .putExtra(XServerDisplayActivity.WINDROID_RUNTIME_DISABLE, !change.enable);
            }
            return intent.putExtra("exec_path", file.getAbsolutePath())
                    .putExtra(XServerDisplayActivity.WINDROID_ENTRY_PATH, entryPath);
        }

        private static Intent sessionIntent(Context context) {
            return new Intent(context, XServerDisplayActivity.class)
                    .putExtra("container_id", WindowsEnvironment.CONTAINER_ID)
                    .putExtra(XServerDisplayActivity.WINDROID_SESSION, true);
        }
    }
}
