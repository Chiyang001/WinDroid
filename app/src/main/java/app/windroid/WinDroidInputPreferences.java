package app.windroid;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;

import com.winlator.R;

public final class WinDroidInputPreferences {
    public enum Mode { DIRECT, TRACKPAD }

    static final String PREFERENCES = "windroid_input";
    private static final String POINTER_MODE = "pointer_mode";
    private static final String SHOW_LEFT_BUTTON = "show_left_button";
    private static final String SHOW_RIGHT_BUTTON = "show_right_button";
    private static final String SHOW_WHEEL = "show_wheel";
    private static final String LEGACY_DIRECT_TOUCH = "move_cursor_to_touchpoint";
    private static final int[][] CHECKED_STATES = {{android.R.attr.state_checked}, {}};

    private WinDroidInputPreferences() {}

    public static Mode mode(Context context) {
        String saved = preferences(context).getString(POINTER_MODE, "");
        if (Mode.DIRECT.name().equals(saved)) return Mode.DIRECT;
        if (Mode.TRACKPAD.name().equals(saved)) return Mode.TRACKPAD;
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(LEGACY_DIRECT_TOUCH, false)
                ? Mode.DIRECT : Mode.TRACKPAD;
    }

    public static boolean showWheel(Context context) {
        return preferences(context).getBoolean(SHOW_WHEEL, false);
    }

    public static boolean showLeftButton(Context context) {
        return preferences(context).getBoolean(SHOW_LEFT_BUTTON, true);
    }

    public static boolean showRightButton(Context context) {
        return preferences(context).getBoolean(SHOW_RIGHT_BUTTON, true);
    }

