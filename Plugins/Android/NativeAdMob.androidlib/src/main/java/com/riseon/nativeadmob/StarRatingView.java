package com.riseon.nativeadmob;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class StarRatingView extends View {
    private static final String EMPTY_STAR_COLOR = "#66FFFFFF";
    private static final String FILLED_STAR_COLOR = "#FFFFC107";
    private static final int STAR_COUNT = 5;
    private static final int DESIRED_WIDTH_DP = 74;
    private static final int DESIRED_HEIGHT_DP = 16;
    private static final int STAR_GAP_DP = 2;
    private static final int STAR_PATH_POINT_COUNT = 10;
    private static final float INNER_RADIUS_RATIO = 0.45f;

    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float rating;

    StarRatingView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        emptyPaint.setColor(Color.parseColor(EMPTY_STAR_COLOR));
        filledPaint.setColor(Color.parseColor(FILLED_STAR_COLOR));
        setMinimumWidth(Math.round(DESIRED_WIDTH_DP * density));
        setMinimumHeight(Math.round(DESIRED_HEIGHT_DP * density));
    }

    void SetRating(float value) {
        rating = Math.max(0f, Math.min(STAR_COUNT, value));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(DESIRED_WIDTH_DP * density);
        int desiredHeight = Math.round(DESIRED_HEIGHT_DP * density);
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec)
              , resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float gap = STAR_GAP_DP * density;
        float starSize = Math.min(
                getHeight()
              , (getWidth() - gap * (STAR_COUNT - 1)) / STAR_COUNT);
        if (starSize <= 0f) return;

        float totalWidth = starSize * STAR_COUNT
                + gap * (STAR_COUNT - 1);
        float startX = (getWidth() - totalWidth) / 2f;
        float centerY = getHeight() / 2f;
        float outerRadius = starSize / 2f;
        float innerRadius = outerRadius * INNER_RADIUS_RATIO;

        for (int index = 0; index < STAR_COUNT; ++index) {
            float left = startX + index * (starSize + gap);
            Path path = CreateStarPath(
                    left + outerRadius
                  , centerY
                  , outerRadius
                  , innerRadius);
            canvas.drawPath(path, emptyPaint);

            float filledFraction = Math.max(
                    0f
                  , Math.min(1f, rating - index));
            if (filledFraction <= 0f) continue;

            int saveCount = canvas.save();
            canvas.clipPath(path);
            canvas.clipRect(
                    left
                  , 0
                  , left + starSize * filledFraction
                  , getHeight());
            canvas.drawPath(path, filledPaint);
            canvas.restoreToCount(saveCount);
        }
    }

    private static Path CreateStarPath(
            float centerX
          , float centerY
          , float outerRadius
          , float innerRadius) {
        Path path = new Path();
        for (int point = 0; point < STAR_PATH_POINT_COUNT; ++point) {
            double angle = -Math.PI / 2d
                    + point * Math.PI / STAR_COUNT;
            float radius = point % 2 == 0
                    ? outerRadius
                    : innerRadius;
            float x = centerX
                    + (float) (Math.cos(angle) * radius);
            float y = centerY
                    + (float) (Math.sin(angle) * radius);
            if (point == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }
}
