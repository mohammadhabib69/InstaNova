package com.insta.nova.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import com.insta.nova.R;

/**
 * InstaNova brand wordmark custom view.
 * Draws "Insta" in the text color, "Nova" with a horizontal gradient shader
 * (#22D3EE -> #7C4DFF -> #D946EF), and a 4-point concave sparkle star
 * as a superscript after the last letter.
 */
public class WordmarkView extends View {

    public static final float SIZE_TOP_BAR = 26f;
    public static final float SIZE_WELCOME = 34f;
    public static final float SIZE_LOGIN = 44f;

    private static final String TEXT_INSTA = "Insta";
    private static final String TEXT_NOVA = "Nova";
    private static final String STAR_PATH_DATA =
            "M12 2 C12.6 7.4 16.6 11.4 22 12 C16.6 12.6 12.6 16.6 12 22 C11.4 16.6 7.4 12.6 2 12 C7.4 11.4 11.4 7.4 12 2 Z";

    private final Paint paintInsta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintNova = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStar = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Path baseStarPath;
    private final Path transformedStarPath = new Path();
    private final Matrix starMatrix = new Matrix();

    private float textSizeSp = SIZE_TOP_BAR;
    private int customTextColor = 0;
    private float revealProgress = 1.0f; // 0.0 to 1.0 for left-to-right reveal animation

    public WordmarkView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public WordmarkView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WordmarkView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WordmarkView);
            int sizeEnum = a.getInt(R.styleable.WordmarkView_wordmarkSize, 0);
            if (sizeEnum == 1) {
                textSizeSp = SIZE_LOGIN;
            } else if (sizeEnum == 2) {
                textSizeSp = SIZE_WELCOME;
            } else {
                textSizeSp = SIZE_TOP_BAR;
            }

            if (a.hasValue(R.styleable.WordmarkView_android_textSize)) {
                float px = a.getDimension(R.styleable.WordmarkView_android_textSize, 0);
                if (px > 0) {
                    textSizeSp = px / getResources().getDisplayMetrics().scaledDensity;
                }
            }

            if (a.hasValue(R.styleable.WordmarkView_android_textColor)) {
                customTextColor = a.getColor(R.styleable.WordmarkView_android_textColor, 0);
            }
            a.recycle();
        }

        try {
            baseStarPath = PathParser.createPathFromPathData(STAR_PATH_DATA);
        } catch (Exception e) {
            baseStarPath = new Path();
        }

        Typeface tf;
        try {
            tf = Typeface.createFromAsset(context.getAssets(), "fonts/wordmark.ttf");
        } catch (Exception e) {
            tf = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }

        paintInsta.setTypeface(tf);
        paintNova.setTypeface(tf);
        paintStar.setStyle(Paint.Style.FILL);

        updatePaints();
    }

    public void setTextSizeSp(float sp) {
        if (this.textSizeSp != sp) {
            this.textSizeSp = sp;
            updatePaints();
            requestLayout();
            invalidate();
        }
    }

    public void setTextColor(int color) {
        this.customTextColor = color;
        updatePaints();
        invalidate();
    }

    public void setTypeface(Typeface tf) {
        if (tf != null) {
            paintInsta.setTypeface(tf);
            paintNova.setTypeface(tf);
            requestLayout();
            invalidate();
        }
    }

    public void setRevealProgress(float progress) {
        this.revealProgress = Math.max(0.0f, Math.min(1.0f, progress));
        invalidate();
    }

    public float getRevealProgress() {
        return revealProgress;
    }

    private void updatePaints() {
        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                getResources().getDisplayMetrics()
        );
        paintInsta.setTextSize(px);
        paintNova.setTextSize(px);

        int textColor = customTextColor != 0 ? customTextColor : resolveThemeTextColor();
        paintInsta.setColor(textColor);
    }

    private int resolveThemeTextColor() {
        try {
            TypedArray a = getContext().getTheme().obtainStyledAttributes(new int[]{R.attr.themeTextPrimary});
            int color = a.getColor(0, 0xFF000000);
            a.recycle();
            return color;
        } catch (Exception ignored) {
            return 0xFF000000;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updatePaints();

        float instaWidth = paintInsta.measureText(TEXT_INSTA);
        float novaWidth = paintNova.measureText(TEXT_NOVA);

        Paint.FontMetrics fm = paintInsta.getFontMetrics();
        float textHeight = Math.abs(fm.ascent);
        float starSize = 0.45f * textHeight;
        float starGap = 0.15f * starSize;

        float contentWidth = instaWidth + novaWidth + starGap + starSize;
        float contentHeight = fm.descent - fm.ascent;

        int desiredWidth = Math.round(contentWidth + getPaddingLeft() + getPaddingRight());
        int desiredHeight = Math.round(contentHeight + getPaddingTop() + getPaddingBottom());

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (revealProgress < 1.0f) {
            canvas.clipRect(0, 0, getWidth() * revealProgress, getHeight());
        }

        updatePaints();

        float instaWidth = paintInsta.measureText(TEXT_INSTA);
        float novaWidth = paintNova.measureText(TEXT_NOVA);

        Paint.FontMetrics fm = paintInsta.getFontMetrics();
        float textHeight = Math.abs(fm.ascent);
        float starSize = 0.45f * textHeight;
        float starGap = 0.15f * starSize;

        float totalContentWidth = instaWidth + novaWidth + starGap + starSize;
        float totalContentHeight = fm.descent - fm.ascent;

        // Horizontally center within available width
        float startX = getPaddingLeft() + Math.max(0, (getWidth() - getPaddingLeft() - getPaddingRight() - totalContentWidth) / 2f);
        // Vertically center baseline
        float baseline = getPaddingTop() - fm.ascent + Math.max(0, (getHeight() - getPaddingTop() - getPaddingBottom() - totalContentHeight) / 2f);

        // 1. Draw "Insta"
        canvas.drawText(TEXT_INSTA, startX, baseline, paintInsta);

        // 2. Draw "Nova" with horizontal gradient (#22D3EE -> #7C4DFF -> #D946EF)
        float novaX = startX + instaWidth;
        float starX = novaX + novaWidth + starGap;
        float gradientEndX = starX + starSize;

        LinearGradient gradient = new LinearGradient(
                novaX, 0, gradientEndX, 0,
                new int[]{0xFF22D3EE, 0xFF7C4DFF, 0xFFD946EF},
                new float[]{0.0f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP
        );
        paintNova.setShader(gradient);
        paintStar.setShader(gradient);

        canvas.drawText(TEXT_NOVA, novaX, baseline, paintNova);

        // 3. Draw small star (the mark, 0.45 x text height) as superscript
        if (baseStarPath != null) {
            float starTop = baseline + fm.ascent; // Aligned with the top of the text
            float scale = starSize / 24f;

            starMatrix.reset();
            starMatrix.postScale(scale, scale);
            starMatrix.postTranslate(starX, starTop);

            transformedStarPath.reset();
            baseStarPath.transform(starMatrix, transformedStarPath);
            canvas.drawPath(transformedStarPath, paintStar);
        }
    }
}
