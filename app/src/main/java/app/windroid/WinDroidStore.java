package app.windroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.windroid.WinDroidGlyph.Symbol;
import app.windroid.WinDroidStoreCatalog.App;
import app.windroid.WinDroidStoreCatalog.Category;
import app.windroid.WinDroidStoreCatalog.Installer;

final class WinDroidStore {
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();
    private static final int PAGE_SIZE = 30;
    private static final long HIGHLIGHT_INTERVAL_MS = 12_000;
    private static final String PREFER_MIRRORS = "windroid_store_prefer_domestic_mirrors";
    private static final String VLC = "VideoLAN.VLC";
    private final WinDroidActivity activity;
    private final WinDroidPalette palette;
    private final WinDroidModel model;
    private final SharedPreferences preferences;
    private final WinDroidStoreIcons icons;
    private final LinearLayout content;
    private final EnumMap<Category, TextView> chips = new EnumMap<>(Category.class);
    private final EditText search;
    private final LinearLayout editorial;
    private final FrameLayout highlight;
    private final LinearLayout results;
    private final LinearLayout transfer;
    private final ProgressBar transferProgress;
    private final TextView transferText;
    private final View cancelDownload;
    private final TextView appCount;
    private final Runnable runSearch = this::render;
    private final Runnable rotateHighlight = this::rotateHighlight;
    private List<App> catalog;
    private List<App> filtered;
    private StoreHighlights highlights;
    private Category category = Category.ALL;
    private boolean loading;
    private boolean loadFailed;
    private boolean active;
    private boolean detailsOpen;
    private int page;

    WinDroidStore(WinDroidActivity activity, WinDroidPalette palette, WinDroidModel model, LinearLayout content) {
        this.activity = activity;
        this.palette = palette;
        this.model = model;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.icons = new WinDroidStoreIcons(activity.getApplicationContext());
        this.content = content;
        content.setFocusableInTouchMode(true);

        LinearLayout heading = activity.row();
        TextView title = activity.text(t(R.string.wd_nav_store), 31, palette.text, true);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        appCount = activity.text("", 12, palette.secondary, true);
        heading.addView(appCount);
        content.addView(heading);

        LinearLayout searchField = activity.row();
        searchField.setBackground(activity.ripple(palette.surface, palette.border, 17, activity.isLight()));
        searchField.setPadding(dp(14), 0, dp(5), 0);
        searchField.addView(activity.glyph(Symbol.SEARCH, palette.secondary), new LinearLayout.LayoutParams(dp(20), dp(20)));
        search = new EditText(activity);
        search.setId(R.id.windroid_store_search);
        search.setBackground(null);
        search.setTextSize(15);
        search.setTextColor(palette.text);
        search.setHintTextColor(palette.secondary);
        search.setHint(R.string.wd_store_search);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(10), 0, 0, 0);
        searchField.addView(search, new LinearLayout.LayoutParams(0, dp(50), 1));
        WinDroidGlyph clear = activity.glyph(Symbol.CLOSE, palette.secondary);
        clear.setPadding(dp(13), dp(13), dp(13), dp(13));
        clear.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        clear.setContentDescription(t(R.string.wd_store_clear));
        clear.setBackground(activity.ripple(Color.TRANSPARENT, Color.TRANSPARENT, 15, activity.isLight()));
        clear.setVisibility(View.INVISIBLE);
        activity.activate(clear, () -> search.setText(""));
        searchField.addView(clear, new LinearLayout.LayoutParams(dp(44), dp(44)));
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard();
            render();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                page = 0;
                clear.setVisibility(value.length() == 0 ? View.INVISIBLE : View.VISIBLE);
                content.removeCallbacks(runSearch);
                content.postDelayed(runSearch, 130);
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        activity.addBlock(content, searchField, 12);

