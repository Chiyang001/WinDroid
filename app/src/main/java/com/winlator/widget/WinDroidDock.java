package com.winlator.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.RotateDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.winlator.R;

import java.util.Collections;
import java.util.function.Consumer;

public final class WinDroidDock extends FrameLayout {
    private static final String SIDE = "windroid_dock_right";
    private static final String HEIGHT = "windroid_dock_height";
    private static final int NO_POINTER = -1;
    private static final int IDLE_MILLIS = 3200;
    private static final PathInterpolator EASE_OUT = new PathInterpolator(0.2f, 0.7f, 0.2f, 1f);

    private final SharedPreferences preferences;
    private final AccessibilityManager accessibility;
    private final ImageButton handle;
    private final LinearLayout panel;
    private final int handleSize;
    private final int touchSlop;
    private final int margin;
    private final int foreground;
    private final int edge;
    private final ColorStateList pressTint;
    private final Rect rect = new Rect();
    private final int[] screenLocation = new int[2];
    private final Runnable idleCollapse = () -> {
        if (!panelInUse()) setExpanded(false, true);
    };

    private Insets safeInsets = Insets.NONE;
    private boolean right;
    private float heightFraction;
    private boolean ready;
    private boolean pictureInPicture;
    private boolean expanded;
    private boolean imeVisible;
    private boolean keyboardVisibleFromLayout;
    private boolean panelPressed;
    private Consumer<Boolean> expansionListener;
    private int pointer = NO_POINTER;
    private boolean dragging;
    private boolean multiplePointers;
    private float downX;
    private float downY;
    private float handleStartX;
    private float handleStartY;
    private float minX;
    private float maxX;
    private float minY;
    private float maxY;
    private float visibleMaxY;

    public WinDroidDock(Context context, SharedPreferences preferences, Runnable keyboard, Runnable controls, Runnable exit) {
        super(context);
        this.preferences = preferences;
        accessibility = context.getSystemService(AccessibilityManager.class);
        right = preferences.getBoolean(SIDE, true);
        heightFraction = preferences.getFloat(HEIGHT, 0.38f);
        if (!Float.isFinite(heightFraction)) heightFraction = 0.38f;
        heightFraction = clamp(heightFraction, 0f, 1f);
        handleSize = dp(48);
        margin = dp(5);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        foreground = context.getColor(R.color.windroid_primary_text);
        edge = context.getColor(R.color.windroid_border);
        pressTint = ColorStateList.valueOf(context.getColor(R.color.windroid_control_highlight));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);

