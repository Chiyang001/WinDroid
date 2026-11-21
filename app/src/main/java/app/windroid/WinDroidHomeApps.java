package app.windroid;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;

import com.winlator.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.windroid.WinDroidGlyph.Symbol;

final class WinDroidHomeApps extends LinearLayout {
    private static final int SUGGESTED_FAVORITES = 4;

    private final WinDroidActivity activity;
    private final WinDroidPalette palette;
    private final WinDroidModel model;
    private final Runnable openSoftware;
    private final Shelf recent;
    private final Shelf favorites;

    WinDroidHomeApps(WinDroidActivity activity, WinDroidPalette palette, WinDroidModel model, Runnable openSoftware) {
        super(activity);
        this.activity = activity;
        this.palette = palette;
        this.model = model;
        this.openSoftware = openSoftware;
        setOrientation(VERTICAL);

        recent = new Shelf(R.string.wd_home_recent, Symbol.RECENT, openSoftware, false);
        favorites = new Shelf(R.string.wd_home_favorites, Symbol.ADD, this::chooseFavorites, true);
        activity.addBlock(this, recent.section, 0);
        activity.addBlock(this, favorites.section, 11);
    }

    void refresh() {
        List<String> pinned = model.homeFavorites();
        Set<String> pins = new HashSet<>(pinned);
        List<String> shortcuts = pinned;
        if (!model.homeFavoritesCustomized()) {
            List<String> frequent = model.frequentApps();
            shortcuts = frequent.subList(0, Math.min(SUGGESTED_FAVORITES, frequent.size()));
        }
        recent.show(model.recentApps(), pins, activity.getString(R.string.wd_home_recent_empty));
        favorites.show(shortcuts, pins, activity.getString(model.library().isEmpty()
                ? R.string.wd_home_no_apps : R.string.wd_home_choose_favorites));
        setLaunchEnabled(model.canOpen());
    }

    void setLaunchEnabled(boolean enabled) {
        recent.setLaunchEnabled(enabled);
        favorites.setLaunchEnabled(enabled);
    }

    void bindAppInfo(String path, WindowsAppInfo app) {
        recent.bindAppInfo(path, app);
        favorites.bindAppInfo(path, app);
    }

    private void chooseFavorites() {
        List<String> apps = model.library();
        if (apps.isEmpty()) {
            openSoftware.run();
            return;
        }
        Set<String> pinned = new HashSet<>(model.homeFavorites());
        String[] labels = new String[apps.size()];
        boolean[] selected = new boolean[apps.size()];
        for (int index = 0; index < apps.size(); index++) {
            String path = apps.get(index);
            labels[index] = model.appInfo(path).name;
            selected[index] = pinned.contains(path);
        }

        AlertDialog.Builder chooser = new AlertDialog.Builder(activity)
                .setTitle(R.string.wd_home_pin)
                .setMultiChoiceItems(labels, selected, (dialog, index, checked) -> selected[index] = checked)
                .setNegativeButton(R.string.wd_cancel, null)
                .setPositiveButton(R.string.wd_home_save_favorites, (dialog, which) -> {
                    List<String> choices = new ArrayList<>();
                    for (int index = 0; index < selected.length; index++) {
                        if (selected[index]) choices.add(apps.get(index));
                    }
                    reportSelection(model.setHomeFavorites(choices));
                });
        if (model.homeFavoritesCustomized()) {
            chooser.setNeutralButton(R.string.wd_home_restore_auto, (dialog, which) -> reportSelection(model.resetHomeFavorites()));
        }
        chooser.show();
    }

    private void reportSelection(boolean saved) {
        refresh();
        activity.showNotice(activity.getString(saved ? R.string.wd_home_favorites_saved : R.string.wd_error_save_changes));
    }

    private final class Shelf {
        final LinearLayout section;
        private final HorizontalScrollView scroll;
        private final LinearLayout cards;
        private final View manage;
        private final LinearLayout empty;
        private final TextView emptyLabel;
        private final Map<String, AppCard> views = new HashMap<>();
        private List<String> displayed = Collections.emptyList();

