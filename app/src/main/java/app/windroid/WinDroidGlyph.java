package app.windroid;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class WinDroidGlyph extends View {
    enum Symbol { HOME, PERSON, DESKTOP, INSTALL, APP, LIBRARY, CHEVRON, MORE, BUSY, READY, ALERT, RECENT, PIN, ADD,
        STORE, SEARCH, CLOSE }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Symbol symbol;
    private ValueAnimator spin;
    private float spinAngle;

    WinDroidGlyph(Context context, Symbol symbol, int color) {
        super(context);
        this.symbol = symbol;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setColor(color);
        paint.setStrokeWidth(1.75f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setSymbol(Symbol symbol) {
        if (this.symbol == symbol) return;
        this.symbol = symbol;
        updateSpin(isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE);
        invalidate();
    }

    void setColor(int color) {
        if (paint.getColor() == color) return;
        paint.setColor(color);
        invalidate();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        updateSpin(isVisible);
    }

    @Override
    protected void onDetachedFromWindow() {
        updateSpin(false);
        super.onDetachedFromWindow();
    }

    private void updateSpin(boolean visible) {
        if (symbol != Symbol.BUSY || !visible || !ValueAnimator.areAnimatorsEnabled()) {
            if (spin != null) spin.cancel();
            spinAngle = 0f;
            return;
        }
        if (spin == null) {
            spin = ValueAnimator.ofFloat(0f, 360f);
            spin.setDuration(900);
            spin.setInterpolator(new LinearInterpolator());
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.addUpdateListener(animation -> {
                spinAngle = (float) animation.getAnimatedValue();
                invalidate();
            });
        }
        if (!spin.isStarted()) spin.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        float side = Math.min(width, height);
        if (side <= 0) return;
        canvas.save();
        canvas.translate(getPaddingLeft() + (width - side) / 2, getPaddingTop() + (height - side) / 2);
        canvas.scale(side / 24, side / 24);
        paint.setStyle(Paint.Style.STROKE);
        path.rewind();

        switch (symbol) {
            case HOME:
                line(3.5f, 10.5f, 12, 4, 20.5f, 10.5f);
                line(5.5f, 9, 5.5f, 20, 10, 20, 10, 15, 14, 15, 14, 20, 18.5f, 20, 18.5f, 9);
                break;
            case PERSON:
                canvas.drawCircle(12, 8.25f, 3.4f, paint);
                path.moveTo(5, 20);
                path.cubicTo(5.5f, 12.75f, 18.5f, 12.75f, 19, 20);
                break;
            case DESKTOP:
                canvas.drawRoundRect(3.5f, 4.5f, 20.5f, 16.5f, 2.5f, 2.5f, paint);
                line(12, 16.5f, 12, 20);
                line(8.5f, 20, 15.5f, 20);
                break;
            case INSTALL:
                line(12, 4, 12, 14);
                line(8, 10, 12, 14, 16, 10);
                line(5, 16, 5, 19, 19, 19, 19, 16);
                break;
            case APP:
                canvas.drawRoundRect(4, 5, 20, 19, 3, 3, paint);
                line(4.5f, 9.5f, 19.5f, 9.5f);
                break;
            case LIBRARY:
                canvas.drawRoundRect(5, 8, 19, 20, 3, 3, paint);
                line(7, 4, 17, 4);
                line(10, 13, 14, 13);
                break;
            case CHEVRON:
                line(9, 5.5f, 15, 12, 9, 18.5f);
                break;
            case MORE:
                paint.setStyle(Paint.Style.FILL);
                for (int x = 6; x <= 18; x += 6) canvas.drawCircle(x, 12, 1.4f, paint);
                break;
            case BUSY:
                int alpha = paint.getAlpha();
                paint.setAlpha(Math.round(alpha * 0.18f));
                canvas.drawCircle(12, 12, 8, paint);
                paint.setAlpha(alpha);
                canvas.drawArc(4, 4, 20, 20, -90 + spinAngle, 108, false, paint);
                break;
            case READY:
                canvas.drawCircle(12, 12, 8, paint);
                line(8.5f, 12, 11, 14.5f, 15.5f, 10);
                break;
            case ALERT:
                canvas.drawCircle(12, 12, 8, paint);
                line(12, 8, 12, 12);
                line(12, 15.5f, 12, 15.6f);
                break;
            case RECENT:
                canvas.drawCircle(12, 12, 8.3f, paint);
                line(12, 7.5f, 12, 12, 15.4f, 13.9f);
                break;
            case PIN:
                line(8.5f, 5.5f, 15.5f, 5.5f);
                line(9.5f, 5.5f, 9.5f, 10.5f, 7, 13.5f, 7, 15, 17, 15, 17, 13.5f,
                        14.5f, 10.5f, 14.5f, 5.5f);
                line(12, 15, 12, 20);
                break;
            case ADD:
                line(12, 5, 12, 19);
                line(5, 12, 19, 12);
                break;
            case STORE:
                canvas.drawRoundRect(4.5f, 7.5f, 19.5f, 20.5f, 2.5f, 2.5f, paint);
                canvas.drawArc(8.5f, 3.5f, 15.5f, 10.5f, 180, 180, false, paint);
                break;
            case SEARCH:
                canvas.drawCircle(10.5f, 10.5f, 5.8f, paint);
                line(15, 15, 20, 20);
                break;
            case CLOSE:
                line(6, 6, 18, 18);
                line(6, 18, 18, 6);
                break;
        }

        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private void line(float... points) {
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) {
            path.lineTo(points[index], points[index + 1]);
        }
    }
}
