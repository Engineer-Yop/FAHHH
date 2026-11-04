package com.example.sanivend;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

public class SimpleLineGraphView extends View {

    private Paint linePaint;
    private Paint pointPaint;
    private Paint axisPaint;
    private Paint textPaint;
    private float[] dataPoints = {0, 0, 0, 0, 0, 0, 0}; 
    private String[] labels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private float pointSpacing;
    private float paddingLeft;
    private float paddingRight;
    private float paddingTop;
    private float paddingBottom;

    public SimpleLineGraphView(Context context) {
        this(context, null);
    }

    public SimpleLineGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Adjusted spacing to "zoom back" and make it more compact
        pointSpacing = dpToPx(60); 
        paddingLeft = dpToPx(50);   
        paddingRight = dpToPx(50);
        paddingTop = dpToPx(40);
        paddingBottom = dpToPx(60);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#5E26D1"));
        linePaint.setStrokeWidth(dpToPx(3));
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(Color.parseColor("#8599E9"));
        pointPaint.setStyle(Paint.Style.FILL);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(Color.BLACK);
        axisPaint.setStrokeWidth(dpToPx(2));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(dpToPx(10));
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    public void setData(float[] newData) {
        this.dataPoints = newData;
        requestLayout(); 
        invalidate(); 
    }

    public void setLabels(String[] newLabels) {
        this.labels = newLabels;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int numPoints = (dataPoints != null) ? dataPoints.length : 0;
        int numLabels = (labels != null) ? labels.length : 0;
        int count = Math.max(numPoints, numLabels);
        
        // Total width = padding + gaps between points
        int width = (int) (paddingLeft + paddingRight + (Math.max(0, count - 1) * pointSpacing));
        
        // Ensure at least container width
        int containerWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            if (width < containerWidth) width = containerWidth;
        }

        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            height = (int) dpToPx(220);
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints == null || dataPoints.length == 0) return;

        float graphWidth = getWidth() - paddingLeft - paddingRight;
        float graphHeight = getHeight() - paddingTop - paddingBottom;

        // Draw only X Axis (Base line) - Removed Y Axis line as it was blocking content
        canvas.drawLine(paddingLeft, paddingTop + graphHeight, paddingLeft + graphWidth, paddingTop + graphHeight, axisPaint); 

        float maxData = 0;
        for (float p : dataPoints) if (p > maxData) maxData = p;
        if (maxData == 0) maxData = 100; 
        maxData = maxData * 1.2f;

        int numPoints = dataPoints.length;
        int numLabels = (labels != null) ? labels.length : 0;
        int count = Math.max(numPoints, numLabels);
        
        float xStep = (count > 1) ? graphWidth / (count - 1) : 0;
        Path path = new Path();

        for (int i = 0; i < count; i++) {
            float val = (i < dataPoints.length) ? dataPoints[i] : 0;
            float x = paddingLeft + (i * xStep);
            float y = paddingTop + graphHeight - (val / maxData * graphHeight);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }

            // Draw a dot and value for every position until the end
            canvas.drawCircle(x, y, dpToPx(4), pointPaint);
            
            String valStr = String.valueOf((int)val);
            float valWidth = textPaint.measureText(valStr);
            canvas.drawText(valStr, x - (valWidth / 2), y - dpToPx(10), textPaint);
        }

        canvas.drawPath(path, linePaint);

        // Draw labels
        if (labels != null) {
            for (int i = 0; i < labels.length; i++) {
                float x = paddingLeft + (i * xStep);
                String label = labels[i];
                float textWidth = textPaint.measureText(label);
                canvas.drawText(label, x - (textWidth / 2), paddingTop + graphHeight + dpToPx(30), textPaint);
            }
        }
    }
}
