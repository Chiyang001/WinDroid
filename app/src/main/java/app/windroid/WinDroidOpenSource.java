package app.windroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.winlator.R;

import java.util.function.Consumer;

final class WinDroidOpenSource {
    private static final Project[] RUNTIME = {
            new Project("Winlator", R.string.wd_oss_winlator,
                    "https://github.com/brunodev85/winlator-app/tree/ecd5ab2980de79eb955621cef26a3b4672013875"),
            new Project("Wine 10.10", R.string.wd_oss_wine, "https://gitlab.winehq.org/wine/wine"),
            new Project("Box64 0.4.4", R.string.wd_oss_box64, "https://github.com/ptitSeb/box64"),
            new Project("Termux Pacman glibc", R.string.wd_oss_glibc,
                    "https://github.com/termux-pacman/glibc-packages")
    };
    private static final Project[] GRAPHICS = {
            new Project("Mesa", R.string.wd_oss_mesa, "https://gitlab.freedesktop.org/mesa/mesa"),
            new Project("DXVK", R.string.wd_oss_dxvk, "https://github.com/doitsujin/dxvk"),
            new Project("D7VK", R.string.wd_oss_d7vk, "https://github.com/WinterSnowfall/d7vk"),
            new Project("D8VK", R.string.wd_oss_d8vk, "https://github.com/AlpyneDreams/d8vk"),
            new Project("VKD3D", R.string.wd_oss_vkd3d, "https://gitlab.winehq.org/wine/vkd3d"),
            new Project("cnc-ddraw", R.string.wd_oss_ddraw, "https://github.com/FunkyFr3sh/cnc-ddraw")
    };
    private static final Project[] ANDROID = {
            new Project("AndroidX", R.string.wd_oss_androidx, "https://github.com/androidx/androidx"),
            new Project("Material Components", R.string.wd_oss_material,
                    "https://github.com/material-components/material-components-android"),
            new Project("Apache Commons Compress", R.string.wd_oss_compress,
                    "https://github.com/apache/commons-compress"),
            new Project("XZ for Java", R.string.wd_oss_xz, "https://tukaani.org/xz/java.html"),
            new Project("zstd-jni", R.string.wd_oss_zstd, "https://github.com/luben/zstd-jni"),
            new Project("Noto Sans CJK SC", R.string.wd_oss_noto, "https://github.com/notofonts/noto-cjk")
    };

    private final Context context;
    private final WinDroidPalette palette;
    private final Consumer<String> openLink;

    WinDroidOpenSource(Context context, WinDroidPalette palette, Consumer<String> openLink) {
        this.context = context;
        this.palette = palette;
        this.openLink = openLink;
    }

    View build() {
        LinearLayout section = column();
        TextView title = text(context.getString(R.string.wd_oss_title), 14, palette.text, true);
        ViewCompat.setAccessibilityHeading(title, true);
        section.addView(title);
        addGroup(section, R.string.wd_oss_runtime, "Winlator · Wine · Box64", RUNTIME);
        addGroup(section, R.string.wd_oss_graphics, "Mesa · DXVK · VKD3D", GRAPHICS);
        addGroup(section, R.string.wd_oss_android, "AndroidX · Material · Noto Sans CJK", ANDROID);
        return section;
    }

    private void addGroup(LinearLayout section, @StringRes int title, String preview, Project[] projects) {
        LinearLayout group = column();
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(palette.surface);
        surface.setCornerRadius(dp(22));
        surface.setStroke(dp(1), palette.border);
        group.setBackground(surface);
        group.setClipToOutline(true);

        LinearLayout header = row(context.getString(title), preview);
        View arrow = new WinDroidGlyph(context, WinDroidGlyph.Symbol.CHEVRON, palette.secondary);
        arrow.setRotation(90f);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
        group.addView(header);

        LinearLayout entries = column();
        entries.setVisibility(View.GONE);
        group.addView(entries);

        ViewCompat.setStateDescription(header, context.getString(R.string.wd_oss_collapsed));
        header.setOnClickListener(view -> {
            boolean expanded = entries.getVisibility() != View.VISIBLE;
            if (expanded && entries.getChildCount() == 0) addProjects(entries, projects);
            entries.setVisibility(expanded ? View.VISIBLE : View.GONE);
            arrow.setRotation(expanded ? -90f : 90f);
            ViewCompat.setStateDescription(header,
                    context.getString(expanded ? R.string.wd_oss_expanded : R.string.wd_oss_collapsed));
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9);
        section.addView(group, params);
    }

    private void addProjects(LinearLayout entries, Project[] projects) {
        for (Project project : projects) {
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.setMarginStart(dp(16));
            dividerParams.setMarginEnd(dp(16));
            View divider = new View(context);
            divider.setBackgroundColor(palette.border);
            divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            entries.addView(divider, dividerParams);
            entries.addView(projectRow(project));
        }
    }

    private View projectRow(Project project) {
        LinearLayout row = row(project.name, context.getString(project.description));
        ImageView external = new ImageView(context);
        external.setImageResource(R.drawable.windroid_profile_external);
        external.setImageTintList(ColorStateList.valueOf(palette.secondary));
        external.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(external, new LinearLayout.LayoutParams(dp(17), dp(17)));
        row.setOnClickListener(view -> openLink.accept(project.url));
        return row;
    }

    private LinearLayout row(String title, String description) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(61));
        row.setPaddingRelative(dp(16), dp(10), dp(16), dp(10));
        row.setFocusable(true);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf((palette.text & 0x00ffffff) | 0x23000000),
                null, new ColorDrawable(Color.WHITE)));
        ViewCompat.setScreenReaderFocusable(row, true);

        LinearLayout labels = column();
        labels.addView(text(title, 15, palette.text, true));
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
        captionParams.topMargin = dp(3);
        labels.addView(text(description, 13, palette.secondary, false), captionParams);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMarginEnd(dp(12));
        row.addView(labels, labelParams);
        return row;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Project {
        final String name;
        @StringRes final int description;
        final String url;

        Project(String name, @StringRes int description, String url) {
            this.name = name;
            this.description = description;
            this.url = url;
        }
    }
}
