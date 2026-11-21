package com.winlator.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.winlator.R;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

public final class WinDroidPointerControls extends FrameLayout {
    private enum Gesture { IDLE, PRESS, SCROLL, MOVE }

    private final SharedPreferences preferences;
    private final XServer xServer;
    private final Control left;
    private final Control right;
    private final Control wheel;
    private final Control[] controls;
    private final int touchSlop;
    private final int foreground;
    private final int secondary;
    private final int border;
    private final int surface;
    private final int pressedSurface;
    private final int wheelStep;
    private Insets safeInsets = Insets.NONE;

    public WinDroidPointerControls(Context context, SharedPreferences preferences, XServer xServer) {
        super(context);
        this.preferences = preferences;
        this.xServer = xServer;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        foreground = context.getColor(R.color.windroid_primary_text);
        secondary = context.getColor(R.color.windroid_secondary_text);
        border = context.getColor(R.color.windroid_border);
        surface = ColorUtils.setAlphaComponent(context.getColor(R.color.windroid_dialog_surface), 240);
        pressedSurface = ColorUtils.compositeColors(context.getColor(R.color.windroid_control_highlight), surface);
        wheelStep = dp(18);
        setMotionEventSplittingEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        left = new Control("windroid_pointer_left", R.string.windroid_input_left_button,
                Pointer.Button.BUTTON_LEFT, 64, 48);
        right = new Control("windroid_pointer_right", R.string.windroid_input_right_button,
                Pointer.Button.BUTTON_RIGHT, 64, 48);
        wheel = new Control("windroid_pointer_wheel", R.string.windroid_input_wheel, null, 44, 110);
        controls = new Control[]{left, right, wheel};

        ViewCompat.addAccessibilityAction(wheel, context.getString(R.string.windroid_input_scroll_up), (view, args) -> {
            click(Pointer.Button.BUTTON_SCROLL_UP);
            return true;
        });
        ViewCompat.addAccessibilityAction(wheel, context.getString(R.string.windroid_input_scroll_down), (view, args) -> {
            click(Pointer.Button.BUTTON_SCROLL_DOWN);
            return true;
        });
    }

    public void setShownControls(boolean showLeft, boolean showRight, boolean showWheel) {
        left.setShown(showLeft);
        right.setShown(showRight);
        wheel.setShown(showWheel);
    }

