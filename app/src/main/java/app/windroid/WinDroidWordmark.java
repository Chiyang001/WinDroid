package app.windroid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.view.animation.LinearInterpolator;

import androidx.appcompat.widget.AppCompatTextView;

final class WinDroidWordmark extends AppCompatTextView {
    private static final String NAME = "WinDroid";
    private static final String CODE = "0129AF#%+<>/";
    private static final long INTRO_MILLIS = 870;

    private final Paint type = new Paint();
    private final Paint pixels = new Paint();
    private final Paint code = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean playOnDraw;
    private ValueAnimator animation;
    private float progress = 1f;

    WinDroidWordmark(Context context, int color, boolean animate) {
        super(context);
        setText(NAME);
        setTextColor(color);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setTextSize(22);
        setLetterSpacing(-0.025f);
        setSingleLine(true);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextAlign(Paint.Align.CENTER);
        playOnDraw = animate;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (playOnDraw && layout != null) {
            playOnDraw = false;
            if (ValueAnimator.areAnimatorsEnabled()) startIntro();
        }
        if (progress >= 1f || layout == null) {
            super.onDraw(canvas);
            return;
        }

        type.set(getPaint());
        type.setColor(getCurrentTextColor());
        pixels.setColor(getCurrentTextColor());
        code.setColor(getCurrentTextColor());
        code.setTextSize(type.getTextSize() * 0.66f);
        float baseline = getBaseline();
        float gridTop = baseline - type.getTextSize() * 0.66f;
        float gridStep = Math.max(2f, type.getTextSize() * 0.12f);
        int frame = (int) (progress * 11f);

        for (int index = 0; index < NAME.length(); index++) {
            float left = getCompoundPaddingLeft() + layout.getPrimaryHorizontal(index);
            float right = getCompoundPaddingLeft() + layout.getPrimaryHorizontal(index + 1);
            float resolve = clamp((progress - index * 0.077f - 0.08f) / 0.28f);
            if (resolve < 1f && right > left) {
                int clipped = canvas.save();
                canvas.clipRect(left, gridTop - gridStep, right, baseline + gridStep);
                drawCode(canvas, index, frame, left, right, gridTop, baseline, gridStep, 1f - resolve);
                canvas.restoreToCount(clipped);
            }
            if (resolve > 0f) {
                type.setAlpha(Math.round(255f * resolve));
                canvas.drawText(NAME, index, index + 1, left, baseline, type);
            }
        }
    }

    private void drawCode(Canvas canvas, int index, int frame, float left, float right, float top,
                          float baseline, float step, float noise) {
        int seed = index * 71 + frame * 37;
        int symbol = (seed ^ seed >>> 3) % CODE.length();
        code.setAlpha(Math.round(142f * noise));
        canvas.drawText(CODE, symbol, symbol + 1, (left + right) / 2f, baseline - step * 0.3f, code);

        int columns = (int) Math.ceil((right - left) / step);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < columns; column++) {
                int pattern = (seed + row * 17 + column * 29) ^ (seed << (row % 3));
                if ((pattern & 7) >= 3) continue;
                pixels.setAlpha(Math.round((46f + (pattern & 3) * 27f) * noise));
                float x = left + column * step;
                float y = top + row * step;
                canvas.drawRect(x, y, Math.min(right, x + step * 0.87f), y + step * 0.82f, pixels);
            }
        }
    }

    private void startIntro() {
        progress = 0f;
        animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(INTRO_MILLIS);
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                progress = 1f;
                animation = null;
                invalidate();
            }
        });
        animation.start();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (!isVisible && animation != null) animation.cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animation != null) animation.cancel();
        super.onDetachedFromWindow();
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
