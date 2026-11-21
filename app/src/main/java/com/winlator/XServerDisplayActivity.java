package com.winlator;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.alsaserver.ALSAClient;
import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.ActiveWindowsDialog;
import com.winlator.contentdialog.AudioDriverConfigDialog;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.DXVKConfigDialog;
import com.winlator.contentdialog.DebugDialog;
import com.winlator.contentdialog.ScreenEffectDialog;
import com.winlator.contentdialog.TurnipConfigDialog;
import com.winlator.contentdialog.VKD3DConfigDialog;
import com.winlator.contentdialog.VirGLConfigDialog;
import com.winlator.contentdialog.WineD3DConfigDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.core.DesktopProgramLinks;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.GeneralComponents;
import com.winlator.core.KeyValueSet;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.ProcessHelper;
import com.winlator.core.StringUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.Win32AppWorkarounds;
import com.winlator.core.WineInfo;
import com.winlator.core.WineInstaller;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineGuestLocale;
import com.winlator.core.WineStartMenuCreator;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.math.Mathf;
import com.winlator.renderer.GLRenderer;
import com.winlator.services.ForegroundService;
import com.winlator.widget.FrameRating;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.MagnifierView;
import com.winlator.widget.TouchpadView;
import com.winlator.widget.WinDroidDock;
import com.winlator.widget.WinDroidKeyboardBar;
import com.winlator.widget.WinDroidPointerControls;
import com.winlator.widget.XServerView;
import com.winlator.winhandler.TaskManagerDialog;
import com.winlator.winhandler.WinHandler;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.ALSAServerComponent;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.xenvironment.components.NetworkInfoUpdateComponent;
import com.winlator.xenvironment.components.PulseAudioComponent;
import com.winlator.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.xenvironment.components.VirGLRendererComponent;
import com.winlator.xenvironment.components.VortekRendererComponent;
import com.winlator.xenvironment.components.XServerComponent;
import com.winlator.xserver.Atom;
import com.winlator.xserver.Property;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;

import app.windroid.WinDroidInputPreferences;
import app.windroid.WinDroidLibrary;
import app.windroid.WinDroidModel;
import app.windroid.WinDroidPreferences;
import app.windroid.WinDroidWallpaper;