        HorizontalScrollView scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = activity.row();
        for (Category entry : Category.values()) {
            TextView chip = activity.text(t(entry.title), 13, palette.secondary, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(15), 0, dp(15), 0);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, dp(45));
            size.setMarginEnd(dp(7));
            chipRow.addView(chip, size);
            activity.activate(chip, () -> select(entry));
            chips.put(entry, chip);
        }
        scroller.addView(chipRow);
        activity.addBlock(content, scroller, 11);
        updateChips();

        transfer = activity.column();
        transfer.setPadding(dp(12), dp(7), dp(10), dp(12));
        transfer.setBackground(activity.ripple(palette.surface, palette.border, 16, activity.isLight()));
        transfer.setVisibility(View.GONE);
        LinearLayout transferRow = activity.row();
        transferText = activity.text("", 13, palette.text, true);
        transferRow.addView(transferText, new LinearLayout.LayoutParams(0, -2, 1));
        cancelDownload = activity.button(t(android.R.string.cancel), t(android.R.string.cancel), model::cancelStoreDownload);
        transferRow.addView(cancelDownload);
        transfer.addView(transferRow);
        transferProgress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        transferProgress.setProgressTintList(ColorStateList.valueOf(palette.accent));
        transferProgress.setIndeterminateTintList(ColorStateList.valueOf(palette.accent));
        transfer.addView(transferProgress, new LinearLayout.LayoutParams(-1, dp(6)));
        activity.addBlock(content, transfer, 11);

        editorial = activity.column();
        editorial.setVisibility(View.GONE);
        highlight = new FrameLayout(activity);
        activity.addBlock(content, editorial, 13);
        results = activity.column();
        activity.addBlock(content, results, 16);
    }

    void restore(Bundle state) {
        if (state == null) return;
        select(Category.values()[Math.min(Category.values().length - 1, Math.max(0, state.getInt("storeCategory")))]);
        search.setText(state.getString("storeQuery", ""));
    }

    void save(Bundle state) {
        state.putInt("storeCategory", category.ordinal());
        state.putString("storeQuery", search.getText().toString());
    }

    void open() {
        active = true;
        scheduleHighlight();
        if (catalog != null || loading) return;
        loading = true;
        loadFailed = false;
        render();
        Context app = activity.getApplicationContext();
        READER.execute(() -> {
            List<App> loaded = null;
            try {
                loaded = WinDroidStoreCatalog.load(app);
                icons.loadIndex();
            }
            catch (Exception error) {
                android.util.Log.w("WinDroid", "Unable to read store catalog", error);
            }
            List<App> entries = loaded;
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) return;
                catalog = entries;
                loading = false;
                loadFailed = entries == null;
                if (!loadFailed) {
                    appCount.setText(t(R.string.wd_store_count, entries.size()));
                    buildEditorial();
                }
                render();
            });
        });
    }

    void pause() {
        active = false;
        scheduleHighlight();
    }

    void update(WinDroidModel.Status state) {
        transfer.setVisibility(state.isStoreDownload() ? View.VISIBLE : View.GONE);
        if (!state.isStoreDownload()) return;
        transferText.setText(state.message(activity));
        transferProgress.setIndeterminate(state.progress < 0);
        if (state.progress >= 0) transferProgress.setProgress(state.progress);
        WinDroidActivity.setEnabled(cancelDownload, !state.isCancellingStoreDownload());
    }

    private void select(Category selection) {
        category = selection;
        page = 0;
        updateChips();
        render();
    }

    private void updateChips() {
        for (Map.Entry<Category, TextView> item : chips.entrySet()) {
            TextView chip = item.getValue();
            boolean selected = item.getKey() == category;
            chip.setSelected(selected);
            chip.setTextColor(selected ? palette.onAccent : palette.secondary);
            chip.setBackground(activity.ripple(selected ? palette.accent : palette.surface,
                    selected ? Color.TRANSPARENT : palette.border, 22, activity.isLight() != selected));
        }
    }

    private void render() {
        results.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        editorial.setVisibility(catalog != null && category == Category.ALL && query.isEmpty() ? View.VISIBLE : View.GONE);
        scheduleHighlight();
        if (catalog == null) {
            if (!loading && !loadFailed) return;
            int message = loadFailed ? R.string.wd_store_catalog_error : R.string.wd_store_loading;
            results.addView(activity.text(t(message), 14, palette.secondary, false));
            if (loadFailed) activity.addBlock(results, activity.button(t(R.string.wd_retry), t(R.string.wd_retry), this::open), 8);
            return;
        }
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        filtered = new ArrayList<>();
        for (App app : catalog) {
            if ((category == Category.ALL || category == app.category) && app.matches(terms)) filtered.add(app);
        }
        if (!query.isEmpty()) filtered.sort(Comparator.comparingInt(app -> app.searchRank(query)));
        renderPage();
    }

    private void renderPage() {
        results.removeAllViews();
        results.addView(activity.text(t(R.string.wd_store_results, filtered.size()), 19, palette.text, true));
        if (filtered.isEmpty()) {
            activity.addBlock(results, activity.text(t(R.string.wd_store_empty), 14, palette.secondary, false), 8);
            return;
        }
        int first = page * PAGE_SIZE;
        for (int i = first; i < Math.min(first + PAGE_SIZE, filtered.size()); i++) {
            activity.addBlock(results, appCard(filtered.get(i), false), 8);
        }
        int pages = (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (pages > 1) {
            LinearLayout navigation = activity.row();
            Button previous = activity.button(t(R.string.wd_store_previous), t(R.string.wd_store_previous), () -> changePage(-1));
            Button next = activity.button(t(R.string.wd_store_next), t(R.string.wd_store_next), () -> changePage(1));
            WinDroidActivity.setEnabled(previous, page > 0);
            WinDroidActivity.setEnabled(next, page + 1 < pages);
            navigation.addView(previous);
            TextView position = activity.text(t(R.string.wd_store_position, page + 1, pages), 13, palette.secondary, true);
            position.setGravity(Gravity.CENTER);
            navigation.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
            navigation.addView(next);
            activity.addBlock(results, navigation, 11);
        }
        TextView source = activity.text(t(R.string.wd_store_source), 12, palette.secondary, false);
        source.setGravity(Gravity.CENTER);
        activity.addBlock(results, source, 12);
    }

    private void changePage(int step) {
        page += step;
        hideKeyboard();
        renderPage();
        View parent = activity.findViewById(R.id.windroid_store_scroll);
        if (parent instanceof ScrollView) {
            ScrollView scroll = (ScrollView)parent;
            scroll.post(() -> scroll.smoothScrollTo(0, content.getTop() + results.getTop()));
        }
    }

    private void buildEditorial() {
        editorial.removeAllViews();
        highlights = new StoreHighlights(catalog, app -> VLC.equals(app.id) || icons.contains(app.id), new Random());
        activity.addBlock(editorial, highlight, 0);
        rotateHighlight();
        List<App> featured = new ArrayList<>(4);
        for (App app : catalog.subList(0, Math.min(6, catalog.size()))) {
            if (!VLC.equals(app.id) && featured.size() < 4) featured.add(app);
        }
        activity.addBlock(editorial, activity.text(t(R.string.wd_store_featured), 20, palette.text, true), 17);
        for (int i = 0; i < featured.size(); i += 2) {
            LinearLayout row = activity.row();
            for (int j = i; j < Math.min(featured.size(), i + 2); j++) {
                LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(0, -2, 1);
                if (j != i) position.setMarginStart(dp(8));
                row.addView(appCard(featured.get(j), true), position);
            }
            activity.addBlock(editorial, row, 8);
        }
    }

    private void scheduleHighlight() {
        content.removeCallbacks(rotateHighlight);
        if (active && !detailsOpen && editorial.getVisibility() == View.VISIBLE && highlight.getChildCount() > 0) {
            content.postDelayed(rotateHighlight, HIGHLIGHT_INTERVAL_MS);
        }
    }

    private void rotateHighlight() {
        App app = highlights.next();
        highlight.removeAllViews();
        if (app != null) highlight.addView(hero(app), new FrameLayout.LayoutParams(-1, -2));
        scheduleHighlight();
    }

    private View hero(App app) {
        FrameLayout panel = new FrameLayout(activity);
        GradientDrawable color = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff171329, 0xff3b2148, 0xff773a43});
        color.setCornerRadius(dp(23));
        panel.setBackground(color);
        panel.setClipToOutline(true);
        panel.setMinimumHeight(dp(178));
        panel.addView(new HeroDecoration(activity), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout words = activity.column();
        words.setPadding(dp(17), dp(17), dp(5), dp(16));
        words.addView(activity.text(t(R.string.wd_store_hero_label, t(app.category.title)), 12, 0xffffdcb2, true));
        TextView name = activity.text(app.name, 25, Color.WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        activity.addBlock(words, name, 4);
        TextView summary = activity.text(description(app), 13, 0xfff4e4ed, false);
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        activity.addBlock(words, summary, 3);
        activity.addBlock(words, activity.text(t(R.string.wd_store_view), 13, 0xffffdcb2, true), 10);
        FrameLayout.LayoutParams wordPosition = new FrameLayout.LayoutParams(-1, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        wordPosition.setMarginEnd(dp(122));
        panel.addView(words, wordPosition);
        FrameLayout.LayoutParams iconPosition = new FrameLayout.LayoutParams(dp(85), dp(85), Gravity.END | Gravity.CENTER_VERTICAL);
        iconPosition.setMarginEnd(dp(15));
        panel.addView(icon(app, 85), iconPosition);
        panel.setForeground(activity.ripple(Color.TRANSPARENT, Color.TRANSPARENT, 23, false));
        activity.activate(panel, () -> details(app));
        return panel;
    }

    private View appCard(App app, boolean compact) {
        LinearLayout card = compact ? activity.column() : activity.row();
        card.setPadding(dp(11), dp(9), dp(11), dp(9));
        card.setMinimumHeight(dp(compact ? 88 : 57));
        card.setBackground(activity.ripple(palette.surface, palette.border, 15, activity.isLight()));
        int iconSize = dp(compact ? 34 : 38);
        card.addView(icon(app, compact ? 34 : 38), new LinearLayout.LayoutParams(iconSize, iconSize));
        LinearLayout label = activity.column();
        if (compact) label.setPadding(0, dp(5), 0, 0);
        else label.setPadding(dp(10), 0, dp(4), 0);
        TextView name = activity.text(app.name, 14, palette.text, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        label.addView(name);
        String secondary = compact ? t(app.category.title) : app.publisher + "  ·  " + t(app.category.title);
        TextView subtitle = activity.text(secondary, 11, palette.secondary, false);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        label.addView(subtitle);
        card.addView(label, compact ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1));
        if (!compact) card.addView(activity.glyph(Symbol.CHEVRON, palette.secondary), new LinearLayout.LayoutParams(dp(17), dp(17)));
        activity.activate(card, () -> details(app));
        return card;
    }

    private View icon(App app, int size) {
        int image = 0;
        switch (app.id) {
            case VLC: image = R.drawable.windroid_store_vlc; break;
            case "7zip.7zip": image = R.drawable.windroid_store_7zip; break;
            case "Audacity.Audacity": image = R.drawable.windroid_store_audacity; break;
            case "Notepad++.Notepad++": image = R.drawable.windroid_store_notepad; break;
        }
        if (image != 0) {
            ImageView view = new ImageView(activity);
            view.setImageResource(image);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            return view;
        }
        ImageView bundled = icons.createView(activity, app.id);
        if (bundled != null) return bundled;
        int[] colors = {0xffdce7ee, 0xffcbdbe8, 0xffead9ee, 0xffeeddca, 0xffd1e7de, 0xffdce0f3,
                0xfff5e4bc, 0xffefd9da, 0xffe2e4e4};
        String initial = app.name.substring(0, Character.charCount(app.name.codePointAt(0))).toUpperCase(Locale.ROOT);
        TextView view = activity.text(initial, size / 2, 0xff242339, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors[app.category.ordinal()]);
        background.setCornerRadius(dp(10));
        view.setBackground(background);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private void details(App app) {
        hideKeyboard();
        detailsOpen = true;
        scheduleHighlight();
        ScrollView scroll = new ScrollView(activity);
        LinearLayout detail = activity.column();
        detail.setPadding(dp(23), dp(9), dp(23), dp(4));
        LinearLayout heading = activity.row();
        heading.addView(icon(app, 47), new LinearLayout.LayoutParams(dp(47), dp(47)));
        LinearLayout metadata = activity.column();
        metadata.setPadding(dp(12), 0, 0, 0);
        metadata.addView(activity.text(app.publisher, 15, palette.text, true));
        metadata.addView(activity.text(t(R.string.wd_store_version, app.version, t(app.category.title)), 12, palette.secondary, false));
        heading.addView(metadata, new LinearLayout.LayoutParams(0, -2, 1));
        detail.addView(heading);
        String description = description(app);
        if (!description.isEmpty()) activity.addBlock(detail, activity.text(description, 14, palette.text, false), 10);
        activity.addBlock(detail, activity.text(t(R.string.wd_store_architecture), 13, palette.text, true), 13);
        RadioGroup choices = new RadioGroup(activity);
        choices.setOrientation(RadioGroup.VERTICAL);
        TextView delivery = activity.text("", 12, palette.secondary, false);
        TextView route = activity.text("", 13, palette.accent, true);
        route.setMinimumHeight(dp(44));
        route.setGravity(Gravity.CENTER_VERTICAL);
        route.setBackground(activity.ripple(Color.TRANSPARENT, Color.TRANSPARENT, 12, activity.isLight()));
        for (int i = 0; i < app.installers.size(); i++) {
            Installer installer = app.installers.get(i);
            RadioButton option = new RadioButton(activity);
            option.setId(i + 1);
            option.setMinHeight(dp(44));
            option.setText(t(R.string.wd_store_installer_type,
                    "neutral".equals(installer.architecture) ? t(R.string.wd_store_neutral) : installer.architecture,
                    installer.extension.toUpperCase(Locale.ROOT)));
            option.setTextColor(palette.text);
            choices.addView(option);
        }
        Runnable refreshDelivery = () -> {
            int id = choices.getCheckedRadioButtonId();
            if (id <= 0) return;
            boolean mirrors = preferMirrors();
            List<String> sources = StoreDownloadSources.forInstaller(app.installers.get(id - 1).url, mirrors);
            route.setText(t(R.string.wd_store_route_label,
                    t(mirrors ? R.string.wd_store_route_mirrors : R.string.wd_store_route_original)));
            delivery.setText(t(sources.size() > 1 ? R.string.wd_store_first_host : R.string.wd_store_download_host,
                    Uri.parse(sources.get(0)).getHost()));
        };
        activity.activate(route, () -> chooseDownloadRoute(refreshDelivery));
        choices.setOnCheckedChangeListener((group, id) -> refreshDelivery.run());
        choices.check(1);
        detail.addView(choices);
        detail.addView(route);
        activity.addBlock(detail, delivery, 3);
        activity.addBlock(detail, activity.text(t(R.string.wd_store_compatibility), 12, palette.secondary, false), 6);
        scroll.addView(detail);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.WinDroidDialogTheme)
                .setTitle(app.name).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.wd_store_install, (prompt, button) -> {
                    if (XServerDisplayActivity.hasWinDroidSession()) {
                        activity.showNotice(t(R.string.windroid_runtime_session_active));
                    }
                    else if (!model.canOpen()) {
                        activity.showNotice(t(R.string.wd_runtime_not_ready));
                    }
                    else model.installFromStore(app, app.installers.get(choices.getCheckedRadioButtonId() - 1),
                            preferMirrors());
                }).show();
        dialog.setOnDismissListener(prompt -> {
            detailsOpen = false;
            scheduleHighlight();
        });
    }

    private boolean preferMirrors() {
        return preferences.getBoolean(PREFER_MIRRORS, true);
    }

    private void chooseDownloadRoute(Runnable changed) {
        new AlertDialog.Builder(activity, R.style.WinDroidDialogTheme)
                .setTitle(R.string.wd_store_route)
                .setSingleChoiceItems(new String[]{t(R.string.wd_store_route_mirrors),
                        t(R.string.wd_store_route_original)}, preferMirrors() ? 0 : 1, (dialog, choice) -> {
                    preferences.edit().putBoolean(PREFER_MIRRORS, choice == 0).apply();
                    changed.run();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String description(App app) {
        boolean chinese = "zh".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
        return chinese && !app.chineseDescription.isEmpty() ? app.chineseDescription : app.description;
    }

    void hideKeyboard() {
        if (!search.hasFocus()) return;
        InputMethodManager input = activity.getSystemService(InputMethodManager.class);
        if (input != null) input.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private int dp(int value) { return activity.dp(value); }
    private String t(int id, Object... arguments) { return activity.getString(id, arguments); }

    private static final class HeroDecoration extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeroDecoration(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float scale = getHeight() / 155f;
            float x = getWidth() - 50 * scale;
            float y = 70 * scale;
            for (int i = 0; i < 4; i++) {
                paint.setColor(0x12ffd3b5);
                paint.setStrokeWidth(scale);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(x, y, (42 + 18 * i) * scale, paint);
            }
            paint.setColor(0x1cffa877);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, 49 * scale, paint);
        }
    }
}
