package com.pgraphics.paisystemmanager;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Staged startup reveal for the approved P AI artwork.
 * The original PNG remains the source of every logo pixel, including TM.
 */
public final class StartupLogoView extends View {

    private final Paint imagePaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private Bitmap logoBitmap;
    private final RectF destination = new RectF();
    private final RectF revealRect = new RectF();
    private final Path orbitRevealPath = new Path();

    private float animationProgress = 0f;
    private ValueAnimator animator;
    private boolean animationStarted = false;

    public StartupLogoView(Context context) {
        super(context);
        initialize();
    }

    public StartupLogoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public StartupLogoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        logoBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.p_ai_launcher);

        dimPaint.setAlpha(34);
        imagePaint.setAlpha(255);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimationOnce();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    private void startAnimationOnce() {
        if (animationStarted || logoBitmap == null) return;
        animationStarted = true;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2200L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            animationProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateDestination(width, height);
    }

    private void updateDestination(int width, int height) {
        if (logoBitmap == null || width <= 0 || height <= 0) return;

        float bitmapWidth = logoBitmap.getWidth();
        float bitmapHeight = logoBitmap.getHeight();
        float scale = Math.min(width / bitmapWidth, height / bitmapHeight);
        float drawnWidth = bitmapWidth * scale;
        float drawnHeight = bitmapHeight * scale;
        float left = (width - drawnWidth) / 2f;
        float top = (height - drawnHeight) / 2f;

        destination.set(left, top, left + drawnWidth, top + drawnHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (logoBitmap == null) return;

        // Keep the original navy/circuit artwork subtly visible as the stage base.
        canvas.drawBitmap(logoBitmap, null, destination, dimPaint);

        // Stage 1: the main P shape forms from the lower edge upward.
        drawRectReveal(canvas, 0.13f, 0.17f, 0.66f, 0.72f,
                stageProgress(0.00f, 0.24f), true);

        // Stage 2: the AI lettering appears from left to right.
        drawRectReveal(canvas, 0.48f, 0.43f, 0.91f, 0.72f,
                stageProgress(0.24f, 0.43f), false);

        // Stage 3: reveal the orbital sweep around the P and AI.
        drawOrbitReveal(canvas, stageProgress(0.43f, 0.68f));

        // Stage 4: reveal the official upper-right TM mark.
        drawRectReveal(canvas, 0.81f, 0.12f, 0.96f, 0.25f,
                stageProgress(0.68f, 0.77f), false);

        // Stage 5: the cyan underline/light streak forms beneath the brand.
        drawRectReveal(canvas, 0.17f, 0.78f, 0.84f, 0.83f,
                stageProgress(0.77f, 0.87f), false);

        // Stage 6: SYSTEM MANAGER lettering appears, followed by the full artwork.
        drawRectReveal(canvas, 0.12f, 0.68f, 0.88f, 0.79f,
                stageProgress(0.87f, 0.96f), false);

        if (animationProgress > 0.96f) {
            float finish = (animationProgress - 0.96f) / 0.04f;
            Paint finishPaint = new Paint(imagePaint);
            finishPaint.setAlpha(Math.min(255, Math.max(0, (int) (finish * 255f))));
            canvas.drawBitmap(logoBitmap, null, destination, finishPaint);
        }
    }

    private float stageProgress(float start, float end) {
        if (animationProgress <= start) return 0f;
        if (animationProgress >= end) return 1f;
        return (animationProgress - start) / (end - start);
    }

    private void drawRectReveal(
            Canvas canvas,
            float leftFraction,
            float topFraction,
            float rightFraction,
            float bottomFraction,
            float progress,
            boolean bottomToTop) {

        if (progress <= 0f || destination.isEmpty()) return;

        float left = destination.left + destination.width() * leftFraction;
        float top = destination.top + destination.height() * topFraction;
        float right = destination.left + destination.width() * rightFraction;
        float bottom = destination.top + destination.height() * bottomFraction;

        if (bottomToTop) {
            float revealTop = bottom - ((bottom - top) * progress);
            revealRect.set(left, revealTop, right, bottom);
        } else {
            float revealRight = left + ((right - left) * progress);
            revealRect.set(left, top, revealRight, bottom);
        }

        int save = canvas.save();
        canvas.clipRect(revealRect);
        canvas.drawBitmap(logoBitmap, null, destination, imagePaint);
        canvas.restoreToCount(save);
    }

    private void drawOrbitReveal(Canvas canvas, float progress) {
        if (progress <= 0f || destination.isEmpty()) return;

        float left = destination.left + destination.width() * 0.08f;
        float top = destination.top + destination.height() * 0.15f;
        float right = destination.left + destination.width() * 0.91f;
        float bottom = destination.top + destination.height() * 0.70f;

        orbitRevealPath.reset();
        orbitRevealPath.moveTo(left, top + (bottom - top) * (1f - progress));
        orbitRevealPath.cubicTo(
                left + (right - left) * progress * 0.25f, top,
                left + (right - left) * progress * 0.70f, bottom,
                left + (right - left) * progress, bottom);
        orbitRevealPath.lineTo(left + (right - left) * progress, top);
        orbitRevealPath.lineTo(left, top);
        orbitRevealPath.close();

        int save = canvas.save();
        canvas.clipPath(orbitRevealPath);
        canvas.drawBitmap(logoBitmap, null, destination, imagePaint);
        canvas.restoreToCount(save);
    }
}