public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final String WINDROID_SESSION = "windroid_session";
    public static final String WINDROID_ENTRY_PATH = "windroid_entry_path";
    public static final String WINDROID_INSTALLER = "windroid_installer";
    public static final String WINDROID_RUNTIME_COMMAND = "windroid_runtime_command";
    public static final String WINDROID_RUNTIME_DISABLE = "windroid_runtime_disable";
    public static final String WINDROID_RUNTIME_INSTALLED = "windroid_runtime_installed";
    public static final String WINDROID_APP_UNINSTALL = "windroid_app_uninstall";
    public static final String WINDROID_APP_UNINSTALLED = "windroid_app_uninstalled";
    private static final String RUNTIME_EXIT_FILE = "windroid-runtime-exit";
    public static final String WINDROID_DESKTOP = "windroid_desktop";
    public static final String WINDROID_DESKTOP_LIBRARY = "windroid_desktop_library";
    public static final String WINDROID_ERROR = "windroid_error";

    private XServerView xServerView;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private Container container;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private RootFS rootFS;
    private FrameRating frameRating;
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
    private String[] graphicsDriver = {GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER};
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private ScreenInfo screenInfo = new ScreenInfo(Container.DEFAULT_SCREEN_SIZE);
    private KeyValueSet[] dxwrapperConfig;
    private KeyValueSet[] graphicsDriverConfig = {new KeyValueSet(), new KeyValueSet()};
    private KeyValueSet audioDriverConfig;
    private String wincomponents;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private EnvVars overrideEnvVars;
    private ClipboardManager clipboardManager;
    private SharedPreferences preferences;
    private final WinHandler winHandler = new WinHandler(this);
    private float globalCursorSpeed = 1.0f;
    private boolean capturePointerOnExternalMouse = true;
    private MagnifierView magnifierView;
    private DebugDialog debugDialog;
    public int frameRatingWindowId = -1;
    private Win32AppWorkarounds win32AppWorkarounds;
    private String screenEffectProfile;
    private Context windroidUi;
    private View windroidInput;
    private WinDroidKeyboardBar windroidKeyboardBar;
    private WinDroidDock windroidDock;
    private WinDroidPointerControls windroidPointerControls;
    private boolean windroidControlsReady;
    private boolean windroidDockExpanded;
    private boolean windroidImeVisible;
    private boolean windroidFinishing;
    private boolean runtimePackageInstalled;
    private boolean appUninstallCompleted;
    private static XServerDisplayActivity activeWinDroidSession;
    private boolean sessionRegistered;
    private volatile boolean windroidWindowMapped;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        boolean windroidSession = isWinDroidSession();
        if (windroidSession) {
            setTheme(R.style.WinDroidRuntimeTheme);
            getDelegate().setLocalNightMode(WinDroidPreferences.nightMode(this));
        }
        else AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        if (windroidSession) {
            windroidUi = new ContextThemeWrapper(WinDroidPreferences.wrapLanguage(this), R.style.WinDroidRuntimeTheme);
            boolean resetting = WinDroidModel.blocksWindowsLaunch(this);
            if (resetting || activeWinDroidSession != null) {
                setResult(RESULT_CANCELED, new Intent().putExtra(WINDROID_ERROR,
                        windroidUi.getString(resetting ? R.string.wd_vm_reset_busy : R.string.windroid_runtime_session_active)));
                finish();
                return;
            }
            activeWinDroidSession = this;
        }
        sessionRegistered = true;
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);
        setContentView(R.layout.xserver_display_activity);
        ForegroundService.startSession(this);

        final PreloaderDialog preloaderDialog = new PreloaderDialog(this,
                windroidSession ? R.layout.windroid_runtime_preloader : R.layout.preloader_dialog);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useAndroidClipboardOnWine = preferences.getBoolean("use_android_clipboard_on_wine", false);
        clipboardManager = useAndroidClipboardOnWine ? (ClipboardManager)getSystemService(CLIPBOARD_SERVICE) : null;

        drawerLayout = findViewById(R.id.DrawerLayout);
        drawerLayout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (windroidDock != null) {
                WindowInsetsCompat insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets, view);
                windroidDock.applyInsets(insets);
                windroidImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                windroidKeyboardBar.applyInsets(insets);
                windroidPointerControls.applyInsets(insets);
                updateWinDroidKeyboardVisibility();
                updateWinDroidPointerControls();
            }
            return windowInsets.replaceSystemWindowInsets(0, 0, 0, 0);
        });
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        NavigationView navigationView = findViewById(R.id.NavigationView);
        if (windroidSession) configureWinDroidNavigation(navigationView);
        ProcessHelper.removeAllDebugCallbacks();
        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false) || preferences.getInt("box64_logs", 0) >= 1;
        if (enableLogs) ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.menu_item_logs).setVisible(enableLogs);
        navigationView.setNavigationItemSelectedListener(this);

        rootFS = RootFS.find(this);

        if (!isGenerateWineprefix()) {
            ContainerManager containerManager = new ContainerManager(this);
            container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));
            if (container == null && isWinDroidSession()) {
                finishWinDroidSession(windroidUi.getString(R.string.windroid_runtime_disk_unreadable), preloaderDialog);
                return;
            }
            containerManager.activateContainer(container);

            boolean wineprefixNeedsUpdate = container.getExtra("wineprefixNeedsUpdate").equals("t");
            if (wineprefixNeedsUpdate) {
                if (windroidSession) preloaderDialog.show(windroidUi.getText(R.string.windroid_updating_environment));
                else preloaderDialog.show(R.string.updating_system_files);
                WineUtils.updateWineprefix(this, (status) -> {
                    if (status == 0) {
                        container.putExtra("wineprefixNeedsUpdate", null);
                        container.putExtra("wincomponents", null);
                        container.saveData();
                        AppUtils.restartActivity(this);
                    }
                    else finish();
                });
                return;
            }

            win32AppWorkarounds = new Win32AppWorkarounds(this);

            String wineVersion = container.getWineVersion();
            wineInfo = WineInfo.fromIdentifier(this, wineVersion);

            if (wineInfo != WineInfo.MAIN_WINE_INFO) rootFS.setWinePath(wineInfo.path);

            String shortcutPath = getIntent().getStringExtra("shortcut_path");
            if (shortcutPath != null && !shortcutPath.isEmpty()) shortcut = new Shortcut(container, new File(shortcutPath));

            String graphicsDriver = container.getGraphicsDriver();
            audioDriver = container.getAudioDriver();
            String dxwrapper = container.getDXWrapper();
            wincomponents = container.getWinComponents();
            String dxwrapperConfig = container.getDXWrapperConfig();
            String graphicsDriverConfig = container.getGraphicsDriverConfig();
            audioDriverConfig = new KeyValueSet(container.getAudioDriverConfig());
            screenInfo = resolveScreenInfo(container.getScreenSize());

            if (shortcut != null) {
                graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
                audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
                dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
                wincomponents = shortcut.getExtra("wincomponents", container.getWinComponents());
                dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
                graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
                audioDriverConfig = new KeyValueSet(shortcut.getExtra("audioDriverConfig", container.getAudioDriverConfig()));
                screenInfo = resolveScreenInfo(shortcut.getExtra("screenSize", container.getScreenSize()));

                String dinputMapperType = shortcut.getExtra("dinputMapperType");
                if (!dinputMapperType.isEmpty()) winHandler.gamepadHandler.setDInputMapperType(Byte.parseByte(dinputMapperType));

                win32AppWorkarounds.applyStartupWorkarounds(!shortcut.wmClass.isEmpty() ? shortcut.wmClass : shortcut.path);
            }
            else {
                Intent intent = getIntent();
                if (intent.hasExtra("exec_path")) win32AppWorkarounds.applyStartupWorkarounds(FileUtils.getName(intent.getStringExtra("exec_path")));
            }

            this.graphicsDriver = GraphicsDrivers.parseIdentifiers(graphicsDriver);
            this.graphicsDriverConfig = GraphicsDrivers.parseConfigs(graphicsDriver, graphicsDriverConfig);
            this.dxwrapper = DXWrappers.parseIdentifier(dxwrapper);
            this.dxwrapperConfig = DXWrappers.parseConfigs(dxwrapper, dxwrapperConfig);
        }

        boolean desktop = isWinDroidDesktop();
        String trackedApp = windroidSession && !desktop && !getIntent().getBooleanExtra(WINDROID_INSTALLER, false)
                ? getIntent().getStringExtra(WINDROID_ENTRY_PATH) : null;
        if (windroidSession) {
            int message = getIntent().hasExtra(WINDROID_RUNTIME_COMMAND)
                    ? getIntent().getBooleanExtra(WINDROID_RUNTIME_DISABLE, false) ? R.string.wd_runtime_disabling : R.string.wd_runtime_installing
                    : getIntent().hasExtra(WINDROID_APP_UNINSTALL) ? R.string.wd_uninstall_preparing
                    : desktop ? R.string.windroid_starting_desktop : R.string.windroid_starting_program;
            preloaderDialog.show(windroidUi.getText(message));
        }
        else preloaderDialog.show(R.string.starting_up);

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(this, screenInfo);
        xServer.setWinHandler(winHandler);
        final boolean[] flags = {false, !desktop && (shortcut != null || getIntent().hasExtra("exec_path"))};
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (window.id == frameRatingWindowId) frameRating.update();
            }

            @Override
            public void onMapWindow(Window window) {
                if (!flags[0] && window.isRenderable() && !window.getClassName().isEmpty()) {
                    updateCursorVisibility();
                    if (windroidSession) runOnUiThread(() -> showWinDroidDock(preloaderDialog));
                    else preloaderDialog.closeOnUiThread();
                    flags[0] = true;
                }

                if (windroidSession && !windroidWindowMapped && window.isRenderable()
                        && (desktop || !window.isDesktopWindow()) && !window.getClassName().isEmpty()) {
                    windroidWindowMapped = true;
                    if (trackedApp != null) runOnUiThread(() -> WinDroidLibrary.recordLaunch(getApplicationContext(), trackedApp));
                }

                if (flags[1] && window.attributes.isViewable() && window.isDesktopWindow()) {
                    window.attributes.setViewable(false);
                    if (window.attributes.isEnabled()) window.disableAllDescendants();
                }

                if (win32AppWorkarounds != null) win32AppWorkarounds.applyWindowWorkarounds(window);
                changeFrameRatingVisibility(window, true);
            }

            @Override
            public void onUnmapWindow(Window window) {
                changeFrameRatingVisibility(window, false);
            }
        });

        setupUI();

        Point desktopDisplay = desktop ? getWinDroidDisplaySize() : null;
        java.util.concurrent.ExecutorService startup = Executors.newSingleThreadExecutor();
        startup.execute(() -> {
            try {
                Bitmap wallpaper = windroidSession ? WinDroidWallpaper.load(this) : null;
                if (wallpaper != null) xServerView.getRenderer().setBackground(wallpaper);
                WineGuestLocale guestLocale = windroidSession
                        ? WineGuestLocale.prepare(rootFS, WinDroidPreferences.usesChinese(windroidUi)) : null;
                if (!isGenerateWineprefix()) {
                    setupWineSystemFiles(guestLocale == WineGuestLocale.CHINESE);
                    if (desktopDisplay != null && wallpaper != null) {
                        WinDroidWallpaper.applyToDesktop(wallpaper, new File(rootFS.getRootDir(), RootFS.WINEPREFIX),
                                screenInfo, desktopDisplay);
                    }
                    extractGraphicsDriverFiles();
                    changeWineAudioDriver();
                }
                setupXEnvironment(preloaderDialog, guestLocale);
            }
            catch (RuntimeException | LinkageError error) {
                if (!isWinDroidSession()) throw error;
                Log.e("WinDroid", "Unable to start the Windows session", error);
                runOnUiThread(() -> finishWinDroidSession(
                        windroidUi.getString(R.string.windroid_runtime_start_failed), preloaderDialog));
            }
        });
        startup.shutdown();
        if (windroidSession) new Handler(Looper.getMainLooper())
                .postDelayed(() -> showWinDroidDock(preloaderDialog), 60000);
    }

    private void configureWinDroidNavigation(NavigationView navigation) {
        navigation.setItemIconTintList(ColorStateList.valueOf(getColor(R.color.windroid_secondary_text)));
        navigation.removeHeaderView(navigation.getHeaderView(0));
        navigation.inflateHeaderView(R.layout.windroid_runtime_menu_header);
        Menu menu = navigation.getMenu();
        int[][] itemTitles = {
                {R.id.menu_item_keyboard, R.string.windroid_keyboard},
                {R.id.menu_item_input_controls, R.string.windroid_runtime_menu_controls},
                {R.id.menu_item_toggle_fullscreen, R.string.windroid_runtime_menu_fullscreen},
                {R.id.menu_item_task_manager, R.string.windroid_runtime_menu_tasks},
                {R.id.menu_item_active_windows, R.string.windroid_runtime_menu_windows},
                {R.id.menu_item_magnifier, R.string.windroid_runtime_menu_magnifier},
                {R.id.menu_item_screen_effect, R.string.windroid_runtime_menu_effects},
                {R.id.menu_item_pip_mode, R.string.windroid_runtime_menu_pip},
                {R.id.menu_item_logs, R.string.windroid_runtime_menu_logs},
                {R.id.menu_item_touchpad_help, R.string.windroid_runtime_menu_touchpad},
                {R.id.menu_item_exit, R.string.windroid_exit}
        };
        for (int[] itemTitle : itemTitles) {
            menu.findItem(itemTitle[0]).setTitle(windroidUi.getText(itemTitle[1]));
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (editInputControlsCallback != null) {
                editInputControlsCallback.run();
                editInputControlsCallback = null;
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && windroidDock != null) touchpadView.cancelWinDroidGesture();

        if (hasFocus && touchpadView != null) {
            if (capturePointerOnExternalMouse && (windroidInput == null || !windroidInput.hasFocus())) {
                // Captured events are delivered to the focused view.
                View captureView = inputControlsView.getVisibility() == View.VISIBLE ? inputControlsView : touchpadView;
                if (captureView.requestFocus()) captureView.requestPointerCapture();
            }

            if (winHandler != null && clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                ClipData primaryClip = clipboardManager.getPrimaryClip();
                if (primaryClip != null && primaryClip.getItemCount() > 0) {
                    winHandler.setClipboardData(primaryClip.getItemAt(0).getText().toString());
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (windroidDock != null) applyWinDroidInputPreferences();
        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
        }
        if (sessionRegistered) ForegroundService.onResumeSession(this);
    }

    @Override
    public void onPause() {
        if (windroidDock != null) {
            touchpadView.cancelWinDroidGesture();
            windroidDock.onHostPaused();
        }
        if (sessionRegistered) ForegroundService.onPauseSession(this);
        super.onPause();
        if (environment != null && !isInPictureInPictureMode()) {
            environment.onPause();
            xServerView.onPause();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        ForegroundService.setPipMode(isInPictureInPictureMode);
        if (windroidDock != null) windroidDock.setPictureInPicture(isInPictureInPictureMode);
        if (isInPictureInPictureMode && windroidKeyboardBar != null) hideWinDroidKeyboard();
        updateWinDroidPointerControls();
    }

    @Override
    protected void onDestroy() {
        if (sessionRegistered) {
            winHandler.stop();
            if (environment != null) environment.stopEnvironmentComponents();
            ForegroundService.stopSession(this);
        }
        if (activeWinDroidSession == this) activeWinDroidSession = null;
        super.onDestroy();
    }

    public static boolean resumeWinDroidSession(Activity caller, String entryPath) {
        XServerDisplayActivity running = activeWinDroidSession;
        if (running == null) return false;
        if (!running.windroidFinishing && entryPath.equals(running.getIntent().getStringExtra(WINDROID_ENTRY_PATH))) {
            ActivityManager manager = caller.getSystemService(ActivityManager.class);
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                int id = Build.VERSION.SDK_INT >= 29 ? info.taskId : info.id;
                if (id == running.getTaskId()) {
                    task.moveToFront();
                    return true;
                }
            }
        }
        Toast.makeText(caller, WinDroidPreferences.wrapLanguage(caller).getText(R.string.windroid_runtime_session_active),
                Toast.LENGTH_SHORT).show();
        return true;
    }

    public static boolean hasWinDroidSession() {
        return activeWinDroidSession != null;
    }

    @Override
    public void onBackPressed() {
        if (windroidDock != null && windroidDock.collapseIfExpanded()) return;
        if (windroidKeyboardBar != null && windroidKeyboardBar.getVisibility() == View.VISIBLE) {
            hideWinDroidKeyboard();
            return;
        }
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
            else drawerLayout.closeDrawers();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (windroidDock != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            windroidDock.onOutsideDown(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        final GLRenderer renderer = xServerView.getRenderer();
        switch (item.getItemId()) {
            case R.id.menu_item_keyboard:
                drawerLayout.closeDrawers();
                if (windroidInput != null) showWinDroidKeyboard();
                else AppUtils.showKeyboard(this);
                break;
            case R.id.menu_item_input_controls:
                showInputControlsDialog();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_toggle_fullscreen:
                renderer.toggleFullscreen();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_task_manager:
                (new TaskManagerDialog(this)).show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_active_windows:
                (new ActiveWindowsDialog(this)).show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_magnifier:
                if (magnifierView == null) {
                    final FrameLayout container = findViewById(R.id.FLXServerDisplay);
                    magnifierView = new MagnifierView(this);
                    magnifierView.setZoomButtonCallback((value) -> {
                        renderer.setMagnifierZoom(Mathf.clamp(renderer.getMagnifierZoom() + value, 1.0f, 3.0f));
                        magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    });
                    magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    magnifierView.setHideButtonCallback(() -> {
                        container.removeView(magnifierView);
                        magnifierView = null;
                    });
                    container.addView(magnifierView);
                }
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_screen_effect:
                (new ScreenEffectDialog(this)).show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_pip_mode:
                PictureInPictureParams pipParams = (new PictureInPictureParams.Builder())
                    .setAspectRatio(screenInfo.aspectRatio())
                    .build();
                enterPictureInPictureMode(pipParams);
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_logs:
                debugDialog.show();
                drawerLayout.closeDrawers();
                break;
            case R.id.menu_item_touchpad_help:
                showTouchpadHelpDialog();
                break;
            case R.id.menu_item_exit:
                exit();
                break;
        }
        return true;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    private void exit() {
        if (isWinDroidSession()) {
            if (windroidFinishing) return;
            if (getIntent().hasExtra(WINDROID_APP_UNINSTALL)) {
                new AlertDialog.Builder(this, R.style.WinDroidDialogTheme)
                        .setTitle(windroidUi.getText(R.string.wd_uninstall_exit_prompt))
                        .setNegativeButton(windroidUi.getText(R.string.wd_cancel), null)
                        .setPositiveButton(windroidUi.getText(R.string.windroid_exit),
                                (dialog, which) -> finishWinDroidSession(null, null)).show();
            }
            else if (getIntent().getBooleanExtra(WINDROID_INSTALLER, false)) {
                new AlertDialog.Builder(this, R.style.WinDroidDialogTheme)
                        .setTitle(windroidUi.getText(R.string.windroid_runtime_exit_install_title))
                        .setMessage(windroidUi.getText(R.string.windroid_runtime_exit_install_message))
                        .setNegativeButton(windroidUi.getText(R.string.windroid_runtime_continue_install), null)
                        .setPositiveButton(windroidUi.getText(R.string.windroid_exit),
                                (dialog, which) -> finishWinDroidSession(null, null)).show();
            }
            else finishWinDroidSession(null, null);
            return;
        }
        winHandler.stop();
        if (environment != null) environment.stopEnvironmentComponents();

        Intent intent = getIntent();
        if (intent.hasExtra("exec_path")) {
            AppUtils.RestartApplicationOptions options = new AppUtils.RestartApplicationOptions();
            options.containerId = container.id;
            options.startPath = FileUtils.getDirname(intent.getStringExtra("exec_path"));
            AppUtils.restartApplication(this, options);
        }
        else AppUtils.restartApplication(this);
        ForegroundService.stopSession(this);
    }

    private boolean isWinDroidSession() {
        return getIntent().getBooleanExtra(WINDROID_SESSION, false);
    }

    private boolean isWinDroidDesktop() {
        return isWinDroidSession() && getIntent().getBooleanExtra(WINDROID_DESKTOP, false);
    }

    private Point getWinDroidDisplaySize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            return new Point(bounds.width(), bounds.height());
        }
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        return size;
    }

    private void finishWinDroidSession(String error, PreloaderDialog preloader) {
        if (windroidFinishing || isDestroyed()) return;
        windroidFinishing = true;
        if (windroidDock != null) {
            touchpadView.cancelWinDroidGesture();
            windroidDock.onHostPaused();
            updateWinDroidPointerControls();
        }
        if (preloader != null) preloader.close();
        if (windroidKeyboardBar != null) hideWinDroidKeyboard();
        GuestProgramLauncherComponent guest = environment == null ? null
                : environment.getComponent(GuestProgramLauncherComponent.class);
        if (guest == null) {
            completeWinDroidExit(error);
            return;
        }
        new Thread(() -> {
            guest.stopWineServer();
            runOnUiThread(() -> completeWinDroidExit(error));
        }, "windroid-close-wine").start();
    }

    private void completeWinDroidExit(String error) {
        if (isDestroyed()) return;
        winHandler.stop();
        if (environment != null) {
            environment.stopEnvironmentComponents();
            environment = null;
        }
        if (activeWinDroidSession == this) activeWinDroidSession = null;
        if (error != null) setResult(RESULT_CANCELED, new Intent().putExtra(WINDROID_ERROR, error));
        else setResult(RESULT_OK, new Intent().putExtra(WINDROID_RUNTIME_INSTALLED, runtimePackageInstalled)
                .putExtra(WINDROID_APP_UNINSTALLED, appUninstallCompleted));
        finish();
    }

    private void setupWineSystemFiles(boolean chinese) {
        String appVersion = String.valueOf(AppUtils.getVersionCode(this));
        String rfsVersion = String.valueOf(rootFS.getVersion());
        boolean containerDataChanged = false;

        boolean wineprefixWasUpdated = WineUtils.isWineprefixWasUpdated(container);
        if (!container.getExtra("appVersion").equals(appVersion) || !container.getExtra("rfsVersion").equals(rfsVersion) || wineprefixWasUpdated) {
            applyGeneralPatches(container);
            container.putExtra("appVersion", appVersion);
            container.putExtra("rfsVersion", rfsVersion);
            containerDataChanged = true;
        }

        if (verifyUserRegistry()) containerDataChanged = true;
        if (extractDXWrapperFiles()) containerDataChanged = true;

        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        if (!(desktopTheme+","+xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme+","+xServer.screenInfo);
            containerDataChanged = true;
        }

        Set<File> builtInLinks = WineStartMenuCreator.create(this, container, chinese);
        if (isWinDroidDesktop()) {
            try {
                DesktopProgramLinks.refresh(new File(container.getRootDir().getCanonicalFile(), ".wine/drive_c"),
                        getIntent().getStringArrayListExtra(WINDROID_DESKTOP_LIBRARY), builtInLinks);
            }
            catch (IOException error) {
                Log.w("WinDroid", "Unable to refresh the Windows desktop program links", error);
            }
        }
        WineUtils.createDosdevicesSymlinks(container, true);

        String startupSelection = String.valueOf(container.getStartupSelection());
        if (!startupSelection.equals(container.getExtra("startupSelection")) || wineprefixWasUpdated) {
            WineUtils.changeServicesStatus(container, container.getStartupSelection());
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }

        boolean openAndroidBrowserFromWine = preferences.getBoolean("open_android_browser_from_wine", true);
        String openAndroidBrowserFromWineStr = openAndroidBrowserFromWine ? "t" : "f";
        if (!openAndroidBrowserFromWineStr.equals(container.getExtra("openAndroidBrowserFromWine")) || wineprefixWasUpdated) {
            WineUtils.changeBrowsersRegistryKey(container, openAndroidBrowserFromWine);
            container.putExtra("openAndroidBrowserFromWine", openAndroidBrowserFromWineStr);
            containerDataChanged = true;
        }

        if (containerDataChanged) container.saveData();
    }

    private void setupXEnvironment(PreloaderDialog preloaderDialog, WineGuestLocale guestLocale) {
        String rootPath = rootFS.getRootDir().getPath();
        envVars.put("MESA_DEBUG", "silent");
        envVars.put("MESA_NO_ERROR", "1");
        envVars.put("WINEPREFIX", rootPath+RootFS.WINEPREFIX);
        envVars.put("WINE_DO_NOT_CREATE_DXGI_DEVICE_MANAGER", "1");

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels", SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty() ? "+"+wineDebugChannels.replace(",", ",+") : "-all");

        FileUtils.clear(rootFS.getTmpDir());

        GuestProgramLauncherComponent guestProgramLauncherComponent = new GuestProgramLauncherComponent();
        Intent intent = getIntent();
        String runtimeCommand = isWinDroidSession() ? intent.getStringExtra(WINDROID_RUNTIME_COMMAND) : null;
        String appUninstall = isWinDroidSession() ? intent.getStringExtra(WINDROID_APP_UNINSTALL) : null;
        String installerPath = intent.getStringExtra("exec_path");
        boolean importedMsi = isWinDroidSession() && intent.getBooleanExtra(WINDROID_INSTALLER, false)
                && installerPath != null && installerPath.toLowerCase(java.util.Locale.ROOT).endsWith(".msi");

        if (container != null) {
            if (container.getHUDMode() == FrameRating.Mode.FULL.ordinal()) envVars.put("X11_WND_GPU_INFO", "1");

            boolean desktop = isWinDroidDesktop();
            String desktopName = !desktop && (shortcut != null || getIntent().hasExtra("exec_path")) ? "nogui" : "shell";
            String guestExecutable;
            if (appUninstall != null) {
                try {
                    Files.write(new File(rootFS.getTmpDir(), "windroid-app-uninstall").toPath(),
                            appUninstall.getBytes(StandardCharsets.UTF_16LE));
                } catch (IOException error) {
                    throw new IllegalStateException("Unable to stage the Windows uninstaller", error);
                }
                guestExecutable = runtimeInstallerCommand("--app-uninstall");
            }
            else if (runtimeCommand != null) guestExecutable = runtimeInstallerCommand(runtimeCommand.substring("wine ".length()));
            else if (importedMsi) {
                String msi = WineUtils.unixToDOSPath(installerPath, container);
                guestExecutable = runtimeInstallerCommand("C:\\windows\\system32\\msiexec.exe /i "
                        + msi.replace(" ", "\\ ") + " REBOOT=ReallySuppress");
            }
            else {
                guestExecutable = "wine explorer /desktop="+desktopName+","+xServer.screenInfo;
                guestExecutable += " " + (desktop ? desktopSessionCommand() : getWineStartCommand());
            }
            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());
            if (shortcut != null) envVars.putAll(shortcut.getExtra("envVars"));
            if (!envVars.has("WINEESYNC")) envVars.put("WINEESYNC", "1");
            if (isWinDroidSession()) {
                app.windroid.WineRuntimePackage.applyInstalledRuntimeEnvironment(
                        new File(container.getRootDir(), ".wine/drive_c"), envVars, runtimeCommand);
            }

            guestProgramLauncherComponent.setBox64Preset(shortcut != null ? shortcut.getExtra("box64Preset", container.getBox64Preset()) : container.getBox64Preset());
        }

        environment = new XEnvironment(this, rootFS);
        environment.addComponent(new SysVSharedMemoryComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)));
        environment.addComponent(new XServerComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.XSERVER_PATH)));
        environment.addComponent(new NetworkInfoUpdateComponent());

        if (audioDriver.equals(AudioDrivers.ALSA)) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath+UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", ALSAClient.USE_SHARED_MEMORY ? "true" : "false");

            ALSAClient.Options options = ALSAClient.Options.fromKeyValueSet(audioDriverConfig);
            environment.addComponent(new ALSAServerComponent(UnixSocketConfig.create(rootPath, UnixSocketConfig.ALSA_SERVER_PATH), options));
        }
        else if (audioDriver.equals(AudioDrivers.PULSEAUDIO)) {
            PulseAudioComponent pulseAudioComponent = new PulseAudioComponent(UnixSocketConfig.create(rootPath, UnixSocketConfig.PULSE_SERVER_PATH));
            envVars.put("PULSE_SERVER", rootPath+UnixSocketConfig.PULSE_SERVER_PATH);

            if (!audioDriverConfig.isEmpty()) {
                envVars.put("PULSE_LATENCY_MSEC", audioDriverConfig.getInt("latencyMillis", AudioDriverConfigDialog.DEFAULT_LATENCY_MILLIS));
                pulseAudioComponent.setVolume(audioDriverConfig.getFloat("volume", AudioDriverConfigDialog.DEFAULT_VOLUME));
                pulseAudioComponent.setPerformanceMode(audioDriverConfig.getInt("performanceMode", AudioDriverConfigDialog.DEFAULT_PERFORMANCE_MODE));
            }
            else envVars.put("PULSE_LATENCY_MSEC", AudioDriverConfigDialog.DEFAULT_LATENCY_MILLIS);
            environment.addComponent(pulseAudioComponent);
        }

        if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK)) {
            VortekRendererComponent.Options options = VortekRendererComponent.Options.fromKeyValueSet(this, graphicsDriverConfig[0]);
            VortekRendererComponent vortekRendererComponent = new VortekRendererComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.VORTEK_SERVER_PATH), options);
            environment.addComponent(vortekRendererComponent);
        }
        if (graphicsDriver[1].equals(GraphicsDrivers.VIRGL)) {
            environment.addComponent(new VirGLRendererComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.VIRGL_SERVER_PATH)));
        }

        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback(status -> {
            if (!isWinDroidSession()) {
                exit();
                return;
            }
            runOnUiThread(() -> {
                if (!windroidFinishing && !isDestroyed()) {
                    String error = null;
                    if (status == -1) error = windroidUi.getString(R.string.windroid_runtime_start_failed);
                    else if (appUninstall != null) {
                        int windowsStatus = readRuntimeExitStatus();
                        appUninstallCompleted = windowsStatus == 0 || windowsStatus == 3010 || windowsStatus == 1641;
                        if (!appUninstallCompleted && windowsStatus != 1602 && windowsStatus != 1223) {
                            error = windroidUi.getString(R.string.wd_uninstall_failed, windowsStatus < 0 ? status : windowsStatus);
                        }
                    }
                    else if (runtimeCommand != null || importedMsi) {
                        int windowsStatus = readRuntimeExitStatus();
                        boolean alreadyAbsent = windowsStatus == 1605
                                && intent.getBooleanExtra(WINDROID_RUNTIME_DISABLE, false);
                        boolean installed = windowsStatus == 0 || windowsStatus == 3010 || alreadyAbsent;
                        runtimePackageInstalled = installed && runtimeCommand != null;
                        if (windowsStatus == -1) error = windroidUi.getString(R.string.wd_runtime_exit_missing, status);
                        else if (!installed && windowsStatus != 1602) {
                            error = windroidUi.getString(R.string.wd_runtime_msi_install_failed, windowsStatus);
                        }
                    }
                    else if (!windroidWindowMapped) error = windroidUi.getString(isWinDroidDesktop()
                            ? R.string.windroid_runtime_start_failed : R.string.windroid_runtime_no_window);
                    else if (status != 0) error = windroidUi.getString(R.string.windroid_runtime_exit_code, status);
                    finishWinDroidSession(error, preloaderDialog);
                }
            });
        });
        environment.addComponent(guestProgramLauncherComponent);

        if (isGenerateWineprefix()) {
            wineInfo = getIntent().getParcelableExtra("wine_info");
            if (wineInfo != null) WineInstaller.generateWineprefix(wineInfo, environment);
        }
        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars = null;
        }
        if (guestLocale != null) guestLocale.configure(rootFS, envVars);
        environment.startEnvironmentComponents();

        winHandler.start();
        envVars.clear();
        graphicsDriver = null;
        dxwrapperConfig = null;
        graphicsDriverConfig = null;
        audioDriver = null;
        audioDriverConfig = null;
        wincomponents = null;
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        final GLRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);
        renderer.setCursorColor(preferences.getInt("cursor_color", 0xffffff));
        renderer.setCursorScale(preferences.getFloat("cursor_scale", 1.0f));
        renderer.setForceWindowsFullscreen(shortcut != null && shortcut.getExtra("forceFullscreen", "0").equals("1"));

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        capturePointerOnExternalMouse = preferences.getBoolean("capture_pointer_on_external_mouse", true);
        touchpadView = new TouchpadView(this, xServer, capturePointerOnExternalMouse);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMoveCursorToTouchpoint(preferences.getBoolean("move_cursor_to_touchpoint", false));
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.openDrawer(GravityCompat.START);
        });
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this);
        inputControlsView.setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        inputControlsView.setTouchpadView(touchpadView);
        if (capturePointerOnExternalMouse) {
            inputControlsView.setOnCapturedPointerListener((view, event) ->
                touchpadView.isEnabled() && touchpadView.onCapturedPointer(view, event));
        }
        inputControlsView.setXServer(xServer);
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);

        if (container != null && container.getHUDMode() != FrameRating.Mode.DISABLED.ordinal()) {
            frameRating = new FrameRating(this);
            frameRating.setMode(FrameRating.Mode.values()[container.getHUDMode()]);
            frameRating.setVisibility(View.GONE);
            rootView.addView(frameRating);
        }

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null) showInputControls(profile);
            }
        }

        if (MainActivity.DEBUG_MODE) rootView.addView(AppUtils.createDebugMsgTextView(this));
        AppUtils.observeSoftKeyboardVisibility(drawerLayout, visible -> {
            renderer.setScreenOffsetYRelativeToCursor(visible);
            if (windroidDock != null) windroidDock.setKeyboardVisibleFromLayout(visible);
            updateWinDroidKeyboardVisibility();
            updateWinDroidPointerControls();
        });
        if (isWinDroidSession()) addWinDroidControls(rootView);
    }

    private void addWinDroidControls(FrameLayout root) {
        windroidInput = new View(this) {
            @Override
            public boolean onCheckIsTextEditor() {
                return true;
            }

            @Override
            public InputConnection onCreateInputConnection(EditorInfo attrs) {
                attrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
                attrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
                return new BaseInputConnection(this, true) {
                    @Override
                    public boolean commitText(CharSequence text, int newCursorPosition) {
                        getEditable().clear();
                        type(text);
                        return true;
                    }

                    @Override
                    public boolean finishComposingText() {
                        String remaining = getEditable().toString();
                        getEditable().clear();
                        type(remaining);
                        return true;
                    }

                    @Override
                    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                        for (int i = 0; i < Math.min(beforeLength, 64); i++) sendKey(KeyEvent.KEYCODE_DEL);
                        for (int i = 0; i < Math.min(afterLength, 64); i++) sendKey(KeyEvent.KEYCODE_FORWARD_DEL);
                        return true;
                    }

                    @Override
                    public boolean performEditorAction(int editorAction) {
                        sendKey(KeyEvent.KEYCODE_ENTER);
                        return true;
                    }

                    private void type(CharSequence text) {
                        KeyCharacterMap map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
                        for (int i = 0; i < text.length(); i++) {
                            char character = text.charAt(i);
                            KeyEvent[] events = map.getEvents(new char[]{character});
                            if (events != null) {
                                for (KeyEvent event : events) xServer.keyboard.onKeyEvent(event);
                            }
                            else xServer.keyboard.onKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), String.valueOf(character),
                                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0));
                        }
                    }

                    private void sendKey(int keyCode) {
                        xServer.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                        xServer.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                    }
                };
            }
        };
        windroidInput.setFocusableInTouchMode(true);
        windroidInput.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(windroidInput, new FrameLayout.LayoutParams(1, 1));
        // Keep an entire gesture with the view that received its first finger.
        root.setMotionEventSplittingEnabled(false);
        windroidKeyboardBar = new WinDroidKeyboardBar(windroidUi, xServer, this::hideWinDroidKeyboard);
        windroidPointerControls = new WinDroidPointerControls(windroidUi, preferences, xServer);
        root.addView(windroidPointerControls, new FrameLayout.LayoutParams(-1, -1));
        root.addView(windroidKeyboardBar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        windroidDock = new WinDroidDock(windroidUi, preferences, this::showWinDroidKeyboard,
                () -> WinDroidInputPreferences.show(this, windroidUi, this::applyWinDroidInputPreferences), this::exit);
        windroidDock.setExpansionListener(expanded -> {
            windroidDockExpanded = expanded;
            updateWinDroidPointerControls();
        });
        root.addView(windroidDock, new FrameLayout.LayoutParams(-1, -1));
        applyWinDroidInputPreferences();
        ViewCompat.requestApplyInsets(drawerLayout);
    }

    private void showWinDroidDock(PreloaderDialog preloader) {
        preloader.close();
        if (windroidDock == null || windroidFinishing || isDestroyed()) return;
        windroidControlsReady = true;
        windroidDock.showReady();
        updateWinDroidPointerControls();
    }

    private void applyWinDroidInputPreferences() {
        touchpadView.setWinDroidDirectTouch(WinDroidInputPreferences.mode(windroidUi) == WinDroidInputPreferences.Mode.DIRECT);
        updateWinDroidPointerControls();
    }

    private void updateWinDroidPointerControls() {
        if (windroidPointerControls == null) return;
        boolean visible = windroidControlsReady && !windroidFinishing && !windroidDockExpanded && !windroidImeVisible
                && windroidKeyboardBar.getVisibility() != View.VISIBLE
                && !xServerView.getRenderer().isScreenOffsetYRelativeToCursor() && !isInPictureInPictureMode()
                && inputControlsView.getVisibility() != View.VISIBLE
                && WinDroidInputPreferences.mode(windroidUi) == WinDroidInputPreferences.Mode.TRACKPAD;
        windroidPointerControls.setShownControls(
                visible && WinDroidInputPreferences.showLeftButton(windroidUi),
                visible && WinDroidInputPreferences.showRightButton(windroidUi),
                visible && WinDroidInputPreferences.showWheel(windroidUi));
    }

    private void showWinDroidKeyboard() {
        if (windroidFinishing || isInPictureInPictureMode()) return;
        windroidKeyboardBar.show();
        updateWinDroidKeyboardVisibility();
        updateWinDroidPointerControls();
        if (windroidInput.requestFocus()) {
            windroidInput.post(() -> {
                if (windroidKeyboardBar.getVisibility() == View.VISIBLE) {
                    getSystemService(InputMethodManager.class).showSoftInput(windroidInput, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private void updateWinDroidKeyboardVisibility() {
        if (windroidKeyboardBar == null) return;
        boolean visibleFromLayout = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && xServerView.getRenderer().isScreenOffsetYRelativeToCursor();
        windroidKeyboardBar.setImeVisible(windroidImeVisible || visibleFromLayout);
    }

    private void hideWinDroidKeyboard() {
        windroidKeyboardBar.dismiss();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(windroidInput.getWindowToken(), 0);
        updateWinDroidPointerControls();
    }

    private void showInputControlsDialog() {
        final ContentDialog dialog = new ContentDialog(this, R.layout.input_controls_dialog);
        dialog.setTitle(R.string.input_controls);
        dialog.setIcon(R.drawable.icon_input_controls);

        final Spinner sProfile = dialog.findViewById(R.id.SProfile);
        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- "+getString(R.string.disabled)+" --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (profile == inputControlsView.getProfile()) selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }

            sProfile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profileItems));
            sProfile.setSelection(selectedPosition);
        };
        loadProfileSpinner.run();

        final CheckBox cbRelativeMouseMovement = dialog.findViewById(R.id.CBRelativeMouseMovement);
        cbRelativeMouseMovement.setChecked(xServer.isRelativeMouseMovement());

        final CheckBox cbShowTouchscreenControls = dialog.findViewById(R.id.CBShowTouchscreenControls);
        cbShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());

        dialog.findViewById(R.id.BTSettings).setOnClickListener((v) -> {
            int position = sProfile.getSelectedItemPosition();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("edit_input_controls", true);
            intent.putExtra("selected_profile_id", position > 0 ? inputControlsManager.getProfiles().get(position - 1).id : 0);
            editInputControlsCallback = () -> {
                hideInputControls();
                inputControlsManager.loadProfiles(true);
                loadProfileSpinner.run();
            };
            startActivityForResult(intent, MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE);
        });

        dialog.setOnConfirmCallback(() -> {
            xServer.setRelativeMouseMovement(cbRelativeMouseMovement.isChecked());
            inputControlsView.setShowTouchscreenControls(cbShowTouchscreenControls.isChecked());
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            }
            else hideInputControls();
        });

        dialog.show();
    }

    private void showInputControls(ControlsProfile profile) {
        touchpadView.cancelWinDroidGesture();
        inputControlsView.setVisibility(View.VISIBLE);
        inputControlsView.requestFocus();
        inputControlsView.setProfile(profile);

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonRightEnabled(false);

        touchpadView.setEnabled(!profile.isDisableMouseInput());
        updateCursorVisibility();
        updateWinDroidPointerControls();

        inputControlsView.invalidate();
    }

    private void hideInputControls() {
        touchpadView.cancelWinDroidGesture();
        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        touchpadView.setEnabled(true);
        updateCursorVisibility();
        updateWinDroidPointerControls();

        inputControlsView.invalidate();
    }

    private void updateCursorVisibility() {
        ControlsProfile profile = inputControlsView.getProfile();
        xServerView.getRenderer().setCursorVisible(profile == null || profile.isCursorVisible());
    }

    private void extractGraphicsDriverFiles() {
        envVars.put("vblank_mode", "0");

        String cacheId = "";
        if (graphicsDriver[0].equals(GraphicsDrivers.TURNIP)) {
            cacheId += graphicsDriver[0]+"-"+graphicsDriverConfig[0].get("version", DefaultVersion.TURNIP);
        }
        else cacheId += graphicsDriver[0]+"-"+DefaultVersion.valueOf(graphicsDriver[0]);
        cacheId += "-"+graphicsDriver[1]+"-"+DefaultVersion.valueOf(graphicsDriver[1]);

        String currentGraphicsDriver = preferences.getString("current_graphics_driver", "");
        boolean changed = !cacheId.equals(currentGraphicsDriver);
        File rootDir = rootFS.getRootDir();
        File libDir = rootFS.getLibDir();

        if (changed) {
            FileUtils.delete(new File(libDir, "libvulkan_freedreno.so"));
            FileUtils.delete(new File(libDir, "libvulkan_vortek.so"));
            FileUtils.delete(new File(libDir, "libGL.so.1.7.0"));

            File vulkanICDDir = new File(rootDir, "/usr/share/vulkan/icd.d");
            FileUtils.delete(vulkanICDDir);
            vulkanICDDir.mkdirs();

            preferences.edit().putString("current_graphics_driver", cacheId).apply();
        }

        if (graphicsDriver[0].equals(GraphicsDrivers.TURNIP)) {
            TurnipConfigDialog.setEnvVars(this, graphicsDriverConfig[0], envVars);

            if (changed) {
                String version = graphicsDriverConfig[0].get("version", DefaultVersion.TURNIP);
                GeneralComponents.extractFile(GeneralComponents.Type.TURNIP, this, version, DefaultVersion.TURNIP);
            }
        }
        else if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK) && (changed || MainActivity.DEBUG_MODE)) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/vortek-" + DefaultVersion.VORTEK + ".tzst", rootDir);
        }

        switch (graphicsDriver[1]) {
            case GraphicsDrivers.ZINK:
                envVars.put("GALLIUM_DRIVER", "zink");
                envVars.put("ZINK_CONTEXT_THREADED", "1");
                if (graphicsDriver[0].equals(GraphicsDrivers.VORTEK)) envVars.put("MESA_GL_VERSION_OVERRIDE", "3.3");

                if (changed) TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/zink-"+DefaultVersion.ZINK+".tzst", rootDir);
                break;
            case GraphicsDrivers.VIRGL:
                envVars.put("GALLIUM_DRIVER", "virpipe");
                envVars.put("VIRGL_NO_READBACK", "true");
                envVars.put("VIRGL_SERVER_PATH", rootDir+UnixSocketConfig.VIRGL_SERVER_PATH);
                VirGLConfigDialog.setEnvVars(graphicsDriverConfig[1], envVars);

                if (changed) TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/virgl-"+DefaultVersion.VIRGL+".tzst", rootDir);
                break;
            case GraphicsDrivers.GLADIO:
                envVars.put("GLADIO_NO_ERROR", "1");

                if (changed || MainActivity.DEBUG_MODE) TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/gladio-"+DefaultVersion.GLADIO+".tzst", rootDir);
                break;
        }
    }

    private void showTouchpadHelpDialog() {
        ContentDialog dialog = new ContentDialog(this, R.layout.touchpad_help_dialog);
        dialog.setTitle(R.string.touchpad_help);
        dialog.setIcon(R.drawable.icon_help);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        dialog.show();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return !winHandler.onGenericMotionEvent(event) && !touchpadView.onExternalMouseEvent(event) && super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event) && xServer.keyboard.onKeyEvent(event)) ||
               (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
    }

    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    private boolean extractDXWrapperFiles() {
        String cacheId = "";
        if (dxwrapper.equals(DXWrappers.DXVK)) {
            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig[0], envVars);
            cacheId += dxwrapper+"-"+dxwrapperConfig[0].get("version", DefaultVersion.DXVK(graphicsDriver[0]));
        }
        else if (dxwrapper.equals(DXWrappers.WINED3D)) {
            WineD3DConfigDialog.setEnvVars(dxwrapperConfig[0], envVars);
            cacheId += dxwrapper+"-"+dxwrapperConfig[0].get("version", DefaultVersion.WINED3D);
        }

        String ddrawWrapper = dxwrapperConfig[0].get("ddrawWrapper", DXWrappers.WINED3D);
        cacheId += "-"+DXWrappers.VKD3D+"-"+dxwrapperConfig[1].get("version", DefaultVersion.VKD3D)+"-"+ddrawWrapper;
        boolean changed = !cacheId.equals(container.getExtra("dxwrapper"));
        VKD3DConfigDialog.setEnvVars(dxwrapperConfig[1], envVars);

        if (ddrawWrapper.equals(DXWrappers.CNC_DDRAW)) envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\ProgramData\\cnc-ddraw\\ddraw.ini");

        if (!changed) return false;
        container.putExtra("dxwrapper", cacheId);

        File rootDir = rootFS.getRootDir();
        File windowsDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");

        if (dxwrapper.equals(DXWrappers.WINED3D)) {
            String version = dxwrapperConfig[0].get("version", DefaultVersion.WINED3D);
            if (version.equals(WineInfo.MAIN_WINE_VERSION)) {
                final String[] dlls = {"d3d8.dll", "d3d9.dll", "d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll", "dxgi.dll", "ddraw.dll", "wined3d.dll"};
                restoreBuiltinDllFiles(dlls);
            }
            else GeneralComponents.extractFile(GeneralComponents.Type.WINED3D, this, version, DefaultVersion.WINED3D);
        }
        else if (dxwrapper.equals(DXWrappers.DXVK)) {
            final boolean[] hasD3D8DllFile = {false};
            final boolean[] hasD3D10DllFile = {false};

            GeneralComponents.extractFile(GeneralComponents.Type.DXVK, this, dxwrapperConfig[0].get("version"), DefaultVersion.DXVK(graphicsDriver[0]), (destination, size) -> {
                String name = destination.getName();
                if (name.equals("d3d10.dll")) {
                    hasD3D10DllFile[0] = true;
                }
                else if (name.equals("d3d8.dll")) {
                    hasD3D8DllFile[0] = true;
                }
                return destination;
            });

            if (!hasD3D8DllFile[0]) {
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d8vk-"+DefaultVersion.D8VK+".tzst", windowsDir);
            }
            if (!hasD3D10DllFile[0]) restoreBuiltinDllFiles("d3d10.dll", "d3d10_1.dll");
        }

        GeneralComponents.extractFile(GeneralComponents.Type.VKD3D, this, dxwrapperConfig[1].get("version"), DefaultVersion.VKD3D);

        File containerSysWoW64Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/syswow64");
        FileUtils.delete(new File(containerSysWoW64Dir, "ddraw_.dll"));

        switch (ddrawWrapper) {
            case DXWrappers.CNC_DDRAW:
                final String assetDir = "dxwrapper/cnc-ddraw-"+DefaultVersion.CNC_DDRAW;
                File configFile = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/ProgramData/cnc-ddraw/ddraw.ini");
                if (!configFile.isFile()) FileUtils.copy(this, assetDir+"/ddraw.ini", configFile);
                File shadersDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/ProgramData/cnc-ddraw/Shaders");
                FileUtils.delete(shadersDir);
                FileUtils.copy(this, assetDir+"/Shaders", shadersDir);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, assetDir+"/ddraw.tzst", windowsDir);
                break;
            case DXWrappers.D7VK:
                restoreBuiltinDllFiles("ddraw.dll");
                (new File(containerSysWoW64Dir, "ddraw.dll")).renameTo(new File(containerSysWoW64Dir, "ddraw_.dll"));
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d7vk-"+DefaultVersion.D7VK+".tzst", windowsDir);
                break;
            default:
                restoreBuiltinDllFiles("ddraw.dll");
                break;
        }
        return true;
    }

    private void extractWinComponentFiles() {
        File rootDir = rootFS.getRootDir();
        File windowsDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");
        File systemRegFile = new File(rootDir, RootFS.WINEPREFIX+"/system.reg");

        try {
            JSONObject wincomponentsJSONObject = new JSONObject(FileUtils.readString(this, "wincomponents/wincomponents.json"));
            KeyValueSet oldWinComponents = new KeyValueSet(container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS));
            ArrayList<String> builtinDlls = new ArrayList<>();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                String identifier = wincomponent[0];
                if (wincomponent[1].equals(oldWinComponents.get(identifier, "0"))) continue;
                boolean useNative = wincomponent[1].equals("1");

                if (useNative) {
                    if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/"+identifier+".tzst", windowsDir)) {
                        throw new IllegalStateException("Unable to extract Windows component " + identifier);
                    }
                }
                else {
                    JSONObject wincomponentJSONObject = wincomponentsJSONObject.getJSONObject(identifier);
                    if (wincomponentJSONObject.getBoolean("restoreBuiltinDlls")) {
                        JSONArray dlnames = wincomponentJSONObject.getJSONArray("dlnames");
                        for (int i = 0; i < dlnames.length(); i++) {
                            String dlname = dlnames.getString(i);
                            builtinDlls.add(!dlname.endsWith(".exe") ? dlname+".dll" : dlname);
                        }
                    }
                    else {
                        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/"+identifier+".tzst", windowsDir, (destination, size) -> {
                            String name = destination.getName();
                            if (name.endsWith(".dll") || name.endsWith(".manifest") || name.endsWith("_deadbeef")) FileUtils.delete(destination);
                            return null;
                        });
                    }
                }

                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative);
            }

            if (!builtinDlls.isEmpty()) restoreBuiltinDllFiles(builtinDlls.toArray(new String[0]));
            WineUtils.overrideWinComponentDlls(this, container, wincomponents);
        }
        catch (JSONException error) {
            throw new IllegalStateException("Unable to read Windows component definitions", error);
        }
    }

    private void restoreBuiltinDllFiles(final String... dlls) {
        File rootDir = rootFS.getRootDir();
        File wineDir = new File(rootDir, rootFS.getWinePath());
        File wineSystem32Dir = new File(wineDir, "/lib/wine/x86_64-windows");
        File wineSysWoW64Dir = new File(wineDir, "/lib/wine/i386-windows");
        File containerSystem32Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/system32");
        File containerSysWoW64Dir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows/syswow64");;

        for (String dll : dlls) {
            FileUtils.copy(new File(wineSysWoW64Dir, dll), new File(containerSysWoW64Dir, dll));
            FileUtils.copy(new File(wineSystem32Dir, dll), new File(containerSystem32Dir, dll));
        }
    }

    private boolean isGenerateWineprefix() {
        return getIntent().getBooleanExtra("generate_wineprefix", false);
    }

    private String runtimeInstallerCommand(String arguments) {
        String helperName = "runtime-launcher.exe";
        try (InputStream asset = getAssets().open("windroid/" + helperName)) {
            Files.copy(asset, new File(rootFS.getTmpDir(), helperName).toPath());
        }
        catch (IOException error) {
            throw new IllegalStateException("Unable to stage the runtime installer launcher", error);
        }
        return "wine Z:\\tmp\\" + helperName + " " + arguments;
    }

    private int readRuntimeExitStatus() {
        File report = new File(rootFS.getTmpDir(), RUNTIME_EXIT_FILE);
        if (report.length() != Integer.BYTES) return -1;
        try (java.io.DataInputStream input = new java.io.DataInputStream(new java.io.FileInputStream(report))) {
            return Integer.reverseBytes(input.readInt());
        }
        catch (IOException error) {
            return -1;
        }
    }

    private String desktopSessionCommand() {
        // Wine closes a virtual desktop after its last user process exits.
        String name = "windroid-desktop-session.exe";
        File helper = new File(rootFS.getTmpDir(), name);
        try (InputStream asset = getAssets().open("windroid/desktop-session.exe")) {
            Files.copy(asset, helper.toPath());
        }
        catch (IOException error) {
            throw new IllegalStateException("Unable to stage the Windows desktop session helper", error);
        }
        return "Z:\\tmp\\" + name;
    }

    private String getWineStartCommand() {
        String cmdArgs = "";
        String execPath = null;
        String execArgs = "";

        if (shortcut != null) {
            execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " "+execArgs : "";

            if (shortcut.isLinkPath()) {
                cmdArgs = "\""+shortcut.path+"\""+execArgs;
            }
            else execPath = shortcut.path;
        }
        else {
            Intent intent = getIntent();
            if (intent.hasExtra("exec_path")) {
                execPath = WineUtils.unixToDOSPath(intent.getStringExtra("exec_path"), container);

                if (execPath.toLowerCase(java.util.Locale.ROOT).endsWith(".lnk")) {
                    cmdArgs = "\""+execPath+"\"";
                    execPath = null;
                }
            }
        }

        if (execPath != null) {
            String execDir = FileUtils.getDirname(execPath);
            String filename = FileUtils.getName(execPath);
            int dotIndex, spaceIndex;
            if ((dotIndex = filename.lastIndexOf(".")) != -1 && (spaceIndex = filename.indexOf(" ", dotIndex)) != -1) {
                execArgs = filename.substring(spaceIndex+1)+execArgs;
                filename = filename.substring(0, spaceIndex);
            }
            cmdArgs = "/dir "+StringUtils.escapeDOSPath(execDir)+" \""+filename+"\""+execArgs;
        }

        if (cmdArgs.isEmpty()) cmdArgs = "/dir C:\\windows \"wfm.exe\"";

        if (overrideEnvVars != null && overrideEnvVars.has("EXTRA_EXEC_ARGS")) {
            cmdArgs += " "+overrideEnvVars.get("EXTRA_EXEC_ARGS");
            overrideEnvVars.remove("EXTRA_EXEC_ARGS");
        }
        return "C:\\windows\\winhandler.exe "+cmdArgs;
    }

    public XServer getXServer() {
        return xServer;
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    public RootFS getRootFs() {
        return rootFS;
    }

    public EnvVars getOverrideEnvVars() {
        if (overrideEnvVars == null) overrideEnvVars = new EnvVars();
        return overrideEnvVars;
    }

    public String getDXWrapper() {
        return dxwrapper;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public ScreenInfo getScreenInfo() {
        return screenInfo;
    }

    public void setScreenInfo(ScreenInfo screenInfo) {
        this.screenInfo = screenInfo;
    }

    private ScreenInfo resolveScreenInfo(String value) {
        if (!value.equals("native")) return new ScreenInfo(value);

        int width = AppUtils.getScreenWidth();
        int height = AppUtils.getScreenHeight();
        return new ScreenInfo(width - (width % 2), height - (height % 2));
    }

    public String getWinComponents() {
        return wincomponents;
    }

    public void setWinComponents(String wincomponents) {
        this.wincomponents = wincomponents;
    }

    public DebugDialog getDebugDialog() {
        return debugDialog;
    }

    public String getScreenEffectProfile() {
        return screenEffectProfile;
    }

    public void setScreenEffectProfile(String screenEffectProfile) {
        this.screenEffectProfile = screenEffectProfile;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = rootFS.getRootDir();
            File userRegFile = new File(rootDir, RootFS.WINEPREFIX+"/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals(AudioDrivers.ALSA)) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                }
                else if (audioDriver.equals(AudioDrivers.PULSEAUDIO)) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    private void applyGeneralPatches(Container container) {
        File rootDir = rootFS.getRootDir();
        FileUtils.delete(new File(rootDir, "/opt/apps"));
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "rootfs_patches.tzst", rootDir);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst", new File(getFilesDir(), "pulseaudio"));
        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("dxwrapper", null);
        container.putExtra("desktopTheme", null);
        SettingsFragment.resetPreferenceVersions(this);
    }

    public void changeFrameRatingVisibility(Window window, boolean visible) {
        if (frameRating == null) return;
        if (visible) {
            if (window.id == frameRatingWindowId) return;
            Window child = window.getChildAt(0);
            boolean viewable = window.attributes.isMapped() && window.getWidth() >= ScreenInfo.MIN_WIDTH && window.getHeight() >= ScreenInfo.MIN_HEIGHT;
            Window frameRatingWindow = null;
            if (viewable && (window.isSurface() || (child != null && child.isSurface()))) {
                frameRatingWindow = window.isSurface() ? window : child;
            }
            else if (window.isSurface() && !window.isApplicationWindow()) {
                Window parent = window.getParent();
                if (parent != null && parent.isApplicationWindow() && !parent.isSurface()) frameRatingWindow = window;
            }

            if (frameRatingWindow != null) {
                if (frameRating.getMode() == FrameRating.Mode.FULL) {
                    Property gpuInfo = frameRatingWindow.getProperty(Atom._NET_WM_GPU_INFO);
                    frameRating.setGPUInfo(gpuInfo != null ? new String(gpuInfo.data.array()) : "N/A");
                }
                frameRatingWindowId = frameRatingWindow.id;
                frameRating.reset();
            }
        }
        else if (window.id == frameRatingWindowId) {
            frameRatingWindowId = -1;
            runOnUiThread(() -> frameRating.setVisibility(View.GONE));
        }
    }

    public boolean verifyUserRegistry() {
        File userRegFile = new File(rootFS.getRootDir(), RootFS.WINEPREFIX+"/user.reg");
        String lastModified = String.valueOf(userRegFile.lastModified());

        if (!lastModified.equals(container.getExtra("userRegLastModified"))) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                registryEditor.removeKey("Software\\Wow6432Node\\Wine", true);
            }

            container.putExtra("userRegLastModified", lastModified);
            return true;
        }
        else return false;
    }
}