    static void bindSummary(TextView label) {
        Context context = label.getContext();
        SharedPreferences preferences = preferences(context);
        SharedPreferences.OnSharedPreferenceChangeListener listener = (store, key) -> {
            if (key == null || POINTER_MODE.equals(key)) updateSummary(label);
        };
        updateSummary(label);
        label.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                preferences.registerOnSharedPreferenceChangeListener(listener);
                updateSummary(label);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                preferences.unregisterOnSharedPreferenceChangeListener(listener);
            }
        });
    }

    static void updateSummary(TextView label) {
        label.setText(mode(label.getContext()) == Mode.DIRECT ? R.string.windroid_input_direct : R.string.windroid_input_trackpad);
    }

    public static void show(Activity activity, Context context, @Nullable Runnable onChanged) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        WinDroidPalette palette = new WinDroidPalette(context);
        SharedPreferences preferences = preferences(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPaddingRelative(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 12));

        TextView pointerHeading = new TextView(context);
        pointerHeading.setText(R.string.windroid_input_pointer_mode);
        pointerHeading.setTextColor(palette.text);
        pointerHeading.setTextSize(14);
        pointerHeading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ViewCompat.setAccessibilityHeading(pointerHeading, true);
        content.addView(pointerHeading);

        RadioGroup modeOptions = new RadioGroup(context);
        modeOptions.setOrientation(LinearLayout.VERTICAL);
        AppCompatRadioButton direct = modeOption(context, palette, R.string.windroid_input_direct, R.string.windroid_input_direct_detail);
        AppCompatRadioButton trackpad = modeOption(context, palette, R.string.windroid_input_trackpad, R.string.windroid_input_trackpad_detail);
        modeOptions.addView(direct, new RadioGroup.LayoutParams(-1, -2));
        RadioGroup.LayoutParams secondOption = new RadioGroup.LayoutParams(-1, -2);
        secondOption.topMargin = dp(context, 8);
        modeOptions.addView(trackpad, secondOption);
        Mode currentMode = mode(context);
        modeOptions.check(currentMode == Mode.DIRECT ? direct.getId() : trackpad.getId());
        append(content, modeOptions, 8);

        TextView longPress = new TextView(context);
        longPress.setText(R.string.windroid_input_long_press);
        longPress.setTextSize(13);
        longPress.setTextColor(palette.secondary);
        append(content, longPress, 10);

        LinearLayout pointerControls = new LinearLayout(context);
        pointerControls.setOrientation(LinearLayout.VERTICAL);
        pointerControls.setVisibility(currentMode == Mode.TRACKPAD ? View.VISIBLE : View.GONE);
        append(content, pointerControls, 10);

        TextView moveControlsHint = new TextView(context);
        moveControlsHint.setText(R.string.windroid_input_move_controls);
        moveControlsHint.setTextSize(13);
        moveControlsHint.setTextColor(palette.secondary);
        pointerControls.addView(moveControlsHint);
        append(pointerControls, visibilitySwitch(context, palette, preferences, SHOW_LEFT_BUTTON, showLeftButton(context),
                R.string.windroid_input_show_left_button, 0, onChanged), 8);
        append(pointerControls, visibilitySwitch(context, palette, preferences, SHOW_RIGHT_BUTTON, showRightButton(context),
                R.string.windroid_input_show_right_button, 0, onChanged), 8);
        append(pointerControls, visibilitySwitch(context, palette, preferences, SHOW_WHEEL, showWheel(context),
                R.string.windroid_input_show_wheel, R.string.windroid_input_wheel_detail, onChanged), 8);

        modeOptions.setOnCheckedChangeListener((group, checkedId) -> {
            Mode selected = checkedId == direct.getId() ? Mode.DIRECT : Mode.TRACKPAD;
            preferences.edit().putString(POINTER_MODE, selected.name()).apply();
            pointerControls.setVisibility(selected == Mode.TRACKPAD ? View.VISIBLE : View.GONE);
            if (onChanged != null) onChanged.run();
        });

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        // The localized context may not carry the Activity's window token.
        new AlertDialog.Builder(activity, R.style.WinDroidDialogTheme)
                .setTitle(context.getText(R.string.wd_input_preferences))
                .setView(scroll)
                .setPositiveButton(context.getText(R.string.windroid_input_done), null)
                .show();
    }

    private static SwitchCompat visibilitySwitch(Context context, WinDroidPalette palette, SharedPreferences preferences,
                                                  String key, boolean initialValue, int title, int detail,
                                                  @Nullable Runnable onChanged) {
        SwitchCompat option = new SwitchCompat(context);
        styleOption(option, palette, title, detail);
        option.setThumbTintList(new ColorStateList(CHECKED_STATES, new int[]{palette.onAccent, palette.secondary}));
        option.setTrackTintList(new ColorStateList(CHECKED_STATES, new int[]{palette.accent, palette.border}));
        option.setSwitchPadding(dp(context, 12));
        option.setShowText(false);
        option.setChecked(initialValue);
        option.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            if (onChanged != null) onChanged.run();
        });
        return option;
    }

    private static AppCompatRadioButton modeOption(Context context, WinDroidPalette palette, int title, int detail) {
        AppCompatRadioButton option = new AppCompatRadioButton(context);
        option.setId(View.generateViewId());
        styleOption(option, palette, title, detail);
        option.setButtonTintList(new ColorStateList(CHECKED_STATES, new int[]{palette.accent, palette.secondary}));
        return option;
    }

    private static void styleOption(TextView option, WinDroidPalette palette, int title, int detail) {
        Context context = option.getContext();
        String label = context.getString(title);
        SpannableStringBuilder copy = new SpannableStringBuilder(label);
        copy.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (detail != 0) {
            copy.append('\n').append(context.getString(detail));
            int detailStart = label.length() + 1;
            copy.setSpan(new RelativeSizeSpan(0.86f), detailStart, copy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            copy.setSpan(new ForegroundColorSpan(palette.secondary), detailStart, copy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        option.setText(copy);
        option.setTextSize(15);
        option.setTextColor(palette.text);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setMinHeight(dp(context, 54));
        option.setPaddingRelative(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

        StateListDrawable states = new StateListDrawable();
        states.addState(CHECKED_STATES[0], surface(context, palette.tile, palette.secondary));
        states.addState(CHECKED_STATES[1], surface(context, palette.surface, palette.border));
        int ripple = (palette.text & 0x00ffffff) | 0x23000000;
        option.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), states, null));
    }

    private static GradientDrawable surface(Context context, int fill, int edge) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, 13));
        shape.setStroke(dp(context, 1), edge);
        return shape;
    }

    private static void append(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(parent.getContext(), topMargin);
        parent.addView(view, params);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
