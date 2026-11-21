package app.windroid;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.winlator.R;
import com.winlator.XServerDisplayActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.windroid.WinDroidGlyph.Symbol;

public final class WinDroidActivity extends AppCompatActivity {
    private enum Page {
        HOME(R.id.windroid_home_scroll, R.string.wd_nav_home, Symbol.HOME),
        SOFTWARE(R.id.windroid_software_scroll, R.string.wd_nav_software, Symbol.LIBRARY),
        MY(R.id.windroid_my_scroll, R.string.wd_nav_my, Symbol.PERSON),
        ABOUT(R.id.windroid_about_scroll, 0, null),
        STORE(R.id.windroid_store_scroll, R.string.wd_nav_store, Symbol.STORE);

        final int scrollId;
        final int title;
        final Symbol symbol;

        Page(int scrollId, int title, Symbol symbol) {
            this.scrollId = scrollId;
            this.title = title;
            this.symbol = symbol;
        }
    }

    private static final PathInterpolator EASE_OUT = new PathInterpolator(0.2f, 0.75f, 0.25f, 1f);

    private final Map<Page, ScrollView> pages = new EnumMap<>(Page.class);
    private final Map<Page, LinearLayout> tabs = new EnumMap<>(Page.class);
    private WinDroidPalette palette;
    private Page selectedPage = Page.HOME;
    private FrameLayout pageHost;
    private FrameLayout navHost;
    private WinDroidModel model;
    private TextView status;
    private WinDroidGlyph statusGlyph;
    private WinDroidModel.Status.Kind statusKind;
    private View retry;
    private ProgressBar progress;
    private View desktop;
    private View install;
    private View add;
    private TextView softwareCount;
    private LinearLayout library;
    private WinDroidHomeApps homeApps;
    private WinDroidStore store;
    private View emptyLibrary;
    private final Map<String, LibraryRow> libraryRows = new HashMap<>();
    private AlertDialog installationDialog;
    private String pendingDesktopPin;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFileSelected);
    private final ActivityResultLauncher<Intent> wallpaperPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) {
                    model.changeWallpaper(data.getData());
                }
            });
    private final ActivityResultLauncher<Intent> window = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                String error = data == null ? null : data.getStringExtra(XServerDisplayActivity.WINDROID_ERROR);
                if (error != null) model.report(error);
                model.finishInstallation(error);
                model.refreshInstalledApps();
            });
    private final ActivityResultLauncher<Intent> runtimeInstaller = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> model.finishRuntimeInstallation(result.getData()));
    private final ActivityResultLauncher<Intent> appUninstaller = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> model.finishAppUninstall(result.getData()));

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(WinDroidPreferences.wrapLanguage(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        getDelegate().setLocalNightMode(WinDroidPreferences.nightMode(this));
        super.onCreate(state);
        palette = new WinDroidPalette(this);
        if (state != null) {
            selectedPage = Page.values()[state.getInt("page", Page.HOME.ordinal())];
            pendingDesktopPin = state.getString("pendingDesktopPin");
        }
        model = new ViewModelProvider(this).get(WinDroidModel.class);
        buildShell(state == null);
        store.restore(state);
        model.status.observe(this, this::update);
        model.wallpaperNotice.observe(this, message -> {
            if (message == null) return;
            model.wallpaperNotice.setValue(null);
            showNotice(getString(message));
        });
        model.uninstallNotice.observe(this, message -> {
            if (message == null) return;
            model.uninstallNotice.setValue(null);
            showNotice(message);
        });
        model.uninstallPlan.observe(this, plan -> {
            if (plan == null) return;
            model.uninstallPlan.setValue(null);
            confirmUninstall(plan);
        });
        model.launch.observe(this, request -> {
            if (request == null) return;
            model.launch.setValue(null);
            if (request.isAppUninstall()) appUninstaller.launch(request.intent(this));
            else if (request.isRuntimeInstaller()) runtimeInstaller.launch(request.intent(this));
            else window.launch(request.intent(this));
        });
        model.runtimeResults.observe(this, results -> {
            if (results == null) return;
            model.runtimeResults.setValue(null);
            new AlertDialog.Builder(this, R.style.WinDroidDialogTheme)
                    .setTitle(R.string.wd_runtime_install_results)
                    .setItems(results.toArray(new String[0]), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
        model.installation.observe(this, paths -> {
            if (paths != null) showInstallationResult(paths);
        });
        model.installedAppsChanged.observe(this, change -> refreshLibrary());
        model.appInfoChanged.observe(this, path -> {
            WindowsAppInfo app = model.appInfo(path);
            LibraryRow row = libraryRows.get(path);
            if (row != null) row.bind(app);
            homeApps.bindAppInfo(path, app);
            if (path.equals(pendingDesktopPin)) {
                pendingDesktopPin = null;
                if (row != null && model.canOpen()) pinToDesktop(path);
            }
        });
        model.prepare();
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshLibrary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        homeApps.refresh();
        if (selectedPage == Page.STORE) store.open();
    }

    @Override
    protected void onPause() {
        store.pause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", selectedPage.ordinal());
        state.putString("pendingDesktopPin", pendingDesktopPin);
        store.save(state);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (selectedPage == Page.ABOUT) showPage(Page.MY, true);
        else if (selectedPage != Page.HOME) showPage(Page.HOME, true);
        else super.onBackPressed();
    }

    private void buildShell(boolean firstLaunch) {
        LinearLayout shell = column();
        shell.setBackgroundColor(palette.background);
        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        WinDroidProfile profile = new WinDroidProfile(this, palette);
        buildHome(createPage(Page.HOME), firstLaunch);
        buildSoftware(createPage(Page.SOFTWARE));
        store = new WinDroidStore(this, palette, model, createPage(Page.STORE));
        addBlock(createPage(Page.MY), profile.buildMy(() -> showPage(Page.ABOUT, true), this::recreate, model,
                () -> WinDroidRuntimeLibrary.show(this, model, this::chooseInstaller)), 0);
        addBlock(createPage(Page.ABOUT), profile.buildAbout(() -> showPage(Page.MY, true)), 0);

        navHost = new FrameLayout(this);
        navHost.setPadding(dp(18), dp(5), dp(18), dp(8));
        LinearLayout nav = row();
        nav.setPadding(dp(4), dp(4), dp(4), dp(4));
        nav.setBackground(surface(palette.surface, palette.border, 23));
        for (Page page : new Page[]{Page.HOME, Page.SOFTWARE, Page.STORE, Page.MY}) {
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(51));
            item.setPadding(dp(4), dp(5), dp(4), dp(5));
            item.addView(glyph(page.symbol, palette.secondary), new LinearLayout.LayoutParams(dp(21), dp(21)));
            TextView label = text(getString(page.title), 12, palette.secondary, true);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelPosition = new LinearLayout.LayoutParams(-1, -2);
            labelPosition.topMargin = dp(3);
            item.addView(label, labelPosition);
            item.setContentDescription(getString(page.title));
            activate(item, () -> showPage(page, true));
            tabs.put(page, item);
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        int navWidth = Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(520));
        navHost.addView(nav, new FrameLayout.LayoutParams(Math.max(0, navWidth), -2, Gravity.CENTER_HORIZONTAL));
        shell.addView(navHost, new LinearLayout.LayoutParams(-1, -2));
        setContentView(shell);
        showPage(selectedPage, false);
    }

    private LinearLayout createPage(Page page) {
        ScrollView scroll = new ScrollView(this);
        scroll.setId(page.scrollId);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVisibility(View.GONE);
        if (page.title != 0) ViewCompat.setAccessibilityPaneTitle(scroll, getString(page.title));
        FrameLayout center = new FrameLayout(this);
        scroll.addView(center);

        LinearLayout content = column();
        content.setPadding(dp(22), dp(15), dp(22), dp(22));
        int maxWidth = Math.min(getResources().getDisplayMetrics().widthPixels, dp(640));
        center.addView(content, new FrameLayout.LayoutParams(maxWidth, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        pages.put(page, scroll);
        pageHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return content;
    }

    private void showPage(Page page, boolean animate) {
        boolean changed = selectedPage != page;
        if (changed && selectedPage == Page.STORE) {
            store.pause();
            store.hideKeyboard();
        }
        selectedPage = page;
        for (Map.Entry<Page, ScrollView> entry : pages.entrySet()) {
            ScrollView view = entry.getValue();
            boolean visible = entry.getKey() == page;
            if (!visible) {
                view.animate().cancel();
                view.setAlpha(1f);
                view.setTranslationY(0f);
            }
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        navHost.setVisibility(page == Page.ABOUT ? View.GONE : View.VISIBLE);
        if (page == Page.STORE) store.open();
        if (page == Page.HOME && changed) homeApps.refresh();
        for (Map.Entry<Page, LinearLayout> entry : tabs.entrySet()) {
            LinearLayout item = entry.getValue();
            boolean active = entry.getKey() == page;
            item.setSelected(active);
            item.setBackground(ripple(active ? palette.tile : Color.TRANSPARENT, Color.TRANSPARENT, 18, isLight()));
            int tint = active ? palette.text : palette.secondary;
            ((WinDroidGlyph) item.getChildAt(0)).setColor(tint);
            ((TextView) item.getChildAt(1)).setTextColor(tint);
        }
        if (animate && changed && ValueAnimator.areAnimatorsEnabled()) enter(pages.get(page), 0);
    }

    private void buildHome(LinearLayout content, boolean animateEntrance) {

        LinearLayout brand = row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.windroid_launcher);
        icon.setBackground(surface(Color.BLACK, palette.border, 13));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setClipToOutline(true);
        icon.setForeground(surface(Color.TRANSPARENT, palette.border, 13));
        brand.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        WinDroidWordmark name = new WinDroidWordmark(this, palette.text, animateEntrance);
        LinearLayout.LayoutParams brandName = new LinearLayout.LayoutParams(-2, -2);
        brandName.leftMargin = dp(12);
        brand.addView(name, brandName);
        content.addView(brand);

        View hero = buildHero();
        addBlock(content, hero, 14);

        LinearLayout commands = row();
        desktop = homeCommand(R.string.wd_launch_desktop, R.string.wd_launch_desktop_description,
                Symbol.DESKTOP, true, this::openWindowsDesktop);
        commands.addView(desktop, new LinearLayout.LayoutParams(0, -2, 1.15f));
        install = homeCommand(R.string.wd_home_install_software, R.string.wd_install_description,
                Symbol.INSTALL, false, this::chooseInstallSource);
        LinearLayout.LayoutParams installPosition = new LinearLayout.LayoutParams(0, -2, 1f);
        installPosition.setMarginStart(dp(8));
        commands.addView(install, installPosition);
        addBlock(content, commands, 10);

        View runtime = buildRuntimeStatus();
        addBlock(content, runtime, 8);

        homeApps = new WinDroidHomeApps(this, palette, model, () -> showPage(Page.SOFTWARE, true));
        addBlock(content, homeApps, 12);

        if (animateEntrance && ValueAnimator.areAnimatorsEnabled()) {
            View[] sections = {brand, hero, commands, runtime, homeApps};
            for (int index = 0; index < sections.length; index++) enter(sections[index], index * 34L);
        }
    }

    private View homeCommand(int caption, int description, Symbol symbol, boolean primary, Runnable click) {
        LinearLayout button = row();
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(55));
        button.setPaddingRelative(dp(9), dp(10), dp(9), dp(10));
        button.setBackground(ripple(primary ? palette.accent : palette.surface,
                primary ? Color.TRANSPARENT : palette.border, 17, primary != isLight()));
        int foreground = primary ? palette.onAccent : palette.text;
        button.addView(glyph(symbol, foreground), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = text(getString(caption), 14, foreground, true);
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelPosition = new LinearLayout.LayoutParams(-2, -2);
        labelPosition.setMarginStart(dp(7));
        button.addView(label, labelPosition);
        button.setContentDescription(getString(R.string.wd_entry_description, label.getText(), getString(description)));
        activate(button, click);
        return button;
    }

    private void buildSoftware(LinearLayout content) {
        LinearLayout header = row();
        TextView title = text(getString(R.string.wd_software_title), 29, palette.text, true);
        title.setLetterSpacing(-0.035f);
        ViewCompat.setAccessibilityHeading(title, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        add = button(getString(R.string.wd_add_shortcut), getString(R.string.wd_add_shortcut_description), this::showAddOptions);
        header.addView(add);
        content.addView(header);
        TextView description = text(getString(R.string.wd_software_subtitle), 14, palette.secondary, false);
        addBlock(content, description, 6);
        softwareCount = text("", 13, palette.secondary, true);
        addBlock(content, softwareCount, 22);
        library = column();
        addBlock(content, library, 9);
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setMinimumHeight(dp(159));
        hero.setBackground(surface(isLight() ? 0xffeeeeee : 0xff181818, Color.TRANSPARENT, 26));
        hero.setForeground(surface(Color.TRANSPARENT, palette.border, 26));
        hero.setClipToOutline(true);

        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.windroid_hero);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (isLight()) {
            art.setColorFilter(new ColorMatrixColorFilter(new float[]{
                    -0.78f, 0, 0, 0, 252,
                    0, -0.78f, 0, 0, 252,
                    0, 0, -0.78f, 0, 252,
                    0, 0, 0, 1, 0
            }));
        }
        art.setForeground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                isLight() ? new int[]{0x80f5f5f5, 0x38f5f5f5, 0x00f5f5f5}
                        : new int[]{0x80141414, 0x64141414, 0x00141414}));
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(art, new FrameLayout.LayoutParams(-1, dp(159)));

        LinearLayout copy = column();
        copy.setPadding(dp(18), dp(13), dp(16), dp(13));
        TextView headline = text(getString(R.string.wd_hero_headline), 29, palette.text, true);
        headline.setLetterSpacing(-0.04f);
        ViewCompat.setAccessibilityHeading(headline, true);
        copy.addView(headline);
        TextView note = text(getString(R.string.wd_hero_subtitle), 13, palette.secondary, false);
        addBlock(copy, note, 5);
        hero.addView(copy, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        return hero;
    }

    private View buildRuntimeStatus() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(surface(palette.surface, palette.border, 17));
        LinearLayout statusRow = row();
        statusRow.setPadding(dp(10), dp(6), dp(9), dp(6));
        statusGlyph = glyph(Symbol.BUSY, palette.text);
        statusGlyph.setBackground(surface(palette.tile, Color.TRANSPARENT, 10));
        statusGlyph.setPadding(dp(6), dp(6), dp(6), dp(6));
        statusRow.addView(statusGlyph, new LinearLayout.LayoutParams(dp(28), dp(28)));
        status = text("", 13, palette.text, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, -2, 1);
        statusParams.leftMargin = dp(10);
        statusParams.rightMargin = dp(4);
        statusRow.addView(status, statusParams);
        retry = button(getString(R.string.wd_retry), getString(R.string.wd_retry_description), model::prepare);
        retry.setVisibility(View.GONE);
        statusRow.addView(retry);
        card.addView(statusRow, new FrameLayout.LayoutParams(-1, -2));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(palette.text));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(palette.border));
        progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM);
        progressParams.bottomMargin = dp(2);
        progressParams.leftMargin = dp(13);
        progressParams.rightMargin = dp(13);
        card.addView(progress, progressParams);
        return card;
    }

    private void openWindowsDesktop() {
        if (!model.canOpen()) return;
        window.launch(model.windowsDesktopIntent(this));
    }

    private void chooseInstallSource() {
        CharSequence[] sources = {getText(R.string.wd_install_from_device), getText(R.string.wd_install_from_store)};
        new AlertDialog.Builder(this, R.style.WinDroidDialogTheme)
                .setTitle(R.string.wd_home_install_software)
                .setItems(sources, (dialog, which) -> {
                    if (which == 0) chooseInstaller();
                    else showPage(Page.STORE, true);
                })
                .setNegativeButton(R.string.wd_cancel, null)
                .show();
    }

    private void chooseInstaller() {
        if (!model.canOpen()) return;
        picker.launch(new String[]{"*/*"});
    }

    void pickWallpaper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                wallpaperPicker.launch(new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"));
                return;
            } catch (ActivityNotFoundException unavailable) {
                // Some devices provide only the document picker.
            }
        }
        try {
            wallpaperPicker.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"));
        } catch (ActivityNotFoundException unavailable) {
            showNotice(getString(R.string.windroid_wallpaper_picker_unavailable));
        }
    }

    private void onFileSelected(Uri uri) {
        if (uri != null) model.installFile(uri);
    }

    private void update(WinDroidModel.Status state) {
        if (WinDroidModel.isResetInProgress()) pendingDesktopPin = null;
        store.update(state);
        boolean enabled = model.canOpen();
        boolean busy = state.kind == WinDroidModel.Status.Kind.BUSY;
        if (statusKind == null || (statusKind == WinDroidModel.Status.Kind.BUSY && !busy)) refreshLibrary();
        String message = state.message(this);
        if (!TextUtils.equals(status.getText(), message)) {
            status.setText(message);
            if (state.kind == WinDroidModel.Status.Kind.ERROR && (selectedPage != Page.HOME
                    || (status.isLaidOut() && !status.getGlobalVisibleRect(new Rect())))) {
                showNotice(message);
            }
        }
        if (statusKind != state.kind) {
            Symbol symbol = busy ? Symbol.BUSY : state.kind == WinDroidModel.Status.Kind.READY ? Symbol.READY : Symbol.ALERT;
            statusGlyph.setSymbol(symbol);
            if (statusKind != null && ValueAnimator.areAnimatorsEnabled()) {
                statusGlyph.animate().cancel();
                statusGlyph.setAlpha(0.48f);
                statusGlyph.animate().alpha(1f).setDuration(220).setInterpolator(EASE_OUT);
            }
            statusKind = state.kind;
        }
        retry.setVisibility(!busy && model.environment() == null ? View.VISIBLE : View.GONE);
        boolean showProgress = busy && state.progress >= 0;
        progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (showProgress) progress.setProgress(state.progress, ValueAnimator.areAnimatorsEnabled());
        setEnabled(desktop, enabled);
        setEnabled(install, enabled);
        setEnabled(add, enabled);
        homeApps.setLaunchEnabled(enabled);
        for (LibraryRow entry : libraryRows.values()) entry.setEnabled(enabled);
    }

    private void refreshLibrary() {
        homeApps.refresh();
        List<String> items = model.library();
        softwareCount.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        softwareCount.setText(getResources().getQuantityString(R.plurals.wd_software_saved_count, items.size(), items.size()));
        Set<String> paths = new HashSet<>(items);
        Iterator<Map.Entry<String, LibraryRow>> existing = libraryRows.entrySet().iterator();
        while (existing.hasNext()) {
            Map.Entry<String, LibraryRow> entry = existing.next();
            if (!paths.contains(entry.getKey())) {
                library.removeView(entry.getValue().container);
                existing.remove();
            }
        }
        if (items.isEmpty()) {
            if (emptyLibrary == null) emptyLibrary = buildEmptyLibrary();
            if (emptyLibrary.getParent() == null) addBlock(library, emptyLibrary, 0);
            return;
        }
        if (emptyLibrary != null) library.removeView(emptyLibrary);
        for (int index = 0; index < items.size(); index++) {
            String path = items.get(index);
            LibraryRow existingRow = libraryRows.get(path);
            if (existingRow != null) {
                if (model.hasAppInfo(path)) existingRow.bind(model.appInfo(path));
                model.loadAppInfo(path);
                continue;
            }
            LibraryRow entry = buildLibraryRow(path);
            entry.setEnabled(model.canOpen());
            libraryRows.put(path, entry);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(7);
            library.addView(entry.container, index, params);
            if (statusKind != null && ValueAnimator.areAnimatorsEnabled()) enter(entry.container, 0);
            model.loadAppInfo(path);
        }
    }

    private View buildEmptyLibrary() {
        LinearLayout empty = row();
        empty.setPadding(dp(14), dp(13), dp(14), dp(13));
        empty.setBackground(surface(palette.surface, palette.border, 19));
        empty.addView(iconTile(Symbol.LIBRARY, palette.tile, palette.secondary, 40));
        LinearLayout emptyCopy = column();
        emptyCopy.setPadding(dp(12), 0, 0, 0);
        emptyCopy.addView(text(getString(R.string.wd_library_empty_title), 15, palette.text, true));
        addBlock(emptyCopy, text(getString(R.string.wd_library_empty_subtitle), 13, palette.secondary, false), 3);
        empty.addView(emptyCopy, new LinearLayout.LayoutParams(0, -2, 1));
        return empty;
    }

    private LibraryRow buildLibraryRow(String path) {
        LinearLayout item = row();
        item.setBackground(surface(palette.surface, palette.border, 17));
        String kind = getString(path.startsWith("WinDroid/Imports/") ? R.string.wd_entry_portable : R.string.wd_entry_shortcut);
        WindowsAppInfo app = model.appInfo(path);
        TextView title = text(app.name, 16, palette.text, true);
        FrameLayout tile = iconTile(Symbol.APP, palette.tile, palette.text, 40);
        tile.setClipToOutline(true);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        View open = libraryAction(title, kind, tile, () -> model.launchPinned(path));
        open.setOnLongClickListener(view -> {
            showLibraryMenu(open, path, title.getText().toString());
            return true;
        });
        item.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout more = new FrameLayout(this);
        more.setBackground(ripple(Color.TRANSPARENT, Color.TRANSPARENT, 15, isLight()));
        more.addView(glyph(Symbol.MORE, palette.secondary), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        activate(more, () -> showLibraryMenu(more, path, title.getText().toString()));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        moreParams.rightMargin = dp(4);
        item.addView(more, moreParams);
        LibraryRow row = new LibraryRow(item, open, more, title, image, kind);
        row.bind(app);
        return row;
    }

    void showLibraryMenu(View anchor, String path, String label) {
        if (!model.canOpen()) return;
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        boolean pinned = model.isHomeFavorite(path);
        menu.getMenu().add(pinned ? R.string.wd_home_unpin : R.string.wd_home_pin).setOnMenuItemClickListener(item -> {
            boolean saved = model.setHomeFavorite(path, !pinned);
            homeApps.refresh();
            int notice = !saved ? R.string.wd_error_save_changes : pinned ? R.string.wd_home_unpinned : R.string.wd_home_pinned;
            showNotice(getString(notice));
            return true;
        });
        menu.getMenu().add(R.string.wd_entry_pin).setOnMenuItemClickListener(item -> {
            pinToDesktop(path);
            return true;
        });
        menu.getMenu().add(R.string.wd_entry_open).setOnMenuItemClickListener(item -> {
            model.launchPinned(path);
            return true;
        });
        menu.getMenu().add(R.string.wd_entry_remove).setOnMenuItemClickListener(item -> {
            confirmRemoval(path, label);
            return true;
        });
        if (selectedPage == Page.SOFTWARE) {
            menu.getMenu().add(R.string.wd_entry_uninstall).setOnMenuItemClickListener(item -> {
                model.prepareUninstall(path, label);
                return true;
            });
        }
        menu.show();
    }

    private void confirmUninstall(WinDroidModel.UninstallPlan plan) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.wd_uninstall_title, plan.label))
                .setNegativeButton(R.string.wd_cancel, null);
        if (plan.registered != null) {
            dialog.setMessage(R.string.wd_uninstall_prompt)
                    .setPositiveButton(R.string.wd_entry_uninstall, (window, which) -> model.uninstall(plan, false));
        } else {
            dialog.setMessage(getString(R.string.wd_uninstall_file_prompt, windowsPath(plan.target)))
                    .setPositiveButton(R.string.wd_uninstall_delete_file, (window, which) -> model.uninstall(plan, false));
            if (plan.folder != null) {
                dialog.setNeutralButton(R.string.wd_uninstall_delete_folder, (window, which) ->
                        new AlertDialog.Builder(this).setTitle(R.string.wd_uninstall_delete_folder)
                                .setMessage(getString(R.string.wd_uninstall_folder_prompt, windowsPath(plan.folder)))
                                .setNegativeButton(R.string.wd_cancel, null)
                                .setPositiveButton(R.string.wd_uninstall_delete_folder, (confirm, button) -> model.uninstall(plan, true))
                                .show());
            }
        }
        dialog.show();
    }

    private static String windowsPath(String relative) {
        return "C:\\" + relative.replace('/', '\\');
    }

    private void pinToDesktop(String path) {
        if (!model.canOpen()) return;
        if (!model.hasAppInfo(path)) {
            pendingDesktopPin = path;
            model.loadAppInfo(path);
            return;
        }
        String message = DesktopShortcuts.pin(this, path, model.appInfo(path));
        if (message != null) showNotice(message);
    }

    private void confirmRemoval(String path, String label) {
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage(R.string.wd_remove_prompt)
                .setNegativeButton(R.string.wd_cancel, null)
                .setPositiveButton(R.string.wd_remove, (dialog, which) -> {
                    if (model.unpin(path)) refreshLibrary();
                    else model.report(R.string.wd_error_save_changes);
                }).show();
    }

    private void showInstallationResult(List<String> paths) {
        if (installationDialog != null && installationDialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.wd_installation_title);
        if (paths.isEmpty()) {
            builder.setMessage(R.string.wd_installation_no_new_shortcuts);
        }
        else {
            builder.setItems(shortcutLabels(paths), (dialog, which) -> saveEntry(paths.get(which)));
        }
        installationDialog = builder
                .setNegativeButton(R.string.wd_close, null)
                .setNeutralButton(R.string.wd_browse_drive, (dialog, which) -> browse(""))
                .create();
        installationDialog.setOnDismissListener(dialog -> {
            if (!isChangingConfigurations()) model.acknowledgeInstallation();
            installationDialog = null;
        });
        installationDialog.show();
    }

    private void showAddOptions() {
        if (!model.canOpen()) return;
        List<String> links = model.availableProgramLinks();
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.wd_installation_title);
        if (links.isEmpty()) builder.setMessage(R.string.wd_add_no_shortcuts);
        else builder.setItems(shortcutLabels(links), (dialog, which) -> saveEntry(links.get(which)));
        builder.setNegativeButton(R.string.wd_cancel, null)
                .setNeutralButton(R.string.wd_browse_drive, (dialog, which) -> browse("")).show();
    }

    private String[] shortcutLabels(List<String> paths) {
        String[] labels = new String[paths.size()];
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            labels[i] = getString(path.contains("/Desktop/") ? R.string.wd_shortcut_desktop : R.string.wd_shortcut_start_menu,
                    WinDroidModel.label(path));
        }
        return labels;
    }

    private void browse(String directory) {
        if (!model.canOpen()) return;
        try {
            List<String> paths = model.environment().browse(directory);
            boolean hasParent = !directory.isEmpty();
            ArrayList<String> labels = new ArrayList<>();
            if (hasParent) labels.add(getString(R.string.wd_browse_parent));
            for (String path : paths) {
                File file = model.environment().resolve(path);
                labels.add(file.getName() + (file.isDirectory() ? "  ›" : ""));
            }
            String title = "C:\\" + directory.replace('/', '\\');
            AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title).setNegativeButton(R.string.wd_cancel, null);
            if (labels.isEmpty()) builder.setMessage(R.string.wd_browse_empty);
            else builder.setItems(labels.toArray(new String[0]), (dialog, index) -> {
                if (hasParent && index == 0) {
                    int separator = directory.lastIndexOf('/');
                    browse(separator < 0 ? "" : directory.substring(0, separator));
                    return;
                }
                String path = paths.get(index - (hasParent ? 1 : 0));
                try {
                    if (model.environment().resolve(path).isDirectory()) browse(path);
                    else saveEntry(path);
                }
                catch (IOException error) {
                    model.report(error, R.string.wd_error_browse);
                }
            });
            builder.show();
        }
        catch (IOException error) {
            model.report(error, R.string.wd_error_browse);
        }
    }

    private void saveEntry(String path) {
        try {
            model.environment().launchTarget(path);
            if (libraryRows.containsKey(path)) {
                showNotice(getString(R.string.wd_entry_already_added));
                return;
            }
            if (!model.pin(path)) {
                model.report(R.string.wd_error_save_entry);
                return;
            }
            refreshLibrary();
            showPage(Page.SOFTWARE, true);
            showNotice(getString(R.string.wd_entry_added));
        }
        catch (IOException error) {
            model.report(error, R.string.wd_error_open_program);
        }
    }

    void showNotice(String message) {
        Snackbar notice = Snackbar.make(pageHost, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(palette.text)
                .setTextColor(palette.background);
        if (navHost.getVisibility() == View.VISIBLE) notice.setAnchorView(navHost);
        notice.show();
    }

    private View libraryAction(TextView label, String subtitle, View tile, Runnable click) {
        LinearLayout button = row();
        button.setBackground(ripple(Color.TRANSPARENT, Color.TRANSPARENT, 17, isLight()));
        button.setPadding(dp(12), dp(8), dp(14), dp(8));
        button.setMinimumHeight(dp(56));
        button.setContentDescription(getString(R.string.wd_entry_description, label.getText(), subtitle));
        activate(button, click);
        button.addView(tile);

        LinearLayout words = column();
        words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        words.setPadding(dp(12), 0, dp(6), 0);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(label);
        addBlock(words, text(subtitle, 12, palette.secondary, false), 2);
        button.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        return button;
    }

    Button button(String label, String description, Runnable click) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(label);
        view.setContentDescription(description);
        view.setTextSize(13);
        view.setTextColor(palette.text);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setMinWidth(dp(60));
        view.setBackground(ripple(palette.surface, palette.border, 24, isLight()));
        view.setPadding(dp(14), 0, dp(14), 0);
        activate(view, click);
        return view;
    }

    FrameLayout iconTile(Symbol symbol, int background, int color, int size) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(surface(background, Color.TRANSPARENT, 13));
        tile.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        int iconSize = dp(size >= 42 ? 23 : 21);
        tile.addView(glyph(symbol, color), new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        return tile;
    }

    WinDroidGlyph glyph(Symbol symbol, int color) {
        return new WinDroidGlyph(this, symbol, color);
    }

    void activate(View view, Runnable click) {
        view.setOnClickListener(target -> click.run());
        view.setFocusable(true);
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled}, scale(view, 0.985f, 95));
        states.addState(new int[0], scale(view, 1f, 185));
        view.setStateListAnimator(states);
    }

    private ObjectAnimator scale(View view, float size, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, size), PropertyValuesHolder.ofFloat(View.SCALE_Y, size));
        animator.setDuration(duration);
        animator.setInterpolator(EASE_OUT);
        return animator;
    }

    private void enter(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(9));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(265).setInterpolator(EASE_OUT);
    }

    TextView text(String label, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    void addBlock(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private GradientDrawable surface(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    RippleDrawable ripple(int fill, int stroke, int radius, boolean onLight) {
        return new RippleDrawable(ColorStateList.valueOf(onLight ? 0x23000000 : 0x25ffffff),
                surface(fill, stroke, radius), surface(Color.WHITE, Color.TRANSPARENT, radius));
    }

    static void setEnabled(View view, boolean enabled) {
        if (view.isEnabled() == enabled) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.48f);
    }

    int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    boolean isLight() {
        return Color.luminance(palette.background) > 0.5f;
    }

    private static final class LibraryRow {
        final View container;
        private final View open;
        private final View more;
        private final TextView title;
        private final ImageView image;
        private final String kind;

        LibraryRow(View container, View open, View more, TextView title, ImageView image, String kind) {
            this.container = container;
            this.open = open;
            this.more = more;
            this.title = title;
            this.image = image;
            this.kind = kind;
        }

        void bind(WindowsAppInfo app) {
            title.setText(app.name);
            image.setImageBitmap(app.icon);
            open.setContentDescription(open.getContext().getString(R.string.wd_entry_description, app.name, kind));
            more.setContentDescription(more.getContext().getString(R.string.wd_entry_manage, app.name));
        }

        void setEnabled(boolean enabled) {
            WinDroidActivity.setEnabled(open, enabled);
            WinDroidActivity.setEnabled(more, enabled);
        }
    }
}
