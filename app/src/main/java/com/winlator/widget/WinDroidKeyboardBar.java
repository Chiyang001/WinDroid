package com.winlator.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.winlator.R;
import com.winlator.xserver.XKeycode;
import com.winlator.xserver.XServer;

public final class WinDroidKeyboardBar extends LinearLayout {
    private final Rect visibleFrame = new Rect();
    private final int[] screenLocation = new int[2];
    private final ViewTreeObserver.OnGlobalLayoutListener reposition = this::updatePosition;
    private final XServer xServer;
    private final LinearLayout shortcuts;
    private final int foreground;
    private Insets safeInsets = Insets.NONE;
    private int imeInset;
    private boolean imeWasVisible;

    public WinDroidKeyboardBar(Context context, XServer xServer, Runnable closeKeyboard) {
        super(context);
        this.xServer = xServer;
        foreground = context.getColor(R.color.windroid_primary_text);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(4), 0, dp(4), 0);
        setBackgroundResource(R.drawable.windroid_runtime_panel);
        setVisibility(GONE);

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        shortcuts = new LinearLayout(context);
        shortcuts.setOrientation(HORIZONTAL);
        scroller.addView(shortcuts, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(scroller, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addShortcut("Ctrl+C", XKeycode.KEY_C, XKeycode.KEY_CTRL_L);
        addShortcut("Ctrl+V", XKeycode.KEY_V, XKeycode.KEY_CTRL_L);
        addShortcut("Ctrl+Alt+Del", XKeycode.KEY_DEL, XKeycode.KEY_CTRL_L, XKeycode.KEY_ALT_L);
        addShortcut("Ctrl+X", XKeycode.KEY_X, XKeycode.KEY_CTRL_L);
        addShortcut("Ctrl+A", XKeycode.KEY_A, XKeycode.KEY_CTRL_L);
        addShortcut("Ctrl+Z", XKeycode.KEY_Z, XKeycode.KEY_CTRL_L);
        addShortcut("Alt+Tab", XKeycode.KEY_TAB, XKeycode.KEY_ALT_L);
        addShortcut("Esc", XKeycode.KEY_ESC);

        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.windroid_border));
        addView(divider, new LayoutParams(dp(1), dp(22)));

        ImageButton close = new ImageButton(context);
        close.setImageResource(R.drawable.windroid_runtime_dock_chevron);
        close.setImageTintList(ColorStateList.valueOf(foreground));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setPadding(0, 0, 0, 0);
        close.setRotation(270f);
        close.setBackground(keyRipple());
        close.setContentDescription(context.getText(R.string.windroid_keyboard_hide));
        close.setOnClickListener(view -> closeKeyboard.run());
        addView(close, new LayoutParams(dp(48), dp(48)));
    }

    public void show() {
        setVisibility(VISIBLE);
        updatePosition();
    }

    public void dismiss() {
        imeWasVisible = false;
        setVisibility(GONE);
    }

    public void setImeVisible(boolean visible) {
        if (getVisibility() != VISIBLE) return;
        if (visible) imeWasVisible = true;
        else if (imeWasVisible) dismiss();
    }

    public void applyInsets(WindowInsetsCompat insets) {
        safeInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.mandatorySystemGestures());
        imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        updatePosition();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(reposition);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(reposition);
        super.onDetachedFromWindow();
    }

    private void addShortcut(String label, XKeycode key, XKeycode... modifiers) {
        AppCompatButton button = new AppCompatButton(getContext(), null, 0);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(dp(48));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(keyRipple());
        button.setOnClickListener(view -> xServer.injectKeyChord(key, modifiers));
        shortcuts.addView(button, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)));
    }

    private RippleDrawable keyRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(16));
        return new RippleDrawable(ColorStateList.valueOf(getContext().getColor(R.color.windroid_control_highlight)),
                null, mask);
    }

    private void updatePosition() {
        if (getVisibility() != VISIBLE || !(getParent() instanceof View)) return;
        View parent = (View) getParent();
        parent.getLocationOnScreen(screenLocation);
        int parentBottom = screenLocation[1] + parent.getHeight();
        parent.getWindowVisibleDisplayFrame(visibleFrame);
        int keyboardOverlap = parentBottom - visibleFrame.bottom;
        if (imeInset > 0) {
            View window = parent.getRootView();
            window.getLocationOnScreen(screenLocation);
            int imeTop = screenLocation[1] + window.getHeight() - imeInset;
            keyboardOverlap = Math.max(keyboardOverlap, parentBottom - imeTop);
        }
        int bottomMargin = Math.max(keyboardOverlap, safeInsets.bottom) + dp(6);
        int sideMargin = Math.max(safeInsets.left, safeInsets.right) + dp(8);
        FrameLayout.LayoutParams position = (FrameLayout.LayoutParams) getLayoutParams();
        if (position.bottomMargin == bottomMargin && position.leftMargin == sideMargin) return;
        position.bottomMargin = bottomMargin;
        position.leftMargin = position.rightMargin = sideMargin;
        setLayoutParams(position);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
