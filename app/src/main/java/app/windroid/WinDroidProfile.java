package app.windroid;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;

import com.winlator.R;

import java.util.function.Consumer;

final class WinDroidProfile {
    private static final String BILIBILI = "https://space.bilibili.com/404891612";
    private static final String GITHUB = "https://github.com/Chiyang001?tab=repositories";
    private static final String[] LANGUAGES = {"system", "zh", "en"};
    private static final String[] THEMES = {"system", "light", "dark"};
    private static final int[] LANGUAGE_LABELS = {
            R.string.windroid_profile_system, R.string.windroid_profile_language_zh, R.string.windroid_profile_language_en};
    private static final int[] THEME_LABELS = {
            R.string.windroid_profile_system, R.string.windroid_profile_theme_light, R.string.windroid_profile_theme_dark};

    private final WinDroidActivity activity;
    private final WinDroidPalette palette;

    WinDroidProfile(WinDroidActivity activity, WinDroidPalette palette) {
        this.activity = activity;
        this.palette = palette;
    }

    View buildMy(Runnable showAbout, Runnable updateAppearance, WinDroidModel model, Runnable showRuntimeLibrary) {
        LinearLayout page = column();
        add(page, heading(R.string.windroid_profile_title, 32), 0);
        add(page, heading(R.string.windroid_profile_preferences, 14), 22);

        String currentLanguage = WinDroidPreferences.language(activity);
        String currentTheme = WinDroidPreferences.theme(activity);
        LinearLayout preferences = card();
        preferences.addView(option(R.drawable.windroid_profile_language, R.string.windroid_profile_language,
                label(LANGUAGES, LANGUAGE_LABELS, currentLanguage), false,
                () -> choose(R.string.windroid_profile_language, LANGUAGES, LANGUAGE_LABELS,
                        currentLanguage, value -> WinDroidPreferences.setLanguage(activity, value), updateAppearance)));
        divider(preferences);
        preferences.addView(option(R.drawable.windroid_profile_theme, R.string.windroid_profile_theme,
                label(THEMES, THEME_LABELS, currentTheme), false,
                () -> choose(R.string.windroid_profile_theme, THEMES, THEME_LABELS,
                        currentTheme, value -> WinDroidPreferences.setTheme(activity, value), updateAppearance)));
        divider(preferences);
        TextView inputMode = text("", 13, palette.secondary, false);
        WinDroidInputPreferences.bindSummary(inputMode);
        preferences.addView(optionWithSummary(R.drawable.windroid_runtime_controls, R.string.wd_input_preferences,
                inputMode, false, () -> WinDroidInputPreferences.show(activity, activity, null)));
        divider(preferences);
        TextView wallpaper = text("", 13, palette.secondary, false);
        model.customWallpaper.observe(activity, custom -> wallpaper.setText(Boolean.TRUE.equals(custom)
                ? R.string.windroid_wallpaper_custom : R.string.windroid_wallpaper_default));
        preferences.addView(optionWithSummary(R.drawable.icon_image_picker, R.string.windroid_wallpaper_title,
                wallpaper, false, () -> showWallpaperOptions(model)));
        add(page, preferences, 9);

        LinearLayout app = card();
        TextView runtimeStatus = text("", 13, palette.secondary, false);
        runtimeStatus.setVisibility(View.GONE);
        model.status.observe(activity, state -> {
            boolean preparing = state.isPreparingRuntime();
            if (preparing) runtimeStatus.setText(state.message(activity));
            runtimeStatus.setVisibility(preparing ? View.VISIBLE : View.GONE);
        });
        app.addView(optionWithSummary(R.drawable.windroid_runtime_controls, R.string.wd_runtime_library,
                runtimeStatus, false, showRuntimeLibrary));
        divider(app);
        app.addView(option(R.drawable.windroid_profile_about, R.string.windroid_profile_about,
                versionLabel(), false, showAbout));
        add(page, app, 16);

        LinearLayout resetCard = card();
        int danger = activity.getColor(R.color.windroid_danger);
        TextView reset = text(activity.getString(R.string.wd_vm_reset), 15, danger, true);
        reset.setGravity(Gravity.CENTER_VERTICAL);
        reset.setMinimumHeight(dp(56));
        reset.setPaddingRelative(dp(14), dp(8), dp(14), dp(8));
        reset.setBackground(ripple(Color.TRANSPARENT, 0));
        reset.setFocusable(true);
        reset.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        reset.setOnClickListener(view -> confirmReset(model, false));
        resetCard.addView(reset);
        add(page, resetCard, 16);
        model.status.observe(activity, state -> {
            boolean resetting = WinDroidModel.isResetInProgress();
            reset.setEnabled(!resetting);
            reset.setText(resetting ? R.string.wd_vm_resetting : R.string.wd_vm_reset);
        });
        model.resetNotice.observe(activity, message -> {
            if (message == null) return;
            model.resetNotice.setValue(null);
            WinDroidInputPreferences.updateSummary(inputMode);
            activity.showNotice(activity.getString(message));
        });
        return page;
    }