        panel = new LinearLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (!expanded) return false;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    panelPressed = true;
                    WinDroidDock.this.removeCallbacks(idleCollapse);
                }
                boolean handled = super.dispatchTouchEvent(event);
                if (!handled || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    panelPressed = false;
                    scheduleCollapse();
                }
                return handled;
            }
        };
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(3), dp(2), dp(3), dp(2));
        panel.setBackground(rounded(ColorUtils.setAlphaComponent(context.getColor(R.color.windroid_dialog_surface), 243), 24, edge));
        panel.setElevation(dp(5));
        panel.setVisibility(INVISIBLE);
        panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.addView(button(R.string.windroid_keyboard, R.drawable.windroid_runtime_keyboard, keyboard));
        addDivider();
        AppCompatButton preferencesButton = button(R.string.windroid_dock_controls, R.drawable.windroid_runtime_controls, controls);
        preferencesButton.setContentDescription(context.getText(R.string.wd_input_preferences));
        preferencesButton.setTooltipText(context.getText(R.string.wd_input_preferences));
        panel.addView(preferencesButton);
        addDivider();
        panel.addView(button(R.string.windroid_exit, R.drawable.windroid_runtime_exit, exit));
        addView(panel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        handle = new ImageButton(context);
        handle.setScaleType(ImageView.ScaleType.CENTER);
        handle.setFocusable(true);
        handle.setElevation(dp(3));
        handle.setOnClickListener(view -> setExpanded(!expanded, true));
        handle.setOnTouchListener((view, event) -> dragHandle(event));
        ViewCompat.addAccessibilityAction(handle, context.getString(R.string.windroid_dock_other_side), (view, args) -> {
            setExpanded(false, false);
            right = !right;
            saveAnchor();
            refreshHandle();
            snap(true);
            return true;
        });
        addView(handle, new LayoutParams(handleSize, handleSize, Gravity.LEFT | Gravity.TOP));
        refreshHandle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            handle.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, handleSize, handleSize)));
        }
    }

    public void showReady() {
        ready = true;
        updateVisibility();
    }

    public void setExpansionListener(Consumer<Boolean> listener) {
        expansionListener = listener;
        listener.accept(expanded);
    }

    public void setPictureInPicture(boolean active) {
        pictureInPicture = active;
        updateVisibility();
    }

    public void onHostPaused() {
        finishDrag(false, false);
        setExpanded(false, false);
    }

    private void updateVisibility() {
        if (ready && !pictureInPicture) setVisibility(VISIBLE);
        else {
            onHostPaused();
            setVisibility(INVISIBLE);
        }
    }

    public void applyInsets(WindowInsetsCompat insets) {
        safeInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout()
                | WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.mandatorySystemGestures());
        imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
        updateGeometry();
    }

    public void setKeyboardVisibleFromLayout(boolean visible) {
        keyboardVisibleFromLayout = visible;
        updateGeometry();
    }

    public void onOutsideDown(MotionEvent event) {
        if (!expanded || getVisibility() != VISIBLE) return;
        getLocationOnScreen(screenLocation);
        int x = (int) (event.getRawX() - screenLocation[0]);
        int y = (int) (event.getRawY() - screenLocation[1]);
        handle.getHitRect(rect);
        if (rect.contains(x, y)) return;
        panel.getHitRect(rect);
        if (!rect.contains(x, y)) setExpanded(false, true);
    }

    public boolean collapseIfExpanded() {
        if (!expanded) return false;
        setExpanded(false, true);
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateGeometry();
    }

    @Override
    protected void onDetachedFromWindow() {
        onHostPaused();
        handle.animate().cancel();
        super.onDetachedFromWindow();
    }

    private void updateGeometry() {
        if (getWidth() == 0 || getHeight() == 0) return;
        int sideSpace = Math.max(0, getWidth() - handleSize);
        float leftLimit = Math.min(sideSpace, safeInsets.left + margin);
        float rightLimit = Math.max(leftLimit, sideSpace - safeInsets.right - margin);
        int panelOverhang = Math.max(0, (panel.getMeasuredHeight() - handleSize + 1) / 2);
        float topLimit = Math.min(Math.max(0, getHeight() - handleSize), safeInsets.top + panelOverhang + margin);
        float bottomLimit = Math.max(topLimit, getHeight() - safeInsets.bottom - panelOverhang - handleSize - margin);
        float keyboardLimit = bottomLimit;
        if (imeVisible || keyboardVisibleFromLayout) {
            getWindowVisibleDisplayFrame(rect);
            getLocationOnScreen(screenLocation);
            keyboardLimit = clamp(rect.bottom - screenLocation[1] - handleSize - panelOverhang - margin,
                    topLimit, bottomLimit);
        }
        boolean boundsChanged = leftLimit != minX || rightLimit != maxX || topLimit != minY
                || bottomLimit != maxY || keyboardLimit != visibleMaxY;
        minX = leftLimit;
        maxX = rightLimit;
        minY = topLimit;
        maxY = bottomLimit;
        visibleMaxY = keyboardLimit;

        if (dragging) {
            handle.setTranslationX(clamp(handle.getTranslationX(), minX, maxX));
            handle.setTranslationY(clamp(handle.getTranslationY(), minY, visibleMaxY));
        } else if (pointer == NO_POINTER && boundsChanged) {
            snap(false);
        } else {
            positionPanel(right ? maxX : minX, anchorY());
        }
    }

    private void snap(boolean animate) {
        float x = right ? maxX : minX;
        float y = anchorY();
        handle.animate().cancel();
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            handle.animate().translationX(x).translationY(y).setDuration(200).setInterpolator(EASE_OUT).start();
        } else {
            handle.setTranslationX(x);
            handle.setTranslationY(y);
        }
        positionPanel(x, y);
    }

    private float anchorY() {
        return Math.min(minY + heightFraction * (maxY - minY), visibleMaxY);
    }

    private void positionPanel(float handleX, float handleY) {
        int width = panel.getMeasuredWidth();
        float x = right ? handleX - width - margin : handleX + handleSize + margin;
        panel.setTranslationX(clamp(x, safeInsets.left, Math.max(safeInsets.left, getWidth() - safeInsets.right - width)));
        panel.setTranslationY(handleY + (handleSize - panel.getMeasuredHeight()) / 2f);
        panel.setPivotX(right ? width : 0);
        panel.setPivotY(panel.getMeasuredHeight() / 2f);
    }

    private boolean dragHandle(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handle.animate().cancel();
                pointer = event.getPointerId(0);
                downX = event.getRawX();
                downY = event.getRawY();
                handleStartX = handle.getTranslationX();
                handleStartY = handle.getTranslationY();
                multiplePointers = false;
                handle.setPressed(true);
                getParent().requestDisallowInterceptTouchEvent(true);
                removeCallbacks(idleCollapse);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiplePointers = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(pointer);
                if (index < 0) return true;
                // The primary raw offset also converts the active pointer's view-local coordinates.
                float dx = event.getRawX() + event.getX(index) - event.getX() - downX;
                float dy = event.getRawY() + event.getY(index) - event.getY() - downY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                    handle.setPressed(false);
                    setExpanded(false, false);
                }
                if (dragging) {
                    handle.setTranslationX(clamp(handleStartX + dx, minX, maxX));
                    handle.setTranslationY(clamp(handleStartY + dy, minY, visibleMaxY));
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointer) finishDrag(false, true);
                return true;
            case MotionEvent.ACTION_UP:
                finishDrag(true, true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishDrag(false, false);
                return true;
            default:
                return true;
        }
    }

    private void finishDrag(boolean allowClick, boolean commit) {
        if (pointer == NO_POINTER) return;
        pointer = NO_POINTER;
        handle.setPressed(false);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        boolean tap = allowClick && !dragging && !multiplePointers;
        if (dragging && commit) {
            right = handle.getTranslationX() >= (minX + maxX) / 2;
            if (maxY > minY) heightFraction = clamp((handle.getTranslationY() - minY) / (maxY - minY), 0f, 1f);
            saveAnchor();
            refreshHandle();
        }
        dragging = false;
        snap(commit && !tap);
        if (tap) handle.performClick();
        else scheduleCollapse();
    }

    private void saveAnchor() {
        preferences.edit().putBoolean(SIDE, right).putFloat(HEIGHT, heightFraction).apply();
    }

    private void setExpanded(boolean show, boolean animate) {
        removeCallbacks(idleCollapse);
        if (expanded == show && (show || panel.getVisibility() != VISIBLE)) return;
        boolean changed = expanded != show;
        expanded = show;
        if (changed && expansionListener != null) expansionListener.accept(show);
        if (!show) panelPressed = false;
        refreshHandle();
        panel.animate().cancel();
        boolean motion = animate && ValueAnimator.areAnimatorsEnabled();
        if (show) {
            panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            if (panel.getVisibility() != VISIBLE) {
                panel.setAlpha(motion ? 0f : 1f);
                panel.setScaleX(motion ? 0.92f : 1f);
                panel.setScaleY(motion ? 0.94f : 1f);
                panel.setVisibility(VISIBLE);
            }
            scheduleCollapse();
        } else {
            if (panel.hasFocus()) handle.requestFocus();
            panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        if (motion) {
            panel.animate().alpha(show ? 1f : 0f).scaleX(show ? 1f : 0.92f).scaleY(show ? 1f : 0.94f)
                    .setDuration(show ? 200 : 155).setInterpolator(EASE_OUT)
                    .withEndAction(() -> { if (!expanded) panel.setVisibility(INVISIBLE); }).start();
        } else {
            panel.setAlpha(1f);
            panel.setScaleX(1f);
            panel.setScaleY(1f);
            panel.setVisibility(show ? VISIBLE : INVISIBLE);
        }
    }

    private void addDivider() {
        View divider = new View(getContext());
        divider.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        divider.setBackgroundColor(edge);
        panel.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(16)));
    }

    private void refreshHandle() {
        int inset = dp(expanded ? 4 : 23);
        GradientDrawable cap = rounded(ColorUtils.setAlphaComponent(getContext().getColor(R.color.windroid_surface), 229), 12, edge);
        InsetDrawable background = new InsetDrawable(cap, right ? inset : 0, dp(2), right ? 0 : inset, dp(2));
        handle.setBackground(new RippleDrawable(pressTint, background, null));
        handle.setPadding(right ? inset : 0, 0, right ? 0 : inset, 0);
        Drawable arrow = getContext().getDrawable(R.drawable.windroid_runtime_dock_chevron).mutate();
        arrow.setTint(foreground);
        if (expanded == right) {
            RotateDrawable flipped = new RotateDrawable();
            flipped.setDrawable(arrow);
            flipped.setFromDegrees(180f);
            flipped.setToDegrees(180f);
            flipped.setLevel(10000);
            arrow = flipped;
        }
        handle.setImageDrawable(arrow);
        handle.setContentDescription(getContext().getString(expanded ? R.string.windroid_dock_collapse : R.string.windroid_dock_expand));
    }

    private boolean panelInUse() {
        return pointer != NO_POINTER || panelPressed || panel.hasFocus()
                || (accessibility != null && accessibility.isTouchExplorationEnabled());
    }

    private void scheduleCollapse() {
        removeCallbacks(idleCollapse);
        if (!expanded || panelInUse()) return;
        int timeout = IDLE_MILLIS;
        if (accessibility != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            timeout = accessibility.getRecommendedTimeoutMillis(timeout, AccessibilityManager.FLAG_CONTENT_CONTROLS
                    | AccessibilityManager.FLAG_CONTENT_TEXT | AccessibilityManager.FLAG_CONTENT_ICONS);
        }
        postDelayed(idleCollapse, timeout);
    }

    private AppCompatButton button(@StringRes int text, @DrawableRes int icon, Runnable action) {
        AppCompatButton button = new AppCompatButton(getContext(), null, 0);
        button.setText(text);
        button.setTextColor(foreground);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setAllCaps(false);
        button.setMinHeight(handleSize);
        button.setMinWidth(handleSize);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), 0, dp(9), 0);
        button.setBackground(new RippleDrawable(pressTint, null, rounded(Color.WHITE, 20, 0)));
        Drawable glyph = getContext().getDrawable(icon).mutate();
        glyph.setTint(foreground);
        glyph.setBounds(0, 0, dp(16), dp(16));
        button.setCompoundDrawablesRelative(glyph, null, null, null);
        button.setCompoundDrawablePadding(dp(5));
        button.setOnClickListener(view -> {
            setExpanded(false, true);
            action.run();
        });
        button.setOnFocusChangeListener((view, focused) -> scheduleCollapse());
        return button;
    }

    private GradientDrawable rounded(int color, int radius, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (border != 0) shape.setStroke(dp(1), border);
        return shape;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(value, high));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