        Shelf(int title, Symbol emptyIcon, Runnable emptyAction, boolean configurable) {
            section = activity.column();
            LinearLayout header = activity.row();
            header.setMinimumHeight(activity.dp(42));
            TextView heading = activity.text(activity.getString(title), 19, palette.text, true);
            ViewCompat.setAccessibilityHeading(heading, true);
            header.addView(heading, new LayoutParams(0, -2, 1f));
            manage = configurable ? activity.button(activity.getString(R.string.wd_home_manage),
                    activity.getString(R.string.wd_home_manage_favorites), WinDroidHomeApps.this::chooseFavorites) : null;
            if (manage != null) {
                manage.setVisibility(GONE);
                header.addView(manage);
            }
            section.addView(header);

            scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setVisibility(GONE);
            cards = activity.row();
            scroll.addView(cards, new FrameLayout.LayoutParams(-2, -2));
            activity.addBlock(section, scroll, 1);

            empty = activity.row();
            empty.setMinimumHeight(activity.dp(57));
            empty.setPaddingRelative(activity.dp(10), activity.dp(7), activity.dp(12), activity.dp(7));
            empty.setBackground(activity.ripple(palette.surface, palette.border, 17, activity.isLight()));
            activity.activate(empty, emptyAction);
            empty.addView(activity.iconTile(emptyIcon, palette.tile, palette.secondary, 34));
            emptyLabel = activity.text("", 13, palette.secondary, false);
            LayoutParams textPosition = new LayoutParams(0, -2, 1f);
            textPosition.setMarginStart(activity.dp(10));
            textPosition.setMarginEnd(activity.dp(6));
            empty.addView(emptyLabel, textPosition);
            empty.addView(activity.glyph(Symbol.CHEVRON, palette.secondary), new LayoutParams(activity.dp(16), activity.dp(16)));
            activity.addBlock(section, empty, 1);
        }

        void show(List<String> paths, Set<String> pinned, String emptyText) {
            boolean hasCards = !paths.isEmpty();
            emptyLabel.setText(emptyText);
            empty.setVisibility(hasCards ? GONE : VISIBLE);
            scroll.setVisibility(hasCards ? VISIBLE : GONE);
            if (manage != null) manage.setVisibility(hasCards ? VISIBLE : GONE);
            if (!displayed.equals(paths)) {
                displayed = new ArrayList<>(paths);
                views.clear();
                cards.removeAllViews();
                if (manage == null) scroll.scrollTo(0, 0);
                for (int index = 0; index < paths.size(); index++) {
                    String path = paths.get(index);
                    AppCard item = new AppCard(path);
                    views.put(path, item);
                    LayoutParams position = new LayoutParams(activity.dp(108), -2);
                    if (index > 0) position.setMarginStart(activity.dp(8));
                    cards.addView(item.card, position);
                    model.loadAppInfo(path);
                }
            }
            for (Map.Entry<String, AppCard> item : views.entrySet()) item.getValue().setPinned(pinned.contains(item.getKey()));
        }

        void setLaunchEnabled(boolean enabled) {
            for (AppCard item : views.values()) WinDroidActivity.setEnabled(item.card, enabled);
        }

        void bindAppInfo(String path, WindowsAppInfo app) {
            AppCard item = views.get(path);
            if (item != null) item.bind(app);
        }
    }

    private final class AppCard {
        final LinearLayout card;
        private final TextView title;
        private final ImageView image;
        private final View pin;
        private boolean pinned;

        AppCard(String path) {
            card = activity.column();
            title = activity.text("", 13, palette.text, true);
            card.setPaddingRelative(activity.dp(10), activity.dp(9), activity.dp(10), activity.dp(10));
            card.setBackground(activity.ripple(palette.surface, palette.border, 17, activity.isLight()));
            activity.activate(card, () -> model.launchPinned(path));
            card.setOnLongClickListener(view -> {
                activity.showLibraryMenu(card, path, title.getText().toString());
                return true;
            });

            LinearLayout top = activity.row();
            FrameLayout icon = activity.iconTile(Symbol.APP, palette.tile, palette.text, 36);
            icon.setClipToOutline(true);
            image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            icon.addView(image, new FrameLayout.LayoutParams(-1, -1));
            top.addView(icon);
            top.addView(new View(activity), new LayoutParams(0, 1, 1f));
            pin = activity.glyph(Symbol.PIN, palette.secondary);
            pin.setVisibility(INVISIBLE);
            top.addView(pin, new LayoutParams(activity.dp(17), activity.dp(17)));
            card.addView(top);

            title.setLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            activity.addBlock(card, title, 5);
            bind(model.appInfo(path));
        }

        void bind(WindowsAppInfo app) {
            title.setText(app.name);
            image.setImageBitmap(app.icon);
            setPinned(pinned);
        }

        void setPinned(boolean value) {
            pinned = value;
            pin.setVisibility(pinned ? VISIBLE : INVISIBLE);
            card.setContentDescription(pinned
                    ? activity.getString(R.string.wd_entry_description, title.getText(), activity.getString(R.string.wd_home_pinned))
                    : title.getText());
        }
    }
}