    private void confirmReset(WinDroidModel model, boolean finalConfirmation) {
        int blockedReason = model.resetBlockedReason();
        if (blockedReason != 0) {
            activity.showNotice(activity.getString(blockedReason));
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.WinDroidDialogTheme)
                .setTitle(finalConfirmation ? R.string.wd_vm_reset_final_title : R.string.wd_vm_reset)
                .setMessage(finalConfirmation ? R.string.wd_vm_reset_final_message : R.string.wd_vm_reset_message)
                .setNegativeButton(R.string.windroid_profile_cancel, null)
                .setPositiveButton(finalConfirmation ? R.string.wd_vm_reset_erase : R.string.wd_vm_reset_continue,
                        (confirmation, which) -> {
                            if (!finalConfirmation) {
                                confirmation.dismiss();
                                confirmReset(model, true);
                                return;
                            }
                            int error = model.resetWindows();
                            if (error != 0) activity.showNotice(activity.getString(error));
                        })
                .show();
        if (finalConfirmation) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(activity.getColor(R.color.windroid_danger));
        }
    }

    private void showWallpaperOptions(WinDroidModel model) {
        CharSequence[] choices = {activity.getText(R.string.windroid_wallpaper_pick),
                activity.getText(R.string.windroid_wallpaper_restore)};
        new AlertDialog.Builder(activity, R.style.WinDroidDialogTheme)
                .setTitle(R.string.windroid_wallpaper_title)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) activity.pickWallpaper();
                    else model.changeWallpaper(null);
                })
                .setNegativeButton(R.string.windroid_profile_cancel, null)
                .show();
    }

    View buildAbout(Runnable goBack) {
        LinearLayout page = column();
        LinearLayout header = row();
        FrameLayout back = new FrameLayout(activity);
        back.setBackground(ripple(palette.surface, 24));
        View arrow = new WinDroidGlyph(activity, WinDroidGlyph.Symbol.CHEVRON, palette.text);
        arrow.setRotation(180f);
        back.addView(arrow, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        back.setContentDescription(activity.getString(R.string.windroid_profile_back));
        back.setFocusable(true);
        back.setOnClickListener(view -> goBack.run());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = heading(R.string.windroid_profile_about, 25);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMarginStart(dp(13));
        header.addView(title, titleParams);
        add(page, header, 0);
        add(page, aboutApp(), 17);
        add(page, heading(R.string.windroid_profile_developer, 14), 22);
        add(page, aboutDeveloper(), 9);
        add(page, new WinDroidOpenSource(activity, palette, this::openLink).build(), 22);
        return page;
    }

    private View aboutApp() {
        LinearLayout appCard = card();
        appCard.setPadding(dp(17), dp(17), dp(17), dp(18));
        LinearLayout identifier = row();

        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(surface(Color.BLACK, palette.border, 18));
        tile.setClipToOutline(true);
        ImageView appLogo = new ImageView(activity);
        appLogo.setImageResource(R.mipmap.windroid_launcher);
        appLogo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(appLogo, new FrameLayout.LayoutParams(-1, -1));
        identifier.addView(tile, new LinearLayout.LayoutParams(dp(62), dp(62)));

        identifier.addView(identity("WinDroid", versionLabel(), 24), identityParams());
        appCard.addView(identifier, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(activity.getString(R.string.windroid_profile_about_description), 14, palette.secondary, false);
        description.setLineSpacing(dp(4), 1f);
        add(appCard, description, 14);
        return appCard;
    }

    private View aboutDeveloper() {
        LinearLayout card = card();
        LinearLayout identifier = row();
        identifier.setPaddingRelative(dp(15), dp(14), dp(15), dp(14));
        ImageView avatar = new ImageView(activity);
        avatar.setImageResource(R.drawable.windroid_developer);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(surface(palette.tile, palette.border, 15));
        avatar.setClipToOutline(true);
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        identifier.addView(avatar, new LinearLayout.LayoutParams(dp(60), dp(60)));
        identifier.addView(identity(activity.getString(R.string.windroid_profile_developer_name),
                activity.getString(R.string.windroid_profile_developer_handle), 19), identityParams());
        card.addView(identifier);

        divider(card);
        card.addView(option(R.drawable.windroid_profile_bilibili, R.string.windroid_profile_bilibili, null, true,
                () -> openLink(BILIBILI)));
        divider(card);
        card.addView(option(R.drawable.windroid_profile_git, R.string.windroid_profile_github, null, true,
                () -> openLink(GITHUB)));
        return card;
    }

    private LinearLayout identity(String title, String caption, int size) {
        LinearLayout labels = column();
        TextView name = text(title, size, palette.text, true);
        name.setLetterSpacing(-0.025f);
        add(labels, name, 0);
        if (caption != null) add(labels, text(caption, 13, palette.secondary, false), 3);
        return labels;
    }

    private LinearLayout.LayoutParams identityParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(dp(13));
        return params;
    }

    private View option(@DrawableRes int symbol, @StringRes int label, String detail, boolean external, Runnable open) {
        TextView summary = detail == null ? null : text(detail, 13, palette.secondary, false);
        return optionWithSummary(symbol, label, summary, external, open);
    }

    private View optionWithSummary(@DrawableRes int symbol, @StringRes int label, TextView summary, boolean external, Runnable open) {
        LinearLayout entry = row();
        entry.setMinimumHeight(dp(summary == null ? 56 : 62));
        entry.setPaddingRelative(dp(13), dp(8), dp(14), dp(8));
        entry.setBackground(ripple(Color.TRANSPARENT, 0));
        entry.setFocusable(true);
        entry.setOnClickListener(view -> open.run());
        ViewCompat.setScreenReaderFocusable(entry, true);

        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(surface(palette.tile, Color.TRANSPARENT, 12));
        tile.addView(icon(symbol, palette.text), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        entry.addView(tile, new LinearLayout.LayoutParams(dp(38), dp(38)));

        String title = activity.getString(label);
        LinearLayout content = identity(title, null, 15);
        if (summary != null) add(content, summary, 3);
        LinearLayout.LayoutParams contentParams = identityParams();
        contentParams.setMarginEnd(dp(8));
        entry.addView(content, contentParams);

        View trailing = external ? icon(R.drawable.windroid_profile_external, palette.secondary)
                : new WinDroidGlyph(activity, WinDroidGlyph.Symbol.CHEVRON, palette.secondary);
        entry.addView(trailing, new LinearLayout.LayoutParams(dp(17), dp(17)));
        return entry;
    }

    private void choose(@StringRes int title, String[] values, int[] resources, String current,
                        Consumer<String> select, Runnable updateAppearance) {
        int selected = indexOf(values, current);
        String[] labels = new String[resources.length];
        for (int index = 0; index < labels.length; index++) labels[index] = activity.getString(resources[index]);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) return;
                    select.accept(values[which]);
                    updateAppearance.run();
                })
                .setNegativeButton(R.string.windroid_profile_cancel, null)
                .show();
    }

    private String label(String[] values, int[] resources, String current) {
        return activity.getString(resources[indexOf(values, current)]);
    }

    private int indexOf(String[] values, String current) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(current)) return index;
        }
        return 0;
    }

    private String versionLabel() {
        try {
            String version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            return version == null || version.isEmpty() ? null : activity.getString(R.string.windroid_profile_version, version);
        } catch (PackageManager.NameNotFoundException unavailable) {
            return null;
        }
    }

    void openLink(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.windroid_profile_link_unavailable)
                    .setMessage(url)
                    .setNegativeButton(R.string.windroid_profile_cancel, null)
                    .setPositiveButton(R.string.windroid_profile_copy_link, (dialog, which) -> {
                        ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("URL", url));
                    })
                    .show();
        }
    }

    private TextView heading(@StringRes int label, int size) {
        TextView heading = text(activity.getString(label), size, palette.text, true);
        heading.setLetterSpacing(-0.02f);
        ViewCompat.setAccessibilityHeading(heading, true);
        return heading;
    }

    private TextView text(String value, int size, int color, boolean medium) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (medium) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }

    private ImageView icon(@DrawableRes int res, int tint) {
        ImageView icon = new ImageView(activity);
        icon.setImageResource(res);
        icon.setImageTintList(ColorStateList.valueOf(tint));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setBackground(surface(palette.surface, palette.border, 22));
        card.setClipToOutline(true);
        return card;
    }

    private void divider(LinearLayout card) {
        View line = new View(activity);
        line.setBackgroundColor(palette.border);
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMarginStart(dp(14));
        params.setMarginEnd(dp(14));
        card.addView(line, params);
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void add(LinearLayout column, View child, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(gap);
        column.addView(child, params);
    }

    private GradientDrawable surface(int fill, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) shape.setStroke(dp(1), border);
        return shape;
    }

    private RippleDrawable ripple(int fill, int radius) {
        int tint = (palette.text & 0x00ffffff) | 0x23000000;
        GradientDrawable background = fill == Color.TRANSPARENT ? null : surface(fill, palette.border, radius);
        return new RippleDrawable(ColorStateList.valueOf(tint), background, surface(Color.WHITE, Color.TRANSPARENT, radius));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