    public void applyInsets(WindowInsetsCompat insets) {
        Insets safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.mandatorySystemGestures());
        if (safeInsets.equals(safe)) return;
        safeInsets = safe;
        restorePositions();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        restorePositions();
    }

    private void restorePositions() {
        for (Control control : controls) {
            if (control.gesture != Gesture.MOVE) control.restorePosition();
        }
    }

    private void click(Pointer.Button button) {
        if (xServer.pointer.isButtonPressed(button)) return;
        xServer.injectPointerButtonPress(button);
        xServer.injectPointerButtonRelease(button);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private final class Control extends View {
        private final String positionKey;
        private final Pointer.Button button;
        private final Drawable mouseIcon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chevrons = new Path();
        private final Runnable longPress = this::startMoving;
        private Gesture gesture = Gesture.IDLE;
        private float downX;
        private float downY;
        private float startX;
        private float startY;
        private float scrollAnchor;
        private float thumbOffset;

        Control(String positionKey, int labelRes, Pointer.Button button, int width, int height) {
            super(WinDroidPointerControls.this.getContext());
            this.positionKey = positionKey;
            this.button = button;
            setVisibility(GONE);
            setFocusable(true);
            setContentDescription(getContext().getString(labelRes));
            if (button != null) {
                mouseIcon = getContext().getDrawable(R.drawable.windroid_pointer_mouse).mutate();
                mouseIcon.setTint(foreground);
                int iconRadius = dp(13);
                mouseIcon.setBounds(-iconRadius, -iconRadius, iconRadius, iconRadius);
                setOnClickListener(view -> click(button));
            } else {
                mouseIcon = null;
            }
            addView(this, new LayoutParams(dp(width), dp(height), Gravity.TOP | Gravity.LEFT));
        }

        void setShown(boolean shown) {
            if (!shown) finishGesture(false);
            setVisibility(shown ? VISIBLE : GONE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean isWheel = button == null;
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float halfWidth = isWheel ? dp(13) : cx - dp(3);
            float halfHeight = isWheel ? cy - dp(5) : dp(19);
            float radius = dp(13);
            boolean active = gesture != Gesture.IDLE || isPressed() || isFocused();
            paint.setColor(active ? pressedSurface : surface);
            canvas.drawRoundRect(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(gesture == Gesture.MOVE || isFocused() ? foreground : border);
            canvas.drawRoundRect(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            if (isWheel) {
                drawWheel(canvas, cx, cy, halfHeight);
            } else {
                canvas.save();
                canvas.translate(cx, cy);
                if (button == Pointer.Button.BUTTON_RIGHT) canvas.scale(-1f, 1f);
                mouseIcon.draw(canvas);
                canvas.restore();
            }
        }

        private void drawWheel(Canvas canvas, float cx, float cy, float halfHeight) {
            float trackHalfHeight = halfHeight - dp(20);
            float thumbHalfHeight = dp(11);
            float travel = trackHalfHeight - thumbHalfHeight;
            float thumbY = cy + clamp(thumbOffset, -travel, travel);

            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(2));
            paint.setColor(border);
            canvas.drawLine(cx, cy - trackHalfHeight, cx, cy + trackHalfHeight, paint);

            float arrowOffset = halfHeight - dp(11);
            float arrowWidth = dp(3);
            float arrowRise = dp(2);
            chevrons.rewind();
            chevrons.moveTo(cx - arrowWidth, cy - arrowOffset + arrowRise);
            chevrons.lineTo(cx, cy - arrowOffset);
            chevrons.lineTo(cx + arrowWidth, cy - arrowOffset + arrowRise);
            chevrons.moveTo(cx - arrowWidth, cy + arrowOffset - arrowRise);
            chevrons.lineTo(cx, cy + arrowOffset);
            chevrons.lineTo(cx + arrowWidth, cy + arrowOffset - arrowRise);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(secondary);
            canvas.drawPath(chevrons, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(foreground);
            canvas.drawRoundRect(cx - dp(5), thumbY - thumbHalfHeight, cx + dp(5), thumbY + thumbHalfHeight,
                    dp(5), dp(5), paint);
            paint.setColor(surface);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(cx - dp(2), thumbY - dp(2), cx + dp(2), thumbY - dp(2), paint);
            canvas.drawLine(cx - dp(2), thumbY + dp(2), cx + dp(2), thumbY + dp(2), paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    finishGesture(false);
                    gesture = Gesture.PRESS;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = getTranslationX();
                    startY = getTranslationY();
                    setPressed(true);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    updateGesture(event);
                    break;
                case MotionEvent.ACTION_UP:
                    updateGesture(event);
                    finishGesture(true);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    finishGesture(false);
                    break;
            }
            return true;
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (!hasFocus) finishGesture(false);
        }

        @Override
        protected void onDetachedFromWindow() {
            finishGesture(false);
            super.onDetachedFromWindow();
        }

        private void startMoving() {
            gesture = Gesture.MOVE;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
        }

        private void updateGesture(MotionEvent event) {
            if (gesture == Gesture.IDLE) return;
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (gesture == Gesture.PRESS && Math.hypot(dx, dy) > touchSlop) {
                if (button != null) {
                    finishGesture(false);
                    return;
                }
                removeCallbacks(longPress);
                gesture = Gesture.SCROLL;
            }
            if (gesture == Gesture.MOVE) {
                setTranslationX(clamp(startX + dx, minX(), maxX()));
                setTranslationY(clamp(startY + dy, minY(), maxY()));
            } else if (gesture == Gesture.SCROLL) {
                thumbOffset = dy;
                int steps = (int) ((dy - scrollAnchor) / wheelStep);
                scrollAnchor += steps * wheelStep;
                Pointer.Button scroll = steps < 0 ? Pointer.Button.BUTTON_SCROLL_UP : Pointer.Button.BUTTON_SCROLL_DOWN;
                for (int index = 0; index < Math.min(Math.abs(steps), 10); index++) click(scroll);
                invalidate();
            }
        }

        private void finishGesture(boolean commit) {
            if (gesture == Gesture.IDLE) return;
            boolean tap = commit && gesture == Gesture.PRESS && button != null;
            boolean moved = gesture == Gesture.MOVE && (getTranslationX() != startX || getTranslationY() != startY);
            gesture = Gesture.IDLE;
            removeCallbacks(longPress);
            setPressed(false);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (moved && commit) {
                preferences.edit().putFloat(positionKey + "_x", fraction(getTranslationX(), minX(), maxX()))
                        .putFloat(positionKey + "_y", fraction(getTranslationY(), minY(), maxY())).apply();
            }
            if (moved && !commit) restorePosition();
            scrollAnchor = thumbOffset = 0;
            invalidate();
            if (tap) performClick();
        }

        private void restorePosition() {
            float minX = minX();
            float maxX = maxX();
            float minY = minY();
            float maxY = maxY();
            float defaultX = (minX + maxX) * 0.5f;
            float defaultY = maxY - dp(4);
            if (button == null) {
                defaultX = maxX - dp(46);
                defaultY = (minY + maxY) * 0.5f;
            } else {
                float offset = getLayoutParams().width * 0.5f + dp(14);
                defaultX += button == Pointer.Button.BUTTON_LEFT ? -offset : offset;
            }
            float x = preferences.getFloat(positionKey + "_x", Float.NaN);
            float y = preferences.getFloat(positionKey + "_y", Float.NaN);
            setTranslationX(Float.isFinite(x) ? minX + clamp(x, 0, 1) * (maxX - minX) : clamp(defaultX, minX, maxX));
            setTranslationY(Float.isFinite(y) ? minY + clamp(y, 0, 1) * (maxY - minY) : clamp(defaultY, minY, maxY));
        }

        private float minX() {
            return Math.min(safeInsets.left + dp(8), Math.max(0, WinDroidPointerControls.this.getWidth() - getLayoutParams().width));
        }

        private float maxX() {
            return Math.max(minX(), WinDroidPointerControls.this.getWidth() - getLayoutParams().width - safeInsets.right - dp(8));
        }

        private float minY() {
            return Math.min(safeInsets.top + dp(8), Math.max(0, WinDroidPointerControls.this.getHeight() - getLayoutParams().height));
        }

        private float maxY() {
            return Math.max(minY(), WinDroidPointerControls.this.getHeight() - getLayoutParams().height - safeInsets.bottom - dp(8));
        }

        private float fraction(float value, float min, float max) {
            return max > min ? (value - min) / (max - min) : 0;
        }
    }
}
